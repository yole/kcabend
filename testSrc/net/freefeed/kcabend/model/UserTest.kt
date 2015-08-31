package net.freefeed.kcabend.model

import net.freefeed.kcabend.persistence.FeedData
import net.freefeed.kcabend.persistence.FeedType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

public class UserTest : AbstractModelTest() {
    Test public fun userIsPersisted() {
        testFeeds.users.createUser("Alpha", "alpha@freefeed.net")
        assertEquals(1, testUserStore.users.size())
    }

    Test public fun userIsLoaded() {
        val id = testUserStore.createFeed(FeedData(FeedType.User, "alpha", "alpha@freefeed.net", null, "Alpha", "alpha", false))
        val user = testFeeds.users[id]
        assertEquals("alpha", user.userName)
    }

    Test public fun findByUsername() {
        val id = testUserStore.createFeed(FeedData(FeedType.User, "alpha", "alpha@freefeed.net", null, "Alpha", "alpha", false))
        val user = testFeeds.users.findByUserName("alpha")
        assertEquals(id, user!!.id)
    }

    Test public fun subscriptionsAreLoaded() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        user2.subscribeTo(user1)

        reload()

        val newUser2 = user2.reload()
        assertEquals(1, newUser2.subscriptions.size())
        val newUser1 = testFeeds.users[user1.id]
        assertEquals(1, newUser1.subscribers.size())
    }

    Test(expected = ValidationException::class) public fun subscribeToSelf() {
        val user1 = createUser("alpha")
        user1.subscribeTo(user1)
    }

    Test(expected = ForbiddenException::class) public fun subscribeToPrivateUser() {
        val user1 = createUser("Alpha", private = true)
        val user2 = createUser("Beta")
        user2.subscribeTo(user1)
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

    Test(expected = ValidationException::class) public fun userNameMustBeUnique() {
        createUser("Alpha")
        createUser("Alpha")
    }

    Test public fun userCanSendSubscriptionRequest() {
        val user1 = createUser("alpha", private = true)
        val user2 = createUser("beta")

        user2.sendSubscriptionRequest(user1)
        assertTrue(user2.id in user1.subscriptionRequests)

        reload()
        val user1new = user1.reload()
        assertTrue(user2.id in user1new.subscriptionRequests)
    }

    Test public fun userCanAcceptSubscriptionRequest() {
        val user1 = createUser("alpha", private = true)
        val user2 = createUser("beta")

        user2.sendSubscriptionRequest(user1)
        user1.acceptSubscriptionRequest(user2, user1)
        assertTrue(user2.id !in user1.subscriptionRequests)
        assertTrue(user2.id in user1.subscribers)

        reload()
        val user1new = user1.reload()
        assertTrue(user2.id !in user1new.subscriptionRequests)
    }

    Test public fun userCanRejectSubscriptionRequest() {
        val user1 = createUser("alpha", private = true)
        val user2 = createUser("beta")

        user2.sendSubscriptionRequest(user1)
        user1.rejectSubscriptionRequest(user2, user1)
        assertTrue(user2.id !in user1.subscriptionRequests)
        assertTrue(user2.id !in user1.subscribers)
    }

    Test(expected = ForbiddenException::class) fun userCantAcceptOtherUsersSubscriptionRequests() {
        val user1 = createUser("alpha", private = true)
        val user2 = createUser("beta")

        user2.sendSubscriptionRequest(user1)
        user2.acceptSubscriptionRequest(user2, user1)
    }

    Test(expected = ForbiddenException::class) fun blockedUserCantSendSubscriptionRequest() {
        val user1 = createUser("alpha", private = true)
        val user2 = createUser("beta")
        user1.blockUser(user2)
        user2.sendSubscriptionRequest(user1)
    }
}
