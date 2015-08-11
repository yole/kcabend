package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.Feeds
import net.freefeed.kcabend.model.Post
import net.freefeed.kcabend.model.User

class CreatePostMeta(val feeds: Array<String>)
class CreatePostPost(val body: String)
class CreatePostRequest(val post: CreatePostPost, val meta: CreatePostMeta?)

class PostController(val feeds: Feeds) {
    fun createPost(requestingUser: User, request: CreatePostRequest): ObjectListResponse {
        val post = requestingUser.publishPost(request.post.body)
        val author = feeds.users.getUser(post.authorId)
        return ObjectListResponse().serializePost(post, author)
    }
}

fun ObjectListResponse.serializePost(post: Post, author: User): ObjectListResponse {
    val postResponse = createRootObject("posts")
    postResponse.serializeProperties(post, "id", "body")
    postResponse.serializeObjectProperty("createdBy", author, UserSerializer)
    return this
}
