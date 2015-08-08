package net.freefeed.kcabend.model

import net.freefeed.kcabend.persistence.PostData
import net.freefeed.kcabend.persistence.FeedData
import net.freefeed.kcabend.persistence.FeedType
import org.junit.Assert.assertEquals
import org.junit.Before
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
        testUserStore.disposed = true
        testPostStore.disposed = true
        testFeeds = Feeds(testUserStore, testPostStore, { currentTime++ })
    }

    @suppress("UNCHECKED_CAST")
    protected fun <T: Feed> T.reload(): T = testFeeds.users[id] as T

    fun createUsers(vararg names: String): List<User> = names.map {
        val user = testFeeds.users.createUser(it)
        ensureLoaded(user.homeFeed)
        ensureLoaded(user.ownPosts)
        user
    }

    fun User.publishPost(body: String, vararg toFeeds: Feed) = publishPost(body, toFeeds.map { it.id }.toIntArray())
    fun User.readHomePosts(): List<PostView> = homeFeed.getPosts(this)
    fun User.readOwnPosts(): List<PostView> = ownPosts.getPosts(this)

    fun User.verifyHomePosts(vararg postBodies: String) {
        val posts = readHomePosts()
        assertEquals(postBodies.size(), posts.size())
        for ((post, expectedBody) in posts zip postBodies) {
            assertEquals(expectedBody, post.body)
        }
    }

    // Do nothing. The very fact that a timeline is passed here as a parameter ensures that it is loaded.
    fun ensureLoaded(timeline: Timeline) {}
}

public class PostTest : AbstractModelTest() {
    Test public fun testPostAppearsInUsersTimeline() {
        val user1 = testFeeds.users.createUser("Alpha")
        user1.publishPost("Hello World")

        val user1Posts = user1.ownPosts.getPosts(null)
        assertEquals(1, user1Posts.size())
        assertEquals("Hello World", user1Posts[0].body)
    }

    Test public fun testPostIsPersisted() {
        val user1 = testFeeds.users.createUser("Alpha")
        user1.publishPost("Hello World")
        assertEquals(1, testPostStore.userPosts[user1.id]!!.size())
    }

    Test public fun testPostsAreLoaded() {
        val userId = testUserStore.createFeed(FeedData(FeedType.User, "alpha", "Alpha", "alpha", false))
        val createdAt = testFeeds.currentTime()
        val postId = testPostStore.createPost(PostData(createdAt, createdAt, userId, intArrayOf(userId), "Hello World"))

        val user = testFeeds.users[userId]
        val userPosts = user.ownPosts.getPosts(null)
        assertEquals("Hello World", userPosts[0].body)
    }

    Test public fun testPostsOfPrivateUserNotShown() {
        val user1 = testFeeds.users.createUser("Alpha", private = true)
        user1.publishPost("Hello World")

        val user1Posts = user1.ownPosts.getPosts(null)
        assertEquals(0, user1Posts.size())
    }

    Test public fun testPostsOfPrivateUserShownToTheirSubscribers() {
        val user1 = testFeeds.users.createUser("Alpha", private = true)
        val user2 = testFeeds.users.createUser("Beta")
        user2.subscribeTo(user1)
        user1.publishPost("Hello World")

        val user1Posts = user1.ownPosts.getPosts(user2)
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

    Test public fun postAppearsInRiverOfNewsAfterSubscribe() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        user1.publishPost("First")

        user2.subscribeTo(user1)
        user2.verifyHomePosts("First")

        user2.unsubscribeFrom(user1)
        user2.verifyHomePosts()
    }

    Test public fun deletedPostDisappearsFromTimelines() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        user2.subscribeTo(user1)
        val post = user1.publishPost("Foo")
        user1.deletePost(post)
        user2.verifyHomePosts()
    }

    Test(expected = ForbiddenException::class) public fun cantDeleteOtherUsersPost() {
        val (user1, user2) = createUsers("Alpha", "Beta")
        val post = user1.publishPost("Foo")
        user2.deletePost(post)
    }

    Test public fun postsOfBlockedUserAreNotShown() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        user3.blockUser(user1)
        val post = user1.publishPost("Hello World")
        user2.likePost(post)

        val likesSeenByUser3 = user2.likesTimeline.getPosts(user3)
        assertEquals(0, likesSeenByUser3.size())
    }

    Test public fun postsOfBlockingUserAreNotShown() {
        val (user1, user2, user3) = createUsers("Alpha", "Beta", "Gamma")
        user3.subscribeTo(user2)
        user1.blockUser(user3)
        val post = user1.publishPost("Hello World")
        user2.likePost(post)

        val likesSeenByUser3 = user2.likesTimeline.getPosts(user3)
        assertEquals(0, likesSeenByUser3.size())
    }
}
