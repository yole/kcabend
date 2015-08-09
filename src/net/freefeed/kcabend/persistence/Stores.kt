package net.freefeed.kcabend.persistence

enum class FeedType { User, Group }
data class FeedData(val feedType: FeedType,
                    val userName: String,
                    val encryptedPassword: String?,
                    val screenName: String,
                    val profile: String,
                    val private: Boolean)

interface UserStore {
    fun createFeed(data: FeedData): Int
    fun loadFeed(id: Int): FeedData?
    fun lookupUserName(userName: String): Int?

    fun createSubscription(fromUserId: Int, toFeedId: Int)
    fun removeSubscription(fromUserId: Int, toFeedId: Int)
    fun loadSubscriptions(id: Int): List<Int>
    fun loadSubscribers(id: Int): List<Int>

    fun createBlock(fromUserId: Int, toUserId: Int)
    fun removeBlock(fromUserId: Int, toUserId: Int)
    fun loadBlocks(userId: Int): List<Int>

    fun createAdmin(groupId: Int, adminId: Int)
    fun removeAdmin(groupId: Int, adminId: Int)
    fun loadAdmins(groupId: Int): List<Int>
}

data class PostData(val createdAt: Long, var updatedAt: Long, val author: Int, val toFeeds: IntArray, val body: String)
data class CommentData(val postId: Int, val createdAt: Long, val author: Int, val body: String)

interface PostStore {
    fun createPost(data: PostData): Int
    fun updatePost(id: Int, data: PostData)
    fun createLike(userId: Int, postId: Int, timestamp: Long)
    fun removeLike(userId: Int, postId: Int)
    fun deletePostWithLikes(postId: Int)

    fun createComment(commentData: CommentData): Int
    fun deleteComment(commentId: Int)

    fun loadUserPostIds(author: Int): List<Int>
    fun loadPost(postId: Int): PostData?
    fun loadLikes(postId: Int): List<Int>
    fun loadComments(postId: Int): List<Pair<Int, CommentData>>
    fun loadUserLikesSortedByTimestamp(userId: Int): List<Int>
    fun loadUserCommentedPosts(userId: Int): List<Int>
}
