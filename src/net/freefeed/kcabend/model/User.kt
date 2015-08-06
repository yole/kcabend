package net.freefeed.kcabend.model

import net.freefeed.kcabend.persistence.PostStore
import net.freefeed.kcabend.persistence.UserData
import net.freefeed.kcabend.persistence.UserStore
import java.util.TreeMap
import java.util.TreeSet

class Feeds(userStore:
            UserStore, postStore: PostStore,
            val currentTime: () -> Long = { System.currentTimeMillis() }) {
    val posts = Posts(postStore, this)
    val users = Users(userStore, this)

}

public class UserIdList {
    val ids = TreeSet<Int>()

    fun add(id: Int) {
        ids.add(id)
    }

    fun set(newIds: List<Int>) {
        ids.clear()
        ids.addAll(newIds)
    }

    fun contains(id: Int): Boolean = id in ids
    fun asSequence() = ids.asSequence()
    fun size() = ids.size()
}

public open class Feed(protected val feeds: Feeds,
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
    val posts: Timeline by lazy { PostsTimeline(feeds, this) }
    val homeFeed: RiverOfNewsTimeline by lazy { RiverOfNewsTimeline(feeds, this) }
    val likesTimeline: Timeline by lazy { LikesTimeline(feeds, this) }

    fun subscribeTo(targetUser: User) {
        if (targetUser.id in subscriptions) return
        feeds.users.createSubscription(this, targetUser)
        subscriptions.add(targetUser.id)
        targetUser.subscribers.add(id)
    }

    fun publishPost(body: String): Post {
        val post = feeds.posts.createPost(id, intArrayOf(id), body)
        posts.addPost(post)
        propagateToSubscribers(post)
        return post
    }

    fun likePost(post: Post) {
        feeds.posts.createLike(this, post)
        post.likes.add(id)
        likesTimeline.addPost(post)
        propagateToSubscribers(post, ShowReason(id, ShowReasonAction.Like))
    }

    private fun propagateToSubscribers(post: Post, reason: ShowReason? = null) {
        subscribers.asSequence().map { feeds.users[it].homeFeed }.forEach { it.addPost(post, reason) }
    }

    fun allPostIdsForSubscribers(): Set<Int> =
        posts.postIds.toHashSet() + likesTimeline.postIds.toHashSet()
}

public class NotFoundException(val type: String, val id: Int) : Exception("Can't find $type with ID $id")
public class ForbiddenException() : Exception("This operation is forbidden")

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
            loadedUser.subscriptions.set(userStore.loadSubscriptions(id))
            loadedUser.subscribers.set(userStore.loadSubscribers(id))
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

    fun createSubscription(fromUser: User, toUser: User) = userStore.createSubscription(fromUser.id, toUser.id)
}

