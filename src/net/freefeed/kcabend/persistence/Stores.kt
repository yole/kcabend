package net.freefeed.kcabend.persistence

data class UserData(val userName: String, val screenName: String, val profile: String, val private: Boolean)

interface UserStore {
    fun createUser(data: UserData): Int
    fun createSubscription(fromUserId: Int, toUserId: Int)
    fun removeSubscription(fromUserId: Int, toUserId: Int)

    fun loadUser(id: Int): UserData?
    fun loadSubscriptions(id: Int): List<Int>
    fun loadSubscribers(id: Int): List<Int>
}

data class PostData(val createdAt: Long, var updatedAt: Long, val author: Int, val toFeeds: IntArray, val body: String)

interface PostStore {
    fun createPost(data: PostData): Int
    fun updatePost(id: Int, data: PostData)
    fun createLike(userId: Int, postId: Int, timestamp: Long)
    fun removeLike(userId: Int, postId: Int)
    fun deletePostWithLikes(postId: Int)

    fun loadUserPostIds(author: Int): List<Int>
    fun loadPost(postId: Int): PostData?
    fun loadLikes(postId: Int): List<Int>
    fun loadUserLikesSortedByTimestamp(userId: Int): List<Int>
}
