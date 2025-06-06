// Copyright 2024 Soil Contributors
// SPDX-License-Identifier: Apache-2.0

package soil.query

import soil.query.core.DataModel

/**
 * Data model for the state handled by [SubscriptionKey].
 *
 * All data models related to subscriptions, implement this interface.
 *
 * @param T Type of data to receive.
 */
interface SubscriptionModel<out T> : DataModel<T> {

    /**
     * The timestamp when the subscription was restarted.
     */
    val restartedAt: Long

    /**
     * The received status of the subscription.
     */
    val status: SubscriptionStatus

    /**
     * The revision of the currently snapshot.
     */
    val revision: String get() = "d-$replyUpdatedAt/e-$errorUpdatedAt"

    /**
     * Returns `true` if the query is pending, `false` otherwise.
     */
    val isPending: Boolean get() = status == SubscriptionStatus.Pending

    /**
     * Returns `true` if the query is successful, `false` otherwise.
     */
    val isSuccess: Boolean get() = status == SubscriptionStatus.Success

    /**
     * Returns `true` if the query is a failure, `false` otherwise.
     */
    val isFailure: Boolean get() = status == SubscriptionStatus.Failure

    /**
     * Returns true if the [SubscriptionModel] is awaited.
     *
     * @see DataModel.isAwaited
     */
    override fun isAwaited(): Boolean {
        return isPending
    }
}

/**
 * The received status of the subscription.
 */
enum class SubscriptionStatus {
    Pending,
    Success,
    Failure
}
