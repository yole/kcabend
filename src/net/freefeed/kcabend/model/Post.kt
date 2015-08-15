package net.freefeed.kcabend.model

import net.freefeed.kcabend.persistence.CommentData
import net.freefeed.kcabend.persistence.PostData
import net.freefeed.kcabend.persistence.PostStore
import java.util.HashMap

public data class Comment(val id: Int, val data: CommentData) {
    val author: Int get() = data.author
}

public class Post(val id: Int, val data: PostData) {
    val authorId: Int get() = data.author
    val updatedAt: Long get() = data.updatedAt
    val body: String get() = data.body

    // Likes sorted by timestamp (newest first)
    val likes = arrayListOf<Int>()
    val comments = arrayListOf<Comment>()
}

enum class ShowReasonAction { Subscription, Like, Comment }
data class ShowReason(val userId: Int, val action: ShowReasonAction)

public class PostView(val post: Post,
                      val likes: List<User>,
                      val comments: List<Comment>,
                      val reason: ShowReason?,
                      val omittedLikes: Int)
    : IdObject(post.id) {

    val body: String get() = post.body
    val authorId: Int get() = post.authorId
}

public class Posts(private val postStore: PostStore, private val feeds: Feeds) {
    private val allPosts = HashMap<Int, Post>()

    fun createPost(author: User, toFeedIds: IntArray, body: String): Post {
        val toFeeds = feeds.users.getAll(toFeedIds.asList())

        if (isDirect(author.id, toFeeds)) {
            if (toFeeds.any { !isMutualSubscription(author, it as User) }) {
                throw ForbiddenException()
            }
        }
        else {
            if (toFeeds.any { it != author && !it.isSubscribedGroup(author) }) {
                throw ForbiddenException()
            }
        }

        val createdAt = feeds.currentTime()
        val postData = PostData(createdAt, createdAt, author.id, toFeedIds, body)
        val postId = postStore.createPost(postData)
        val post = Post(postId, postData)
        allPosts[post.id] = post
        return post
    }

    private fun markPostUpdated(post: Post) {
        post.data.updatedAt = feeds.currentTime()
        postStore.updatePost(post.id, post.data)
    }

    fun deletePost(post: Post, requestingUser: User) {
        if (!canEditPost(post, requestingUser)) {
            throw ForbiddenException()
        }
        postStore.deletePostWithLikes(post.id)
        allPosts.remove(post.id)
    }

    fun loadUserPostIds(author: Feed): List<Int> = postStore.loadUserPostIds(author.id)
    fun loadUserLikes(author: User): List<Int> = postStore.loadUserLikesSortedByTimestamp(author.id)
    fun loadUserCommentedPosts(author: User): List<Int> = postStore.loadUserCommentedPosts(author.id)

    fun getPost(id: Int, requestingUser: User?): Post? {
        var post = allPosts[id] ?: loadPost(id)
        return if (isPostVisible(post, requestingUser)) post else null
    }

    fun createView(post: Post, requestingUser: User?, showReason: ShowReason? = null, maxLikes: Int = 4): PostView {
        val (filteredLikes, omittedLikes) = filterLikes(post.likes, requestingUser, maxLikes)
        return PostView(post,
                filteredLikes,
                filterComments(post.comments, requestingUser),
                showReason,
                omittedLikes)
    }

    private fun filterLikes(likes: List<Int>, requestingUser: User?, maxLikes: Int): Pair<List<User>, Int> {
        val likers = feeds.users.getAllUsers(likes)
        val filteredLikes: List<User>
        if (requestingUser == null) {
            filteredLikes = likers
        } else {
            filteredLikes = likers.filter { !it.isContentBlocked(requestingUser) }.toArrayList()

            val requestingUserIndex = filteredLikes.indexOf(requestingUser)
            if (requestingUserIndex > 0) {
                filteredLikes.remove(requestingUserIndex)
                filteredLikes.add(0, requestingUser)
            }
        }

        if (filteredLikes.size() <= maxLikes) {
            return filteredLikes to 0
        }
        return filteredLikes.take(maxLikes) to (filteredLikes.size() - maxLikes)
    }

    private fun filterComments(comments: List<Comment>, requestingUser: User?): List<Comment> {
        if (requestingUser == null) {
            return comments
        }

        return comments.filter { !feeds.users.getUser(it.author).isContentBlocked(requestingUser) }
    }

    private fun loadPost(id: Int): Post {
        val data = postStore.loadPost(id) ?: throw NotFoundException("Post", id)
        val post = Post(id, data)
        post.likes.addAll(postStore.loadLikesSortedByTimestamp(id))
        post.comments.addAll(postStore.loadComments(id).map { Comment(it.first, it.second) })
        allPosts[id] = post
        return post
    }

    public fun isDirect(post: Post): Boolean {
        val toFeeds = feeds.users.getAll(post.data.toFeeds.asList())
        return isDirect(post.authorId, toFeeds)
    }

    public fun isDirect(authorId: Int, toFeeds: List<Feed>): Boolean {
        return toFeeds.all { it is User && it.id != authorId }
    }

    fun isPostVisible(post: Post, requestingUser: User?): Boolean {
        val author = feeds.users.getUser(post.authorId)

        if (isDirect(post)) {
            return requestingUser != null && (requestingUser.id == author.id || requestingUser.id in post.data.toFeeds)
        }

        if (requestingUser != null && author.isContentBlocked(requestingUser)) {
            return false
        }

        val toFeeds = post.data.toFeeds.map { feeds.users.get(it) }
        return toFeeds.any { isFeedVisible(it, requestingUser) }
    }

    fun isFeedVisible(feed: Feed, requestingUser: User?): Boolean {
        if (feed.private) {
            return requestingUser != null && requestingUser.id in feed.subscribers
        }
        return true
    }

    fun createLike(user: User, post: Post) {
        if (!isPostVisible(post, user)) {
            throw ForbiddenException()
        }
        postStore.createLike(user.id, post.id, feeds.currentTime())
        markPostUpdated(post)
    }

    fun removeLike(user: User, post: Post) {
        if (!isPostVisible(post, user)) {
            throw ForbiddenException()
        }
        postStore.removeLike(user.id, post.id)
    }

    fun createComment(user: User, post: Post, text: String): Comment {
        if (!isPostVisible(post, user)) {
            throw ForbiddenException()
        }
        val commentData = CommentData(post.id, feeds.currentTime(), user.id, text)
        val id = postStore.createComment(commentData)
        markPostUpdated(post)
        return Comment(id, commentData)
    }

    fun deleteComment(user: User, comment: Comment): Post {
        val post = getPost(comment.data.postId, user) ?: throw ForbiddenException()
        if (user.id != comment.data.author && !canEditPost(post, user)) {
            throw ForbiddenException()
        }
        postStore.deleteComment(comment.id)
        return post
    }

    fun canEditPost(post: Post, requestingUser: User): Boolean {
        if (post.authorId == requestingUser.id) return true

        val toFeeds = feeds.users.getAll(post.data.toFeeds)
        if (toFeeds.any { it is Group && it.admins.contains(requestingUser.id) }) {
            return true
        }

        return false
    }

    private fun isMutualSubscription(user1: User, user2: User): Boolean {
        return user1.id in user2.subscriptions && user2.id in user1.subscriptions
    }

    private fun Feed.isSubscribedGroup(subscriber: User) =
        this is Group && subscriber.id in subscribers
}
