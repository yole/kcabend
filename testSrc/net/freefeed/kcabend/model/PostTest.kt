package net.freefeed.kcabend.model

import net.freefeed.kcabend.persistence.PostData
import net.freefeed.kcabend.persistence.UserData
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.properties.Delegates

public abstract class AbstractModelTest {
    var testUserStore: TestUserStore by Delegates.notNull()
    var testPostStore: TestPostStore by Delegates.notNull()
    var testFeeds: Feeds by Delegates.notNull()
    var currentTime: Long = 1

    Before fun setUp() {
        testUserStore = TestUserStore()
        testPostStore = TestPostStore()
        reload()
    }

    protected fun reload() {
        testFeeds = Feeds(testUserStore, testPostStore, { currentTime++ })
    }

    protected fun User.reload(): User = testFeeds.users[id]

    fun createUsers(vararg names: String): List<User> = names.map { testFeeds.users.createUser(it) }
    fun User.readHomePosts() = homeFeed.getPosts(this)
    fun User.readOwnPosts() = posts.getPosts(this)
}

public class PostTest : AbstractModelTest() {
    Test public fun testPostAppearsInUsersTimeline() {
        val user1 = testFeeds.users.createUser("Alpha")
        user1.publishPost("Hello World")

        val user1Posts = user1.posts.getPosts(null)
        assertEquals(1, user1Posts.size())
        assertEquals("Hello World", user1Posts[0].body)
    }

    Test public fun testPostIsPersisted() {
        val user1 = testFeeds.users.createUser("Alpha")
        user1.publishPost("Hello World")
        assertEquals(1, testPostStore.userPosts[user1.id]!!.size())
    }

    Test public fun testPostsAreLoaded() {
        val userId = testUserStore.createUser(UserData("alpha", "Alpha", "alpha", false))
        val postId = testPostStore.createPost(PostData(testFeeds.currentTime(), userId, intArrayOf(userId), "Hello World"))

        val user = testFeeds.users[userId]
        val userPosts = user.posts.getPosts(null)
        assertEquals("Hello World", userPosts[0].body)
    }

    Test public fun testPostsOfPrivateUserNotShown() {
        val user1 = testFeeds.users.createUser("Alpha", private = true)
        user1.publishPost("Hello World")

        val user1Posts = user1.posts.getPosts(null)
        assertEquals(0, user1Posts.size())
    }

    Test public fun testPostsOfPrivateUserShownToTheirSubscribers() {
        val user1 = testFeeds.users.createUser("Alpha", private = true)
        val user2 = testFeeds.users.createUser("Beta")
        user2.subscribeTo(user1)
        user1.publishPost("Hello World")

        val user1Posts = user1.posts.getPosts(user2)
        assertEquals(1, user1Posts.size())
    }

    Test public fun testPostVisibleInSubscriberRiverOfNews() {
        val user1 = testFeeds.users.createUser("Alpha")
        val user2 = testFeeds.users.createUser("Beta")
        user2.subscribeTo(user1)
        user1.publishPost("Hello World")

        val user2Posts = user2.homeFeed.getPosts(user2)
        assertEquals(1, user2Posts.size())
        assertEquals("Hello World", user2Posts [0].body)
    }

    Test public fun riverOfNewsIsLoaded() {
        val user1 = testFeeds.users.createUser("Alpha")
        val user2 = testFeeds.users.createUser("Beta")
        user2.subscribeTo(user1)
        user1.publishPost("Hello World")

        reload()

        val newUser2 = user2.reload()
        val user2Posts = newUser2.homeFeed.getPosts(newUser2)
        assertEquals(1, user2Posts.size())
    }

    Test public fun postsAreSortedByTimestamp() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user1)
        user3.subscribeTo(user2)

        user1.publishPost("First")
        user2.publishPost("Second")

        reload()

        val newUser3 = user3.reload()
        val user3Posts = newUser3.readHomePosts()
        assertEquals(2, user3Posts.size())
        assertEquals("Second", user3Posts[0].body)
        assertEquals("First", user3Posts[1].body)
    }
}

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
        val user1 = testFeeds.users.createUser("Alpha", private = true)
        val user2 = testFeeds.users.createUser("Beta")
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

    Ignore Test fun likedPostAppearsInSubscribersTimeline() {
    }

    Ignore Test fun likedPostIsVisibleInSubscribersTimelineAfterReload() {
    }

    Ignore Test fun subscribersTimelineShowsWhoLikedPost() {
    }

    Ignore Test fun likeBumpsPost() {

    }

    Ignore Test fun likesOfBlockedUsersArentShown() {

    }

}