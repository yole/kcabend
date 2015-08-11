package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.User
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.properties.Delegates

public class PostControllerTest : AbstractControllerTest() {
    private var userAlpha: User by Delegates.notNull()

    Before fun createUser() {
        userAlpha = createUser("Alpha")
    }

    Test fun createPost() {
        val response = postController.createPost(userAlpha, CreatePostRequest(CreatePostPost("Hello World"), null))
        val posts = response.getRootObject("posts")
        assertEquals("Hello World", posts["body"])
        assertEquals(userAlpha.id.toString(), posts["createdBy"])
        val createdBy = response.getObject("users", userAlpha.id)
        assertEquals("Alpha", createdBy["username"])
    }
}
