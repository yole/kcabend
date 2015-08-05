package net.freefeed.kcabend.model

import net.freefeed.kcabend.persistence.PostStore
import net.freefeed.kcabend.persistence.UserData
import net.freefeed.kcabend.persistence.UserStore
import java.util.TreeMap
import java.util.TreeSet

class Feeds(userStore: UserStore, postStore: PostStore) {
    val posts = Posts(postStore, this)
    val users = Users(userStore, this)

}

public class UserIdList {
    val ids = TreeSet<Int>()

    fun add(id: Int) {
        ids.add(id)
    }

    fun contains(id: Int): Boolean = id in ids

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
    val posts = PostsTimeline(feeds, this)
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

public open class Timeline(val feeds: Feeds) {
    val postIds = arrayListOf<Int>()

    fun getPosts(requestingUser: User?): List<Post> {
        return postIds.map { feeds.posts.getPost(it, requestingUser) }.filterNotNull()
    }

    fun addPost(post: Post) {
        postIds.add(0, post.id)
    }
}

public class PostsTimeline(feeds: Feeds, val owner: User) : Timeline(feeds) {
    init {
        postIds.addAll(feeds.posts.loadUserPostIds(owner))
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

    fun createUser(name: String, private: Boolean = false): User {
        val userId = userStore.createUser(UserData(name, name, "", private))
        val user = User(feeds, userId, name, name, "", private)
        allUsers[user.id] = user
        return user
    }
}

