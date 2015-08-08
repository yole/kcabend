package net.freefeed.kcabend.model

import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Ignore

public class GroupsTest : AbstractModelTest() {
    Test public fun canCreateGroup() {
        val (user1) = createUsers("Alpha")
        val group1 = user1.createGroup("Piglets")
        assertTrue(user1.id in group1.subscribers)
        assertTrue(user1.id in group1.admins)

        reload()

        val group1New = testFeeds.users[group1.id]
        assertTrue(group1New is Group)
        assertTrue(user1.id in (group1New as Group).admins)
    }

    Test public fun canPostToGroup() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val group = user1.createGroup("Piglets")
        user2.subscribeTo(group)

        user1.publishPost("Hello Piglets", group)

        user2.verifyHomePosts("Hello Piglets")
    }

    Test(expected = ForbiddenException::class) public fun nonSubscriberCantPostToGroup() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val group = user1.createGroup("Piglets")
        user2.publishPost("Hello Piglets", group)
    }

    Test public fun canCrosspostToOwnFeedAndGroup() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        val group = user1.createGroup("Piglets")
        user2.subscribeTo(group)
        user3.subscribeTo(user1)

        user1.publishPost("Hello Piglets and Gamma", user1, group)
        user2.verifyHomePosts("Hello Piglets and Gamma")
        user3.verifyHomePosts("Hello Piglets and Gamma")
    }

    Test public fun canDeletePostInGroup() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val group = user1.createGroup("Piglets")
        user2.subscribeTo(group)

        val post = user1.publishPost("Hello Piglets", group)
        user2.verifyHomePosts("Hello Piglets")
        user1.deletePost(post)

        assertEquals(0, user2.homeFeed.postCount)
    }

    Test public fun likingPostInGroupDoesNotPromoteItToSubscribers() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        val group = user1.createGroup("Piglets")
        user2.subscribeTo(group)
        user3.subscribeTo(user2)

        val post = user1.publishPost("Hello Piglets", group)
        user2.likePost(post)

        assertEquals(0, user3.homeFeed.postCount)
    }

    // TODO: Is a post shown in my likes timeline if it is posted only to groups?

    Test public fun groupAdminsCanDeletePostInGroup() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        val group = user1.createGroup("Piglets")
        user2.subscribeTo(group)
        user3.subscribeTo(group)

        val post = user2.publishPost("Hello Piglets", group)
        user1.deletePost(post)

        assertEquals(0, user3.homeFeed.postCount)
    }

    Test public fun groupAdminsCanAddOtherAdmins() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val group = user1.createGroup("Piglets")
        user1.addGroupAdmin(group, user2)
        assertEquals(2, group.admins.size())

        reload()

        assertEquals(2, group.reload().admins.size())
    }

    Test(expected = ForbiddenException::class) public fun groupNonAdminsCannotAddAdmins() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        val group = user1.createGroup("Piglets")
        user2.addGroupAdmin(group, user3)
    }

    Test public fun groupAdminsCanRemoveOtherAdmins() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val group = user1.createGroup("Piglets")
        user1.addGroupAdmin(group, user2)
        user2.removeGroupAdmin(group, user1)
        assertEquals(1, group.admins.size())

        reload()

        assertEquals(1, group.reload().admins.size())
    }

    Test(expected = ForbiddenException::class) public fun cannotRemoveOnlyAdmin() {
        val (user1) = createUsers("Alpha")
        val group = user1.createGroup("Piglets")
        user1.removeGroupAdmin(group, user1)
    }

    // TODO: Who can delete a post if it's posted into multiple groups?
    // If a post is cross-posted, does an admin simply delete it from a group rather than removing it entirely?
}
