package net.freefeed.kcabend.model

import net.freefeed.kcabend.persistence.PostData
import net.freefeed.kcabend.persistence.PostStore
import net.freefeed.kcabend.persistence.FeedData
import net.freefeed.kcabend.persistence.UserStore

data class PersistedUser(val id: Int, val data: FeedData)
data class PersistedUserPair(val fromUser: Int, val toUser: Int)

fun List<PersistedUserPair>.loadToUsers(fromUser: Int): List<Int> = filter { it.fromUser == fromUser }.map { it.toUser }

class TestUserStore : UserStore {
    public var disposed: Boolean = false
    private var lastId: Int = 1
    public val users: MutableMap<Int, PersistedUser> = hashMapOf()
    private val subscriptions = arrayListOf<PersistedUserPair>()
    private val blocks = arrayListOf<PersistedUserPair>()
    private val admins = arrayListOf<PersistedUserPair>()

    override fun createFeed(data: FeedData): Int {
        val user = PersistedUser(lastId++, data)
        users[user.id] = user
        return user.id
    }

    override fun loadFeed(id: Int): FeedData? = users[id]?.data

    override fun createSubscription(fromUserId: Int, toFeedId: Int) {
        subscriptions.add(PersistedUserPair(fromUserId, toFeedId))
    }

    override fun removeSubscription(fromUserId: Int, toFeedId: Int) {
        subscriptions.remove(PersistedUserPair(fromUserId, toFeedId))
    }

    override fun loadSubscriptions(id: Int) = subscriptions.loadToUsers(id)
    override fun loadSubscribers(id: Int) = subscriptions.filter { it.toUser == id }.map { it.fromUser}

    override fun createBlock(fromUserId: Int, toUserId: Int) {
        blocks.add(PersistedUserPair(fromUserId, toUserId))
    }

    override fun removeBlock(fromUserId: Int, toUserId: Int) {
        blocks.remove(PersistedUserPair(fromUserId, toUserId))
    }

    override fun loadBlocks(userId: Int) = blocks.loadToUsers(userId)

    override fun createAdmin(groupId: Int, adminId: Int) {
        admins.add(PersistedUserPair(groupId, adminId))
    }

    override fun removeAdmin(groupId: Int, adminId: Int) {
        admins.remove(PersistedUserPair(groupId, adminId))
    }

    override fun loadAdmins(groupId: Int): List<Int> = admins.loadToUsers(groupId)
}

data class PersistedPost(val id: Int, val data: PostData)

class IntMultiMap<T> {
    private val data = hashMapOf<Int, MutableList<T>>()

    public fun put(id: Int, value: T) {
        val list = data.getOrPut(id) { arrayListOf() }
        list.add(value)
    }

    public fun get(id: Int): List<T>? = data[id]

    public fun remove(id: Int, predicate: (T) -> Boolean) {
        val elementData = data[id] ?: return
        data[id] = elementData.filterNot(predicate).toArrayList()
    }

    public fun remove(id: Int, value: T) {
        val elementData = data[id] ?: return
        elementData.remove(value)
    }

    fun removeAll(id: Int) = data.remove(id)
}

data class PersistedLike(val postId: Int, val timestamp: Long)

class TestPostStore: PostStore {
    public var disposed: Boolean = false
    private var lastId: Int = 1
    public val userPosts: IntMultiMap<PersistedPost> = IntMultiMap()
    public val likes: IntMultiMap<Int> = IntMultiMap()
    private val userLikes = IntMultiMap<PersistedLike>()
    public val allPosts: MutableMap<Int, PersistedPost> = hashMapOf()

    override fun createPost(data: PostData): Int {
        val post = PersistedPost(lastId++, data)
        userPosts.put(data.author, post)
        allPosts[post.id] = post
        return post.id
    }

    override fun updatePost(id: Int, data: PostData) {
        val persistedPost = PersistedPost(id, data)
        userPosts.remove(data.author) { it.id == id }
        userPosts.put(data.author, persistedPost)
        allPosts[id] = persistedPost
    }

    override fun createLike(userId: Int, postId: Int, timestamp: Long) {
        likes.put(postId, userId)
        userLikes.put(userId, PersistedLike(postId, timestamp))
    }

    override fun removeLike(userId: Int, postId: Int) {
        likes.remove(postId, userId)
        userLikes.remove(userId) { it.postId == postId }
    }

    override fun deletePostWithLikes(postId: Int) {
        val post = allPosts.remove(postId) ?: throw IllegalStateException("Trying to delete a non-existing post")
        userPosts.remove(post.data.author) { it.id == postId }
        likes.removeAll(postId)
    }

    override fun loadUserPostIds(author: Int): List<Int> {
        return userPosts[author]?.map { it.id } ?: emptyList()
    }

    override fun loadPost(postId: Int): PostData? = allPosts[postId]?.data
    override fun loadLikes(postId: Int) = likes[postId] ?: emptyList()
    override fun loadUserLikesSortedByTimestamp(userId: Int): List<Int> =
            userLikes[userId]?.sortDescendingBy { it.timestamp }?.map { it.postId } ?: emptyList()
}

