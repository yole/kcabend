package net.freefeed.kcabend.model

import org.junit.Assert.assertEquals
import org.junit.Ignore
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
        val user1 = testFeeds.users.createUser("Alpha", "alpha@frefeed.net", private = true)
        val user2 = testFeeds.users.createUser("Beta", "beta@freefreed.net")
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

    Test fun usersCanDeleteComments() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val post = user1.publishPost("Hello World")
        val comment = user2.commentOnPost(post, "Foo")
        user2.deleteComment(comment)

        var user1Posts = user1.readOwnPosts()
        assertEquals(0, user1Posts[0].comments.size())

        reload()
        user1Posts = user1.reload().readOwnPosts()
        assertEquals(0, user1Posts[0].comments.size())
    }

    Test(expected = ForbiddenException::class) fun usersCantDeleteOtherUsersComments() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        val post = user1.publishPost("Hello World")
        val comment = user2.commentOnPost(post, "Foo")
        user3.deleteComment(comment)
    }

    Test fun uncommentedPostDisappearsFromCommentersSubscribersTimelineIfThereAreNoOtherCommenters() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        val post = user1.publishPost("Hello World")
        val comment = user2.commentOnPost(post, "Foo")
        assertEquals(1, user3.homeFeed.postCount)

        user2.deleteComment(comment)
        assertEquals(0, user3.homeFeed.postCount)
        assertEquals(0, user2.commentsTimeline.postCount)
    }

    Test fun uncommentedPostRemainsInCommentsTimelineIfThereAreOtherComments() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        val post = user1.publishPost("Hello World")
        val comment = user2.commentOnPost(post, "Foo")
        user2.commentOnPost(post, "Bar")
        assertEquals(1, user3.homeFeed.postCount)

        user2.deleteComment(comment)
        assertEquals(1, user3.homeFeed.postCount)
        assertEquals(1, user2.commentsTimeline.postCount)
    }

    Test fun uncommentedPostShowsDifferentReasonIfThereIsAnotherCommenter() {
        val (user1, user2, user3, user4) = createUsers("Alpha", "Beta", "Gamma", "Delta")
        user3.subscribeTo(user2)
        user3.subscribeTo(user4)
        val post = user1.publishPost("Hello World")
        val user2Comment = user2.commentOnPost(post, "Foo")
        user4.commentOnPost(post, "Bar")

        var user3HomePosts = user3.readHomePosts()
        assertEquals(user2.id, user3HomePosts [0].reason?.userId)
        assertEquals(ShowReasonAction.Comment, user3HomePosts [0].reason?.action)

        user2.deleteComment(user2Comment)

        user3HomePosts = user3.readHomePosts()
        assertEquals(1, user3HomePosts.size())
        assertEquals(user4.id, user3HomePosts [0].reason?.userId)
        assertEquals(ShowReasonAction.Comment, user3HomePosts [0].reason?.action)
    }

    Test fun deletingAnotherUsersCommentShouldUpdateTimelinesOfThatUsersSubscribers() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        val post = user1.publishPost("Hello World")
        val comment = user2.commentOnPost(post, "Foo")
        assertEquals(1, user3.homeFeed.postCount)

        user1.deleteComment(comment)
        assertEquals(0, user3.homeFeed.postCount)
        assertEquals(0, user2.commentsTimeline.postCount)
    }

    Test fun commentsOfBlockedUsersArentShown() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        user1.blockUser(user3)
        val post = user2.publishPost("Hello World")
        user1.commentOnPost(post, "Foo")

        val posts = user3.readHomePosts()
        assertEquals(0, posts[0].comments.size())
    }
}
