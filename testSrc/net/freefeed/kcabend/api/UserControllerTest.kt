package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.Feeds
import net.freefeed.kcabend.persistence.TestPostStore
import net.freefeed.kcabend.persistence.TestUserStore
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
    protected var commentsController: CommentsController by Delegates.notNull()
    protected var timelineController: TimelineController by Delegates.notNull()

    Before fun setUp() {
        feeds = Feeds(TestUserStore(), TestPostStore())
        authenticator = Authenticator(feeds, "Secret")
        userController = UserController(feeds, authenticator)
        postController = PostController(feeds)
        commentsController = CommentsController(feeds, postController)
        timelineController = TimelineController(feeds)
    }

    protected fun createUser(userName: String) : User {
        val createUserResponse = userController.createUser(userName, "password")
        val profile = createUserResponse.getRootObject("users")
        return feeds.users.getUser(Integer.parseInt(profile["id"]))
    }

    protected fun createPost(requestingUser: User, body: String): String {
        val response = postController.createPost(requestingUser, CreatePostRequest(CreatePostPost(body), null))
        return response.getRootObject("posts")["id"]
    }

    protected fun createComment(requestingUser: User, postId: String, body: String) {
        commentsController.createComment(requestingUser,
                CreateCommentRequest(CreateCommentComment(body, postId)))
    }
}

public class UserControllerTest : AbstractControllerTest() {
    Test fun createUser() {
        val response = userController.createUser("Alpha", "password")
        val profile = response.getRootObject("users")
        assertEquals("user", profile["type"])
        assertNotNull(response["authToken"])
    }

    Test fun whoami() {
        val alpha = createUser("alpha")
        val response = userController.whoami(alpha)
        val profile = response.getRootObject("users")
        assertEquals("alpha", profile["username"])
    }
}