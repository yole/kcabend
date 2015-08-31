package net.freefeed.kcabend.persistence

data class PersistedUser(val id: Int, val data: FeedData)
data class PersistedUserPair(val fromUser: Int, val toUser: Int)

fun List<PersistedUserPair>.loadToUsers(fromUser: Int): List<Int> = filter { it.fromUser == fromUser }.map { it.toUser }
fun List<PersistedUserPair>.loadFromUsers(toUser: Int): List<Int> = filter { it.toUser == toUser }.map { it.fromUser }

class TestUserStore : UserStore {
    public var disposed: Boolean = false
    private var lastId: Int = 1
    public val users: MutableMap<Int, PersistedUser> = hashMapOf()
    private val usersByName = hashMapOf<String, Int>()
    private val subscriptions = arrayListOf<PersistedUserPair>()
    private val blocks = arrayListOf<PersistedUserPair>()
    private val admins = arrayListOf<PersistedUserPair>()
    private val subscriptionRequests = arrayListOf<PersistedUserPair>()

    override fun createFeed(data: FeedData): Int {
        val user = PersistedUser(lastId++, data)
        users[user.id] = user
        usersByName[data.userName] = user.id
        return user.id
    }

    override fun loadFeed(id: Int): FeedData? = users[id]?.data

    override fun lookupUserName(userName: String): Int?  = usersByName[userName]

    override fun createSubscription(fromUserId: Int, toFeedId: Int) {
        subscriptions.add(PersistedUserPair(fromUserId, toFeedId))
    }

    override fun removeSubscription(fromUserId: Int, toFeedId: Int) {
        subscriptions.remove(PersistedUserPair(fromUserId, toFeedId))
    }

    override fun loadSubscriptions(userId: Int) = subscriptions.loadToUsers(userId)
    override fun loadSubscribers(feedId: Int) = subscriptions.filter { it.toUser == feedId }.map { it.fromUser}

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

    override fun createSubscriptionRequest(fromUserId: Int, toUserId: Int) {
        subscriptionRequests.add(PersistedUserPair(fromUserId, toUserId))
    }

    override fun removeSubscriptionRequest(fromUserId: Int, toUserId: Int) {
        subscriptionRequests.remove(PersistedUserPair(fromUserId, toUserId))
    }

    override fun loadSubscriptionRequests(targetUser: Int): List<Int> {
        return subscriptionRequests.loadFromUsers(targetUser)
    }
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

data class PersistedLike(val userId: Int, val postId: Int, val timestamp: Long)

class TestPostStore: PostStore {
    public var disposed: Boolean = false
    private var lastId: Int = 1
    public val userPosts: IntMultiMap<PersistedPost> = IntMultiMap()
    public val likes: IntMultiMap<PersistedLike> = IntMultiMap()
    private val userLikes = IntMultiMap<PersistedLike>()
    public val allPosts: MutableMap<Int, PersistedPost> = hashMapOf()
    public val allComments: MutableMap<Int, CommentData> = hashMapOf()
    val postComments = IntMultiMap<Pair<Int, CommentData>>()

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
        val persistedLike = PersistedLike(userId, postId, timestamp)
        likes.put(postId, persistedLike)
        userLikes.put(userId, persistedLike)
    }

    override fun removeLike(userId: Int, postId: Int) {
        likes.remove(postId) { it.userId == userId }
        userLikes.remove(userId) { it.postId == postId }
    }

    override fun deletePostWithLikes(postId: Int) {
        val post = allPosts.remove(postId) ?: throw IllegalStateException("Trying to delete a non-existing post")
        userPosts.remove(post.data.author) { it.id == postId }
        likes.removeAll(postId)
    }

    override fun createComment(commentData: CommentData): Int {
        val commentId = lastId++
        allComments.put(commentId, commentData)
        postComments.put(commentData.postId, commentId to commentData)
        return commentId
    }

    override fun deleteComment(commentId: Int) {
        val comment = allComments[commentId] ?: throw IllegalStateException("Trying to delete a non-existing comment")
        allComments.remove(commentId)
        postComments.remove(comment.postId) { it.first == commentId }
    }

    override fun loadComments(postId: Int) = postComments.get(postId) ?: emptyList()

    override fun loadUserPostIds(author: Int): List<Int> {
        return userPosts[author]?.map { it.id } ?: emptyList()
    }

    override fun loadPost(postId: Int): PostData? = allPosts[postId]?.data

    override fun loadLikesSortedByTimestamp(postId: Int) =
            likes[postId]?.sortDescendingBy { it.timestamp }?.map { it.userId } ?: emptyList()

    override fun loadUserLikesSortedByTimestamp(userId: Int): List<Int> =
            userLikes[userId]?.sortDescendingBy { it.timestamp }?.map { it.postId } ?: emptyList()

    override fun loadUserCommentedPosts(userId: Int): List<Int> {
        return allComments.values()
                .filter { it.author == userId }
                .groupBy { it.postId }
                .map { it.key to it.value.maxBy { it.createdAt } }
                .sortDescendingBy { it.second!!.createdAt }
                .map { it.first }
    }
}
