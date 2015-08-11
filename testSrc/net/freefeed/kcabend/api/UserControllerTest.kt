package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.Feeds
import net.freefeed.kcabend.model.TestPostStore
import net.freefeed.kcabend.model.TestUserStore
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import kotlin.properties.Delegates

public class UserControllerTest {
    private var feeds: Feeds by Delegates.notNull()
    private var authenticator: Authenticator by Delegates.notNull()
    private var userController: UserController by Delegates.notNull()

    Before fun setUp() {
        feeds = Feeds(TestUserStore(), TestPostStore())
        authenticator = Authenticator(feeds, "Secret")
        userController = UserController(feeds, authenticator)
    }

    Test fun createUser() {
        val response = userController.createUser(CreateUserRequest("Alpha", "alpha@gmail.com", "password"))
        val profile = response.getRootObject("users")
        assertEquals("user", profile["type"])
        assertNotNull(response["authToken"])
    }
}