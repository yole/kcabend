package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.Feeds
import net.freefeed.kcabend.model.TestPostStore
import net.freefeed.kcabend.model.TestUserStore
import net.freefeed.kcabend.model.User
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import kotlin.properties.Delegates

public abstract class AbstractControllerTest {
    protected var feeds: Feeds by Delegates.notNull()
    protected var authenticator: Authenticator by Delegates.notNull()
    protected var userController: UserController by Delegates.notNull()
    protected var postController: PostController by Delegates.notNull()
    protected var timelineController: TimelineController by Delegates.notNull()

    Before fun setUp() {
        feeds = Feeds(TestUserStore(), TestPostStore())
        authenticator = Authenticator(feeds, "Secret")
        userController = UserController(feeds, authenticator)
        postController = PostController(feeds)
        timelineController = TimelineController(feeds)
    }

    protected fun createUser(userName: String) : User {
        val request = CreateUserRequest(userName, userName.toLowerCase() + "@freefeed.net", "password")
        val createUserResponse = userController.createUser(request)
        val profile = createUserResponse.getRootObject("users")
        return feeds.users.getUser(Integer.parseInt(profile["id"]))
    }

    protected fun createPost(requestingUser: User, body: String): String {
        val response = postController.createPost(requestingUser, CreatePostRequest(CreatePostPost(body), null))
        return response.getRootObject("posts")["id"]
    }
}

public class UserControllerTest : AbstractControllerTest() {
    Test fun createUser() {
        val response = userController.createUser(CreateUserRequest("Alpha", "alpha@gmail.com", "password"))
        val profile = response.getRootObject("users")
        assertEquals("user", profile["type"])
        assertNotNull(response["authToken"])
    }
}