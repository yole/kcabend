package net.freefeed.kcabend.model

import net.freefeed.kcabend.persistence.PostData
import net.freefeed.kcabend.persistence.PostStore
import java.util.*

public class Post(val id: Int, val createdAt: Long, val authorId: Int, val toFeeds: IntArray, val body: String) {
    val likes = UserIdList()
}

public class Posts(private val postStore: PostStore, private val feeds: Feeds) {
    private val allPosts = HashMap<Int, Post>()

    fun createPost(author: Int, toFeeds: IntArray, body: String): Post {
        val createdAt = feeds.currentTime()
        val postId = postStore.createPost(PostData(createdAt, author, toFeeds, body))
        val post = Post(postId, createdAt, author, toFeeds, body)
        allPosts[post.id] = post
        return post
    }

    fun loadUserPostIds(author: User): List<Int> = postStore.loadUserPostIds(author.id)
    fun loadUserLikes(author: User): List<Int> = postStore.loadUserLikesSortedByTimestamp(author.id)

    fun getPost(id: Int, requestingUser: User?): Post? {
        var post = allPosts[id] ?: loadPost(id)
        return if (isPostVisible(post, requestingUser)) post else null
    }

    private fun loadPost(id: Int): Post {
        val data = postStore.loadPost(id) ?: throw NotFoundException("Post", id)
        val post = Post(id, data.createdAt, data.author, data.toFeeds, data.body)
        post.likes.set(postStore.loadLikes(id))
        allPosts[id] = post
        return post
    }

    fun isPostVisible(post: Post, requestingUser: User?): Boolean {
        val toFeeds = post.toFeeds.map { feeds.users.get(it) }
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
}
