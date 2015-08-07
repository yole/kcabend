package net.freefeed.kcabend.model

import net.freefeed.kcabend.persistence.UserData
import org.junit.Assert.assertEquals
import org.junit.Test

public class UserTest : AbstractModelTest() {
    Test public fun userIsPersisted() {
        testFeeds.users.createUser("Alpha")
        assertEquals(1, testUserStore.users.size())
    }

    Test public fun userIsLoaded() {
        val id = testUserStore.createUser(UserData("alpha", "Alpha", "alpha", false))
        val user = testFeeds.users[id]
        assertEquals("alpha", user.userName)
    }

    Test public fun subscriptionsAreLoaded() {
        val user1 = testFeeds.users.createUser("Alpha", private = true)
        val user2 = testFeeds.users.createUser("Beta")
        user2.subscribeTo(user1)

        reload()

        val newUser2 = testFeeds.users[user2.id]
        assertEquals(1, newUser2.subscriptions.size())
        val newUser1 = testFeeds.users[user1.id]
        assertEquals(1, newUser1.subscribers.size())
    }

    Test public fun unsubscribe() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        user2.subscribeTo(user1)
        user2.unsubscribeFrom(user1)

        assertEquals(0, user1.subscribers.size())

        reload()

        assertEquals(0, user1.reload().subscribers.size())
    }

    Test public fun usersCanBeBlocked() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        user2.subscribeTo(user1)

        user1.blockUser(user2)

        assertEquals(1, user1.blockedUsers.size())
        assertEquals(0, user2.subscriptions.size())

        reload()

        assertEquals(1, user1.blockedUsers.size())
    }

    Test(expected = ForbiddenException::class) public fun blockedUserCantSubscribe() {
        val (user1, user2) = createUsers("Alpha", "Beta")

        user1.blockUser(user2)
        user2.subscribeTo(user1)
    }

    Test public fun usersCanBeUnblocked() {
        val (user1, user2) = createUsers("Alpha", "Beta")

        user1.blockUser(user2)
        user1.unblockUser(user2)

        assertEquals(0, user1.blockedUsers.size())

        reload()

        assertEquals(0, user1.blockedUsers.size())
    }
}
