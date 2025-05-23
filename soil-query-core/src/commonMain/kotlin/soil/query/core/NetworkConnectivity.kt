// Copyright 2024 Soil Contributors
// SPDX-License-Identifier: Apache-2.0

package soil.query.core

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlin.time.Duration

/**
 * Interface for receiving events of network connectivity.
 */
interface NetworkConnectivity {

    /**
     * Adds an [observer] to receive events.
     */
    fun addObserver(observer: Observer)

    /**
     * Removes an [observer] that receives events.
     */
    fun removeObserver(observer: Observer)

    /**
     * Provides a Flow to receive events of network connectivity.
     */
    fun asFlow(): Flow<NetworkConnectivityEvent> = callbackFlow {
        val observer = object : Observer {
            override fun onReceive(event: NetworkConnectivityEvent) {
                trySend(event)
            }
        }
        addObserver(observer)
        awaitClose { removeObserver(observer) }
    }

    /**
     * Observer interface for receiving events of network connectivity.
     */
    interface Observer {

        /**
         * Receives a [event] of network connectivity.
         */
        fun onReceive(event: NetworkConnectivityEvent)
    }

    /**
     * An object indicating unsupported for the capability of network connectivity.
     */
    companion object Unsupported : NetworkConnectivity {
        override fun addObserver(observer: Observer) = Unit

        override fun removeObserver(observer: Observer) = Unit
    }
}

/**
 * Events of network connectivity.
 */
enum class NetworkConnectivityEvent {

    /**
     * The network is available.
     */
    Available,

    /**
     * The network is lost.
     */
    Lost
}

internal suspend fun observeOnNetworkReconnect(
    networkConnectivity: NetworkConnectivity,
    networkResumeAfterDelay: Duration,
    collector: FlowCollector<Unit>
) {
    networkConnectivity.asFlow()
        .distinctUntilChanged()
        .scan(NetworkConnectivityEvent.Available to NetworkConnectivityEvent.Available) { acc, state -> state to acc.first }
        .filter { it.first == NetworkConnectivityEvent.Available && it.second == NetworkConnectivityEvent.Lost }
        .onEach { delay(networkResumeAfterDelay) }
        .collect { collector.emit(Unit) }
}
