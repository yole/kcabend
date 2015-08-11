package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.Feeds
import net.freefeed.kcabend.model.Post
import net.freefeed.kcabend.model.PostView
import net.freefeed.kcabend.model.User

class CreatePostMeta(val feeds: Array<String>)
class CreatePostPost(val body: String)
class CreatePostRequest(val post: CreatePostPost, val meta: CreatePostMeta?)

class PostController(val feeds: Feeds) {
    public val serializer: PostSerializer = PostSerializer(feeds)

    fun createPost(requestingUser: User, request: CreatePostRequest): ObjectListResponse {
        val post = requestingUser.publishPost(request.post.body)
        return ObjectListResponse().withRootObject(feeds.posts.createView(post, requestingUser), serializer)
    }
}

class PostSerializer(val feeds: Feeds) : ObjectSerializer<PostView>() {
    override val key: String get() = "posts"

    override fun serialize(response: ObjectResponse, value: PostView) {
        val author = feeds.users.getUser(value.authorId)
        response.serializeProperties(value, "id", "body")
        response.serializeObjectProperty("createdBy", author, UserSerializer)
    }
}
