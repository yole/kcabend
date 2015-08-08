package net.freefeed.kcabend.model

import org.junit.Ignore
import org.junit.Test
import org.junit.Assert.assertEquals

public class DirectsTest : AbstractModelTest() {
    Test public fun usersCanSendDirects() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user1.subscribeTo(user2)
        user2.subscribeTo(user1)
        user3.subscribeTo(user1)

        user1.publishPost("Hello Beta", intArrayOf(user2.id))

        user1.verifyHomePosts("Hello Beta")
        user2.verifyHomePosts("Hello Beta")
        user3.verifyHomePosts()
    }

    Test public fun directsDontAppearInOwnPostsForOtherUser() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user1.subscribeTo(user2)
        user2.subscribeTo(user1)

        user1.publishPost("Hello Beta", intArrayOf(user2.id))

        val alphaPostsSeenByGamma = user1.ownPosts.getPosts(user3)
        assertEquals(0, alphaPostsSeenByGamma.size())
    }

    Test(expected = ForbiddenException::class) public fun cantSendPostToSelfAndOtherUser() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        user1.subscribeTo(user2)
        user2.subscribeTo(user1)

        user1.publishPost("Hello Beta", intArrayOf(user1.id, user2.id))
    }

    Test(expected = ForbiddenException::class) public fun cantSendDirectsToNonMutualSubscribers() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        user1.publishPost("Hello Beta", intArrayOf(user2.id))
    }

    Test public fun canSeeDirectMessagesOnly() {
        val (user1, user2) = createUsers("Alpha", "Beta", "Gamma")
        user1.subscribeTo(user2)
        user2.subscribeTo(user1)

        user1.publishPost("Hello Everyone")
        user1.publishPost("Hello Beta", intArrayOf(user2.id))

        val directs = user1.directMessagesTimeline.getPosts(user1)
        assertEquals(1, directs.size())
    }

    // TODO Can a recipient of a direct message delete it?
}
