package net.freefeed.kcabend.model

import net.freefeed.kcabend.persistence.PostData
import net.freefeed.kcabend.persistence.PostStore
import net.freefeed.kcabend.persistence.UserData
import net.freefeed.kcabend.persistence.UserStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.properties.Delegates

data class PersistedUser(val id: Int, val data: UserData)

class TestUserStore : UserStore {
    private var lastId: Int = 1
    public val users: MutableMap<Int, PersistedUser> = hashMapOf()

    override fun createUser(data: UserData): Int {
        val user = PersistedUser(lastId++, data)
        users[user.id] = user
        return user.id
    }

    override fun loadUser(id: Int): UserData? = users[id]?.data
}

data class PersistedPost(val id: Int, val data: PostData)

class TestPostStore: PostStore {
    private var lastId: Int = 1
    public val userPosts: MutableMap<Int, MutableList<PersistedPost>> = hashMapOf()
    public val allPosts: MutableMap<Int, PersistedPost> = hashMapOf()

    override fun createPost(data: PostData): Int {
        val posts = userPosts.getOrPut(data.author) { arrayListOf() }
        val post = PersistedPost(lastId++, data)
        posts.add(post)
        allPosts[post.id] = post
        return post.id
    }

    override fun loadUserPostIds(author: Int): List<Int> {
        return userPosts[author]?.map { it.id } ?: emptyList()
    }

    override fun loadPost(id: Int): PostData? = allPosts[id]?.data
}

public abstract class AbstractModelTest {
    var testUserStore: TestUserStore by Delegates.notNull()
    var testPostStore: TestPostStore by Delegates.notNull()
    var testFeeds: Feeds by Delegates.notNull()

    Before fun setUp() {
        testUserStore = TestUserStore()
        testPostStore = TestPostStore()
        testFeeds = Feeds(testUserStore, testPostStore)
    }
}

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
        val postId = testPostStore.createPost(PostData(userId, intArrayOf(userId), "Hello World"))

        val user = testFeeds.users[userId]
        val userPosts = user.posts.getPosts(null)
        assertEquals("Hello World", userPosts[0].body)
    }

    Ignore Test public fun testSimple() {
        val user1 = testFeeds.users.createUser("Alpha")
        val user2 = testFeeds.users.createUser("Beta")
        user2.subscribeTo(user1)

        user1.publishPost("Hello World")
        val user2Posts = user2.homeFeed.getPosts(user2)
        assertEquals(1, user2Posts.size())
        assertEquals("Hello World", user2Posts [0].body)
    }
}