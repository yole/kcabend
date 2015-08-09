package net.freefeed.kcabend.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentsTest : AbstractModelTest() {
    Test public fun usersCanCommentOnPosts() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val post = user1.publishPost("Hello World")
        user2.commentOnPost(post, "Nice to meet you")

        val user1Posts = user1.readOwnPosts()
        assertEquals(1, user1Posts[0].comments.size())
    }

    Test public fun commentsArePersisted() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val post = user1.publishPost("Hello World")
        user2.commentOnPost(post, "Nice to meet you")

        reload()

        val user1Posts = user1.reload().readOwnPosts()
        assertEquals(1, user1Posts[0].comments.size())
    }

    Test(expected = ForbiddenException::class) fun usersCantCommentOnPostsTheyCantSee() {
        val user1 = testFeeds.users.createUser("Alpha", private = true)
        val user2 = testFeeds.users.createUser("Beta")
        val post = user1.publishPost("Hello World")
        user2.commentOnPost(post, "Foo")
    }

    Test fun commentedPostAppearsInCommentsTimeline() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val post = user1.publishPost("Hello World")
        user2.commentOnPost(post, "Foo")

        val user2Comments = user2.commentsTimeline.getPosts(user2)
        assertEquals(1, user2Comments.size())
    }

    Test fun commentsTimelineIsLoaded() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val post = user1.publishPost("Hello World")
        user2.commentOnPost(post, "Foo")

        reload()

        val newUser2 = user2.reload()
        val user2Comments = newUser2.commentsTimeline.getPosts(newUser2)
        assertEquals(1, user2Comments.size())
    }

    Test fun commentedPostAppearsInSubscribersTimeline() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        val post = user1.publishPost("Hello World")
        user2.commentOnPost(post, "Foo")

        val user3Timeline = user3.readHomePosts()
        assertEquals(1, user3Timeline.size())
    }

    Test fun commentedPostIsVisibleInSubscribersTimelineAfterReload() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        val post = user1.publishPost("Hello World")
        user2.commentOnPost(post, "Foo")

        reload()

        val user3Timeline = user3.reload().readHomePosts()
        assertEquals(1, user3Timeline.size())
    }

    Test fun commentedPostDisappearsFromSubscribersTimelineAfterDeletion() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        val post = user1.publishPost("Hello World")
        user2.commentOnPost(post, "Foo")
        assertEquals(1, user3.homeFeed.postCount)

        user1.deletePost(post)
        assertEquals(0, user3.homeFeed.postCount)
    }

    Test fun subscribersTimelineShowsWhoCommentedOnPost() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        ensureLoaded(user3.homeFeed)

        val post = user1.publishPost("Hello World")
        user2.commentOnPost(post, "Foo")

        val user3Timeline = user3.readHomePosts()
        assertEquals(user2.id, user3Timeline [0].reason?.userId)
        assertEquals(ShowReasonAction.Comment, user3Timeline [0].reason?.action)
    }

    Test fun subscribersTimelineShowsWhoCommentedPostAfterReload() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)

        val post = user1.publishPost("Hello World")
        user2.commentOnPost(post, "Foo")

        reload()

        val user3Timeline = user3.reload().readHomePosts()
        assertEquals(user2.id, user3Timeline [0].reason?.userId)
        assertEquals(ShowReasonAction.Comment, user3Timeline [0].reason?.action)
    }

    Test fun commentsBumpPost() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user2.subscribeTo(user1)
        user2.subscribeTo(user3)

        val post1 = user1.publishPost("Hello World")
        user1.publishPost("Hello World Two")

        var user2Timeline = user2.readHomePosts()
        assertEquals("Hello World Two", user2Timeline[0].body)
        assertEquals("Hello World", user2Timeline[1].body)

        user3.commentOnPost(post1, "Foo")

        user2Timeline = user2.readHomePosts()
        assertEquals("Hello World", user2Timeline[0].body)
        assertEquals("Hello World Two", user2Timeline[1].body)
    }

    Test fun commentBumpsPostAfterReload() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user2.subscribeTo(user1)
        user2.subscribeTo(user3)

        val post1 = user1.publishPost("Hello World")
        val post2 = user1.publishPost("Hello World Two")

        user3.commentOnPost(post1, "Foo")

        reload()

        val user2Timeline = user2.reload().readHomePosts()
        assertEquals("Hello World", user2Timeline[0].body)
        assertEquals("Hello World Two", user2Timeline[1].body)
    }




}