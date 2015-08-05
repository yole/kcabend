package net.freefeed.kcabend.model

import net.freefeed.kcabend.persistence.PostData
import net.freefeed.kcabend.persistence.PostStore
import java.util.*

public class Post(val id: Int, val authorId: Int, val toFeeds: IntArray, val body: String)

public class Posts(private val postStore: PostStore, private val feeds: Feeds) {
    private val allPosts = HashMap<Int, Post>()

    fun createPost(author: Int, toFeeds: IntArray, body: String): Post {
        val postId = postStore.createPost(PostData(author, toFeeds, body))
        val post = Post(postId, author, toFeeds, body)
        allPosts[post.id] = post
        return post
    }

    fun loadUserPostIds(author: User): List<Int> = postStore.loadUserPostIds(author.id)

    fun getPost(id: Int, requestingUser: User?): Post? {
        var post = allPosts[id]
        if (post == null) {
            val data = postStore.loadPost(id) ?: throw NotFoundException("Post", id)
            post = Post(id, data.author, data.toFeeds, data.body)
            allPosts[id] = post
        }
        return if (isPostVisible(post, requestingUser)) post else null
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
}
