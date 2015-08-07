package net.freefeed.kcabend.model

import net.freefeed.kcabend.persistence.PostData
import net.freefeed.kcabend.persistence.PostStore
import java.util.HashMap

public class Post(val id: Int, val data: PostData) {
    val authorId: Int get() = data.author
    val updatedAt: Long get() = data.updatedAt
    val likes = UserIdList()
}

enum class ShowReasonAction { Like }
data class ShowReason(val userId: Int, val action: ShowReasonAction)

public class PostView(val post: Post, val likes: UserIdList, val reason: ShowReason?) {
    val body: String get() = post.data.body
}

public class Posts(private val postStore: PostStore, private val feeds: Feeds) {
    private val allPosts = HashMap<Int, Post>()

    fun createPost(author: Int, toFeeds: IntArray, body: String): Post {
        val createdAt = feeds.currentTime()
        val postData = PostData(createdAt, createdAt, author, toFeeds, body)
        val postId = postStore.createPost(postData)
        val post = Post(postId, postData)
        allPosts[post.id] = post
        return post
    }

    fun updatePost(post: Post) {
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

    fun loadUserPostIds(author: User): List<Int> = postStore.loadUserPostIds(author.id)
    fun loadUserLikes(author: User): List<Int> = postStore.loadUserLikesSortedByTimestamp(author.id)

    fun getPost(id: Int, requestingUser: User?): Post? {
        var post = allPosts[id] ?: loadPost(id)
        return if (isPostVisible(post, requestingUser)) post else null
    }

    private fun loadPost(id: Int): Post {
        val data = postStore.loadPost(id) ?: throw NotFoundException("Post", id)
        val post = Post(id, data)
        post.likes.set(postStore.loadLikes(id))
        allPosts[id] = post
        return post
    }

    fun isPostVisible(post: Post, requestingUser: User?): Boolean {
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
    }

    fun canEditPost(post: Post, requestingUser: User): Boolean {
        return post.authorId == requestingUser.id
    }
}
