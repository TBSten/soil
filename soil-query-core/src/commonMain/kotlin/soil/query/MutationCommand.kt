// Copyright 2024 Soil Contributors
// SPDX-License-Identifier: Apache-2.0

package soil.query

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import soil.query.core.ErrorRecord
import soil.query.core.Marker
import soil.query.core.Reply
import soil.query.core.RetryCallback
import soil.query.core.RetryFn
import soil.query.core.UniqueId
import soil.query.core.epoch
import soil.query.core.exponentialBackOff
import soil.query.core.vvv
import kotlin.coroutines.cancellation.CancellationException

/**
 * Mutation command to handle mutation.
 *
 * @param T Type of the return value from the mutation.
 */
interface MutationCommand<T> {

    /**
     * Handles the mutation.
     */
    suspend fun handle(ctx: Context<T>)

    /**
     * Context for mutation command.
     *
     * @param T Type of the return value from the mutation.
     */
    interface Context<T> {
        val receiver: MutationReceiver
        val options: MutationOptions
        val state: MutationModel<T>
        val dispatch: MutationDispatch<T>
        val notifier: MutationNotifier
        val relay: MutationErrorRelay?
    }
}

internal typealias MutationErrorRelay = (ErrorRecord) -> Unit

/**
 * Determines whether a mutation operation is necessary based on the current state.
 *
 * @return `true` if mutation operation is allowed, `false` otherwise.
 */
fun <T> MutationCommand.Context<T>.shouldMutate(revision: String): Boolean {
    if (options.isOneShot && state.isMutated) {
        return false
    }
    if (options.isStrictMode && state.revision != revision) {
        return false
    }
    return !state.isPending
}

/**
 * Mutates the data.
 *
 * @param key Instance of a class implementing [MutationKey].
 * @param variable The variable to be mutated.
 * @param retryFn The retry function.
 * @return The result of the mutation.
 */
suspend fun <T, S> MutationCommand.Context<T>.mutate(
    key: MutationKey<T, S>,
    variable: S,
    retryFn: RetryFn<T> = options.exponentialBackOff(onRetry = onRetryCallback(key.id))
): Result<T> {
    return try {
        val value = retryFn.withRetry { with(key) { receiver.mutate(variable) } }
        Result.success(value)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }
}

/**
 * Dispatches the mutation result.
 *
 * @param key Instance of a class implementing [MutationKey].
 * @param variable The variable to be mutated.
 * @param marker The marker with additional information based on the caller of a mutation.
 * @param callback The callback to receive the result of the mutation.
 */
suspend inline fun <T, S> MutationCommand.Context<T>.dispatchMutateResult(
    key: MutationKey<T, S>,
    variable: S,
    marker: Marker,
    noinline callback: MutationCallback<T>?
) {
    mutate(key, variable)
        .onSuccess { data ->
            val job1 = key.onQueryUpdate(variable, data)?.let {
                notifier.onMutate { withQuery { it() } }
            }
            val job2 = key.onMutateEffect(variable, data)?.let(notifier::onMutate)
            withContext(NonCancellable) {
                if (job1 != null && options.shouldExecuteEffectSynchronously) {
                    job1.join()
                }
                if (job2 != null && options.shouldExecuteEffectSynchronously) {
                    job2.join()
                }
                dispatchMutateSuccess(data, key.contentEquals)
            }
        }
        .onFailure(::dispatchMutateFailure)
        .onFailure { reportMutationError(it, key.id, marker) }
        .also { callback?.invoke(it) }
}

/**
 * Dispatches the mutate success.
 *
 * @param data The mutation returned data.
 */
fun <T> MutationCommand.Context<T>.dispatchMutateSuccess(
    data: T,
    contentEquals: MutationContentEquals<T>? = null
) {
    val currentAt = epoch()
    val currentReply = state.reply
    val action = if (currentReply is Reply.Some && contentEquals?.invoke(currentReply.value, data) == true) {
        MutationAction.MutateSuccess(
            data = currentReply.value,
            dataUpdatedAt = state.replyUpdatedAt
        )
    } else {
        MutationAction.MutateSuccess(
            data = data,
            dataUpdatedAt = currentAt
        )
    }
    dispatch(action)
}

/**
 * Dispatches the mutate failure.
 *
 * @param error The mutation error.
 */
fun <T> MutationCommand.Context<T>.dispatchMutateFailure(error: Throwable) {
    val currentAt = epoch()
    val currentError = state.error
    val action = if (currentError != null && options.errorEquals?.invoke(currentError, error) == true) {
        MutationAction.MutateFailure(
            error = currentError,
            errorUpdatedAt = state.errorUpdatedAt
        )
    } else {
        MutationAction.MutateFailure(
            error = error,
            errorUpdatedAt = currentAt
        )
    }
    dispatch(action)
}


fun <T> MutationCommand.Context<T>.reportMutationError(error: Throwable, id: UniqueId, marker: Marker) {
    if (options.onError == null && relay == null) {
        return
    }
    val record = ErrorRecord(error, id, marker)
    options.onError?.invoke(record, state)
    val errorRelay = relay
    if (errorRelay != null && options.shouldSuppressErrorRelay?.invoke(record, state) != true) {
        errorRelay(record)
    }
}

internal fun <T> MutationCommand.Context<T>.onRetryCallback(
    id: UniqueId,
): RetryCallback? {
    options.logger ?: return null
    return { err, count, nextBackOff ->
        options.vvv(id) { "retry(count=$count next=$nextBackOff error=${err.message})" }
    }
}
