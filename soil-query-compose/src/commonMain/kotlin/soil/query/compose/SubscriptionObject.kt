// Copyright 2024 Soil Contributors
// SPDX-License-Identifier: Apache-2.0

package soil.query.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import soil.query.SubscriptionModel
import soil.query.SubscriptionStatus
import soil.query.core.Reply
import soil.query.core.getOrNull
import soil.query.core.getOrThrow

/**
 * A SubscriptionObject represents [SubscriptionModel]s interface for receiving data.
 *
 * @param T Type of data to receive.
 */
@Stable
sealed interface SubscriptionObject<out T> : SubscriptionModel<T> {

    /**
     * The received value from the data source.
     */
    val data: T?

    /**
     * Resets the subscribed data and re-executes the subscription process.
     */
    val reset: suspend () -> Unit
}

/**
 * A SubscriptionLoadingObject represents the initial loading state of the [SubscriptionObject].
 *
 * This means that no data is being received and there is at least one more subscriber.
 *
 * @param T Type of data to receive.
 */
@Immutable
data class SubscriptionLoadingObject<T>(
    override val reply: Reply<T>,
    override val replyUpdatedAt: Long,
    override val error: Throwable?,
    override val errorUpdatedAt: Long,
    override val restartedAt: Long,
    override val reset: suspend () -> Unit
) : SubscriptionObject<T> {
    override val status: SubscriptionStatus = SubscriptionStatus.Pending
    override val data: T? get() = reply.getOrNull()
}

/**
 * A SubscriptionErrorObject represents the error state of the [SubscriptionObject].
 *
 * This means that an error occurred during the subscription process and the subscription is being stopped,
 * and you must call [reset] to restart the subscription.
 *
 * @param T Type of data to receive.
 */
@Immutable
data class SubscriptionErrorObject<T>(
    override val reply: Reply<T>,
    override val replyUpdatedAt: Long,
    override val error: Throwable,
    override val errorUpdatedAt: Long,
    override val restartedAt: Long,
    override val reset: suspend () -> Unit
) : SubscriptionObject<T> {
    override val status: SubscriptionStatus = SubscriptionStatus.Failure
    override val data: T? get() = reply.getOrNull()
}

/**
 * A SubscriptionSuccessObject represents the successful state of the [SubscriptionObject].
 *
 * @param T Type of data to receive.
 */
@Immutable
data class SubscriptionSuccessObject<T>(
    override val reply: Reply<T>,
    override val replyUpdatedAt: Long,
    override val error: Throwable?,
    override val errorUpdatedAt: Long,
    override val restartedAt: Long,
    override val reset: suspend () -> Unit
) : SubscriptionObject<T> {
    override val status: SubscriptionStatus = SubscriptionStatus.Success
    override val data: T get() = reply.getOrThrow()
}
