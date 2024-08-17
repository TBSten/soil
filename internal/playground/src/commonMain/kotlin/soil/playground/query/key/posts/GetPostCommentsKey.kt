package soil.playground.query.key.posts

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import soil.playground.query.data.Comments
import soil.playground.query.data.PageParam
import soil.query.InfiniteQueryId
import soil.query.InfiniteQueryKey
import soil.query.receivers.ktor.buildKtorInfiniteQueryKey

class GetPostCommentsKey(private val postId: Int) : InfiniteQueryKey<Comments, PageParam> by buildKtorInfiniteQueryKey(
    id = Id(postId),
    fetch = { param ->
        get("https://jsonplaceholder.typicode.com/posts/$postId/comments") {
            parameter("_start", param.offset)
            parameter("_limit", param.limit)
        }.body()
    },
    initialParam = { PageParam() },
    loadMoreParam = { chunks ->
        chunks.lastOrNull()
            ?.takeIf { it.data.isNotEmpty() }
            ?.run { param.copy(offset = param.offset + param.limit) }
    }
) {
    class Id(postId: Int) : InfiniteQueryId<Comments, PageParam>(
        namespace = "posts/$postId/comments/*"
    )
}
