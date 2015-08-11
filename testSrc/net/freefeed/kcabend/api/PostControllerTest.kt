package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.Feeds
import net.freefeed.kcabend.model.TestPostStore
import net.freefeed.kcabend.model.TestUserStore
import net.freefeed.kcabend.model.User
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import kotlin.properties.Delegates

public class PostControllerTest {
    private var feeds: Feeds by Delegates.notNull()
    private var authenticator: Authenticator by Delegates.notNull()
    private var userController: UserController by Delegates.notNull()
    private var postController: PostController by Delegates.notNull()
    private var userAlpha: User by Delegates.notNull()

    Before fun setUp() {
        feeds = Feeds(TestUserStore(), TestPostStore())
        authenticator = Authenticator(feeds, "Secret")
        userController = UserController(feeds, authenticator)
        postController = PostController(feeds)

        val createUserResponse = userController.createUser(CreateUserRequest("Alpha", "alpha@freefeed.net", "password"))
        val alphaProfile = createUserResponse.getRootObject("users")
        userAlpha = feeds.users.getUser(Integer.parseInt(alphaProfile["id"]))
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
