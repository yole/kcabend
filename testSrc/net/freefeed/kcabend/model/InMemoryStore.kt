package net.freefeed.kcabend.model

import net.freefeed.kcabend.persistence.PostData
import net.freefeed.kcabend.persistence.PostStore
import net.freefeed.kcabend.persistence.UserData
import net.freefeed.kcabend.persistence.UserStore

data class PersistedUser(val id: Int, val data: UserData)
data class PersistedSubscription(val fromUser: Int, val toUser: Int)

class TestUserStore : UserStore {
    private var lastId: Int = 1
    public val users: MutableMap<Int, PersistedUser> = hashMapOf()
    private val subscriptions = arrayListOf<PersistedSubscription>()

    override fun createUser(data: UserData): Int {
        val user = PersistedUser(lastId++, data)
        users[user.id] = user
        return user.id
    }

    override fun loadUser(id: Int): UserData? = users[id]?.data

    override fun createSubscription(fromUserId: Int, toUserId: Int) {
        subscriptions.add(PersistedSubscription(fromUserId, toUserId))
    }

    override fun loadSubscriptions(id: Int) = subscriptions.filter { it.fromUser == id }.map { it.toUser }
    override fun loadSubscribers(id: Int) = subscriptions.filter { it.toUser == id }.map { it.fromUser}
}

data class PersistedPost(val id: Int, val data: PostData)

class IntMultiMap<T> {
    private val data = hashMapOf<Int, MutableList<T>>()

    public fun put(id: Int, value: T) {
        val list = data.getOrPut(id) { arrayListOf() }
        list.add(value)
    }

    public fun get(id: Int): List<T>? = data[id]
}

class TestPostStore: PostStore {
    private var lastId: Int = 1
    public val userPosts: IntMultiMap<PersistedPost> = IntMultiMap()
    public val likes: IntMultiMap<Int> = IntMultiMap()
    public val allPosts: MutableMap<Int, PersistedPost> = hashMapOf()

    override fun createPost(data: PostData): Int {
        val post = PersistedPost(lastId++, data)
        userPosts.put(data.author, post)
        allPosts[post.id] = post
        return post.id
    }

    override fun createLike(userId: Int, postId: Int) {
        likes.put(postId, userId)
    }

    override fun loadUserPostIds(author: Int): List<Int> {
        return userPosts[author]?.map { it.id } ?: emptyList()
    }

    override fun loadPost(postId: Int): PostData? = allPosts[postId]?.data
    override fun loadLikes(postId: Int) = likes[postId] ?: emptyList()
}

