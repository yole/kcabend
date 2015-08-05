package net.freefeed.kcabend.model

import net.freefeed.kcabend.persistence.PostStore
import net.freefeed.kcabend.persistence.UserData
import net.freefeed.kcabend.persistence.UserStore
import java.util.*

class Feeds(userStore: UserStore, postStore: PostStore) {
    val posts = Posts(postStore)
    val users = Users(userStore, this)

}

public class UserIdList {
    val ids = TreeSet<Int>()

    fun add(id: Int) {
        ids.add(id)
    }

}

public open class Feed(val freefeed: Feeds,
                       val id: Int,
                       val userName: String,
                       val screenName: String,
                       val profile: String,
                       val private: Boolean) {

    val subscribers = UserIdList()


}

public class User(feeds: Feeds, id: Int, userName: String, screenName: String, profile: String, private: Boolean)
    : Feed(feeds, id, userName, screenName, profile, private)
{
    val subscriptions = UserIdList()
    val posts = Timeline(feeds)
    val homeFeed = Timeline(feeds)

    fun subscribeTo(targetUser: User) {
        subscriptions.add(targetUser.id)
        targetUser.subscribers.add(id)
    }

    fun publishPost(body: String) {
        val post = freefeed.posts.createPost(id, intArrayOf(id), body)
        posts.addPost(post)
    }
}

public class Post(val id: Int, val authorId: Int, val toFeeds: IntArray, val body: String)

public class Timeline(val Feeds: Feeds) {
    val postIds = arrayListOf<Int>()

    fun getPosts(requestingUser: User?): List<Post> {
        return postIds.map { Feeds.posts.getPost(it, requestingUser) }.filterNotNull()
    }

    fun addPost(post: Post) {
        postIds.add(0, post.id)
    }
}

public class NotFoundException(val type: String, val id: Int) : Exception("Can't find $type with ID $id")

public class Users(private val userStore: UserStore, val feeds: Feeds) {
    private var allUsers = TreeMap<Int, User>()

    fun get(id: Int): User {
        val user = allUsers[id]
        if (user != null) {
            return user
        }
        val data = userStore.loadUser(id)
        if (data != null) {
            val loadedUser = User(feeds, id, data.userName, data.screenName, data.profile, data.private)
            allUsers[id] = loadedUser
            return loadedUser
        }
        throw NotFoundException("User", id)
    }

    fun createUser(name: String): User {
        val userId = userStore.createUser(UserData(name, name, "", false))
        val user = User(feeds, userId, name, name, "", false)
        allUsers[user.id] = user
        return user
    }
}

public class Posts(private val store: PostStore) {
    private val allPosts = TreeMap<Int, Post>()

    fun createPost(author: Int, toFeeds: IntArray, body: String): Post {
        val postId = store.createPost(author, toFeeds, body)
        val post = Post(postId, author, toFeeds, body)
        allPosts[post.id] = post
        return post
    }

    fun getPost(id: Int, requestingUser: User?): Post? {
        return allPosts[id]
    }
}
