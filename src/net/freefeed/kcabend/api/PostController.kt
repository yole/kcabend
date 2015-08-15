package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.*

class CreatePostMeta(val feeds: Array<String>)
class CreatePostPost(val body: String)
class CreatePostRequest(val post: CreatePostPost, val meta: CreatePostMeta?)

class PostController(val feeds: Feeds) {
    public val serializer: PostSerializer = PostSerializer(feeds)

    fun createPost(requestingUser: User, request: CreatePostRequest): ObjectListResponse {
        val post = requestingUser.publishPost(request.post.body)
        return respondWithPostView(post, requestingUser)
    }

    fun getPost(requestingUser: User?, id: String): ObjectListResponse {
        val post = findPostByStringId(requestingUser, id)
        return respondWithPostView(post, requestingUser)
    }

    fun like(requestingUser: User, id: String): ObjectListResponse {
        val post = findPostByStringId(requestingUser, id)
        requestingUser.likePost(post)
        return ObjectListResponse.Empty
    }

    public fun findPostByStringId(requestingUser: User?, id: String): Post {
        val intId = try {
            Integer.parseInt(id)
        } catch(e: NumberFormatException) {
            throw BadRequestException()
        }

        return feeds.posts.getPost(intId, requestingUser) ?: throw ForbiddenException()
    }

    private fun respondWithPostView(post: Post, requestingUser: User?): ObjectListResponse {
        val postView = feeds.posts.createView(post, requestingUser)
        return ObjectListResponse().withRootObject(postView, serializer)
    }
}

class PostSerializer(val feeds: Feeds) : ObjectSerializer<PostView>() {
    private val commentSerializer = CommentSerializer(feeds)

    override val key: String get() = "posts"

    override fun serialize(response: ObjectResponse, value: PostView) {
        val author = feeds.users.getUser(value.authorId)
        response.serializeProperties(value, "id", "body", "omittedLikes")
        response.serializeObjectProperty("createdBy", author, UserSerializer)
        response.serializeObjectList("likes", value.likes, UserSerializer)
        response.serializeObjectList("comments", value.comments, commentSerializer)
    }
}
