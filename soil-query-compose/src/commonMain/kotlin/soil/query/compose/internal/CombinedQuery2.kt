// Copyright 2024 Soil Contributors
// SPDX-License-Identifier: Apache-2.0

package soil.query.compose.internal

import androidx.compose.runtime.RememberObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import soil.query.QueryClient
import soil.query.QueryId
import soil.query.QueryKey
import soil.query.QueryRef
import soil.query.QueryState
import soil.query.compose.QueryConfig
import soil.query.core.uuid
import soil.query.merge


internal fun <T1, T2, R> newCombinedQuery(
    key1: QueryKey<T1>,
    key2: QueryKey<T2>,
    transform: (T1, T2) -> R,
    config: QueryConfig,
    client: QueryClient,
    scope: CoroutineScope
): QueryRef<R> = CombinedQuery2(key1, key2, transform, config, client, scope)

private class CombinedQuery2<T1, T2, R>(
    key1: QueryKey<T1>,
    key2: QueryKey<T2>,
    private val transform: (T1, T2) -> R,
    config: QueryConfig,
    client: QueryClient,
    private val scope: CoroutineScope
) : QueryRef<R>, RememberObserver {

    private val query1: QueryRef<T1> = client.getQuery(key1, config.marker)
    private val query2: QueryRef<T2> = client.getQuery(key2, config.marker)
    private val optimize: (QueryState<R>) -> QueryState<R> = config.optimizer::omit

    override val id: QueryId<R> = QueryId("auto/${uuid()}")

    // FIXME: Switch to K2 mode when it becomes stable.
    private val _state: MutableStateFlow<QueryState<R>> = MutableStateFlow(
        value = merge(query1.state.value, query2.state.value)
    )
    override val state: StateFlow<QueryState<R>> = _state

    override fun close() {
        query1.close()
        query2.close()
    }

    override suspend fun resume() {
        coroutineScope {
            val deferred1 = async { query1.resume() }
            val deferred2 = async { query2.resume() }
            awaitAll(deferred1, deferred2)
        }
    }

    override suspend fun invalidate() {
        coroutineScope {
            val deferred1 = async { query1.invalidate() }
            val deferred2 = async { query2.invalidate() }
            awaitAll(deferred1, deferred2)
        }
    }

    override suspend fun join() {
        coroutineScope {
            val job1 = launch { query1.join() }
            val job2 = launch { query2.join() }
            joinAll(job1, job2)
        }
    }

    private fun merge(state1: QueryState<T1>, state2: QueryState<T2>): QueryState<R> {
        return optimize(QueryState.merge(state1, state2, transform))
    }

    // ----- RememberObserver -----//
    private var job: Job? = null

    override fun onAbandoned() = stop()

    override fun onForgotten() = stop()

    override fun onRemembered() = start()

    private fun start() {
        job = scope.launch {
            combine(query1.state, query2.state, ::merge).collect { _state.value = it }
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
        close()
    }
}
