package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.Comment
import net.freefeed.kcabend.model.Feeds
import net.freefeed.kcabend.model.User

data class CreateCommentComment(val body: String, val postId: String)
data class CreateCommentRequest(val comment: CreateCommentComment)

public class CommentsController(val feeds: Feeds, val postController: PostController) {
    private val commentSerializer = CommentSerializer(feeds)

    fun createComment(requestingUser: User, request: CreateCommentRequest): ObjectListResponse {
        val post = postController.findPostByStringId(requestingUser, request.comment.postId)
        val comment = requestingUser.commentOnPost(post, request.comment.body)
        return ObjectListResponse().withRootObject(comment, commentSerializer)
    }
}

public class CommentSerializer(val feeds: Feeds) : ObjectSerializer<Comment>() {
    override val key: String = "comments"

    override fun serialize(response: ObjectResponse, value: Comment) {
        response.serializeProperties(value, "id", "body", "createdAt")
        val author = feeds.users.getUser(value.author)
        response.serializeObjectProperty("createdBy", author, UserSerializer)
    }
}
