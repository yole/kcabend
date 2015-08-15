package net.freefeed.kcabend.model

import org.junit.Assert.assertEquals
import org.junit.Test

public class LikesTest : AbstractModelTest() {

    Test fun usersCanLikePosts() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val post = user1.publishPost("Hello World")
        user2.likePost(post)

        val user1Posts = user1.readOwnPosts()
        assertEquals(1, user1Posts[0].likes.size())
    }

    Test fun likesArePersisted() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val post = user1.publishPost("Hello World")
        user2.likePost(post)

        reload()

        val user1Posts = user1.reload().readOwnPosts()
        assertEquals(1, user1Posts[0].likes.size())
    }

    Test(expected = ForbiddenException::class) fun usersCantLikePostsTheyCantSee() {
        val user1 = testFeeds.users.createUser("Alpha", "alpha@freefeed.net", private = true)
        val user2 = testFeeds.users.createUser("Beta", "beta@freefeed.net")
        val post = user1.publishPost("Hello World")
        user2.likePost(post)
    }

    Test fun likedPostAppearsInLikesTimeline() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val post = user1.publishPost("Hello World")
        user2.likePost(post)

        val user2Likes = user2.likesTimeline.getPosts(user2)
        assertEquals(1, user2Likes.size())
    }

    Test fun likesTimelineIsLoaded() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val post1 = user1.publishPost("Hello World")
        val post2 = user1.publishPost("Hello World 2")
        user2.likePost(post1)
        user2.likePost(post2)

        reload()

        val user2Likes = user2.reload().likesTimeline.getPosts(user2)
        assertEquals(2, user2Likes.size())
        assertEquals("Hello World 2", user2Likes[0].body)
        assertEquals("Hello World", user2Likes[1].body)
    }

    Test fun likedPostAppearsInSubscribersTimeline() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        val post = user1.publishPost("Hello World")
        user2.likePost(post)

        val user3Timeline = user3.readHomePosts()
        assertEquals(1, user3Timeline.size())
    }

    Test fun likedPostIsVisibleInSubscribersTimelineAfterReload() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        val post = user1.publishPost("Hello World")
        user2.likePost(post)

        reload()

        val user3Timeline = user3.reload().readHomePosts()
        assertEquals(1, user3Timeline.size())
    }

    Test fun likedPostDisappearsFromSubscribersTimelineAfterDeletion() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        val post = user1.publishPost("Hello World")
        user2.likePost(post)
        assertEquals(1, user3.homeFeed.postCount)

        user1.deletePost(post)
        assertEquals(0, user3.homeFeed.postCount)
    }

    Test fun subscribersTimelineShowsWhoLikedPost() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        ensureLoaded(user3.homeFeed)

        val post = user1.publishPost("Hello World")
        user2.likePost(post)

        val user3Timeline = user3.readHomePosts()
        assertEquals(user2.id, user3Timeline [0].reason?.userId)
        assertEquals(ShowReasonAction.Like, user3Timeline [0].reason?.action)
    }

    Test fun subscribersTimelineShowsWhoLikedPostAfterReload() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)

        val post = user1.publishPost("Hello World")
        user2.likePost(post)

        reload()

        val user3Timeline = user3.reload().readHomePosts()
        assertEquals(user2.id, user3Timeline [0].reason?.userId)
        assertEquals(ShowReasonAction.Like, user3Timeline [0].reason?.action)
    }

    Test fun likeBumpsPost() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user2.subscribeTo(user1)
        user2.subscribeTo(user3)

        val post1 = user1.publishPost("Hello World")
        val post2 = user1.publishPost("Hello World Two")

        var user2Timeline = user2.readHomePosts()
        assertEquals("Hello World Two", user2Timeline[0].body)
        assertEquals("Hello World", user2Timeline[1].body)

        user3.likePost(post1)

        user2Timeline = user2.readHomePosts()
        assertEquals("Hello World", user2Timeline[0].body)
        assertEquals("Hello World Two", user2Timeline[1].body)
    }

    Test fun likeBumpsPostAfterReload() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user2.subscribeTo(user1)
        user2.subscribeTo(user3)

        val post1 = user1.publishPost("Hello World")
        val post2 = user1.publishPost("Hello World Two")

        user3.likePost(post1)

        reload()

        val user2Timeline = user2.reload().readHomePosts()
        assertEquals("Hello World", user2Timeline[0].body)
        assertEquals("Hello World Two", user2Timeline[1].body)
    }

    Test fun usersCanUnlikePosts() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val post = user1.publishPost("Hello World")
        user2.likePost(post)
        user2.unlikePost(post)

        var user1Posts = user1.readOwnPosts()
        assertEquals(0, user1Posts[0].likes.size())

        reload()
        user1Posts = user1.reload().readOwnPosts()
        assertEquals(0, user1Posts[0].likes.size())
    }

    Test fun unlikedPostDisappearsFromLikersSubscribersTimelineIfThereAreNoOtherLikers() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        val post = user1.publishPost("Hello World")
        user2.likePost(post)
        assertEquals(1, user3.homeFeed.postCount)

        user2.unlikePost(post)
        assertEquals(0, user3.homeFeed.postCount)
        assertEquals(0, user2.likesTimeline.postCount)
    }

    Test fun unlikedPostShowsDifferentReasonIfThereIsAnotherLiker() {
        val (user1, user2, user3, user4) = createUsers("Alpha", "Beta", "Gamma", "Delta")
        user3.subscribeTo(user2)
        user3.subscribeTo(user4)
        val post = user1.publishPost("Hello World")
        user2.likePost(post)
        user4.likePost(post)

        var user3HomePosts = user3.readHomePosts()
        assertEquals(user2.id, user3HomePosts [0].reason?.userId)
        assertEquals(ShowReasonAction.Like, user3HomePosts [0].reason?.action)

        user2.unlikePost(post)

        user3HomePosts = user3.readHomePosts()
        assertEquals(1, user3HomePosts.size())
        assertEquals(user4.id, user3HomePosts [0].reason?.userId)
        assertEquals(ShowReasonAction.Like, user3HomePosts [0].reason?.action)
    }

    Test fun likesOfBlockedUsersArentShown() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        user1.blockUser(user3)
        val post = user2.publishPost("Hello World")
        user1.likePost(post)

        val posts = user3.readHomePosts()
        assertEquals(0, posts[0].likes.size())
    }

    Test fun omittedLikes() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        val post = user1.publishPost("Hello World")
        user1.likePost(post)
        user2.likePost(post)
        user3.likePost(post)

        var postView = testFeeds.posts.createView(post, user1, maxLikes = 2)
        assertEquals(2, postView.likes.size())
        assertEquals(user1.id, postView.likes[0].id)
        assertEquals(user3.id, postView.likes[1].id)
        assertEquals(1, postView.omittedLikes)

        reload()

        val newUser1 = user1.reload()
        val newPost = testFeeds.posts.getPost(post.id, newUser1)!!
        postView = testFeeds.posts.createView(newPost, newUser1, maxLikes = 2)
        assertEquals(2, postView.likes.size())
        assertEquals(user1.id, postView.likes[0].id)
        assertEquals(user3.id, postView.likes[1].id)
        assertEquals(1, postView.omittedLikes)
    }
}
