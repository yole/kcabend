package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.Feeds
import net.freefeed.kcabend.model.ForbiddenException
import net.freefeed.kcabend.model.TestPostStore
import net.freefeed.kcabend.model.TestUserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

import kotlin.properties.Delegates

public class AuthenticatorTest {
    private var feeds: Feeds by Delegates.notNull()
    private var authenticator: Authenticator by Delegates.notNull()

    Before fun setUp() {
        feeds = Feeds(TestUserStore(), TestPostStore())
        authenticator = Authenticator(feeds, "Secret")
    }

    Test fun verifyPassword() {
        authenticator.createUser("Alpha", "alpha@freefeed.net", "password")
        assertNotNull(authenticator.verifyPassword("Alpha", "password"))
    }

    Test(expected = ForbiddenException::class) fun dontVerifyWrongPassword() {
        authenticator.createUser("Alpha", "alpha@freefeed.net", "password")
        authenticator.verifyPassword("Alpha", "Shmassword")
    }

    Test fun verifyAuthToken() {
        val user = authenticator.createUser("Alpha", "alpha@freefeed.net", "password")
        val authToken = authenticator.verifyPassword("Alpha", "password")
        val loggedInUser = authenticator.verifyAuthToken(authToken)
        assertEquals(loggedInUser.id, user.id)
    }

    Test(expected = ForbiddenException::class) fun dontVerifyWrongAuthToken() {
        authenticator.createUser("Alpha", "alpha@freefeed.net", "password")
        val authToken = authenticator.verifyPassword("Alpha", "password")
        authenticator.verifyAuthToken("!!" + authToken)
    }
}
