package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.User
import org.junit.Before
import org.junit.Test
import kotlin.properties.Delegates
import kotlin.test.assertEquals

public class CommentsControllerTest : AbstractControllerTest() {
    private var userAlpha: User by Delegates.notNull()
    private var postId: String by Delegates.notNull()

    Before fun createUser() {
        userAlpha = createUser("Alpha")
        postId = createPost(userAlpha, "Hello World")
    }

    Test fun createComment() {
        val commentResponse = commentsController.createComment(userAlpha,
                CreateCommentRequest(CreateCommentComment("Foo", postId)))
        val comment = commentResponse.getRootObject("comments")
        assertEquals("Foo", comment["body"])
        assertEquals(userAlpha.id.toString(), comment["createdBy"])
    }
}
