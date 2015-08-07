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

    fun add(id: Int) = ids.add(id)
    fun remove(id: Int) = ids.remove(id)

    fun set(newIds: List<Int>) {
        ids.clear()
        ids.addAll(newIds)
    }

    fun contains(id: Int): Boolean = id in ids
    fun asSequence() = ids.asSequence()
    fun size() = ids.size()
}

public open class Feed(val feeds: Feeds,
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
    val ownPosts: Timeline by lazy { PostsTimeline(feeds, this) }
    val homeFeed: RiverOfNewsTimeline by lazy { RiverOfNewsTimeline(feeds, this) }
    val likesTimeline: Timeline by lazy { LikesTimeline(feeds, this) }

    fun subscribeTo(targetFeed: Feed) {
        if (targetFeed.id in subscriptions) return
        feeds.users.createSubscription(this, targetFeed)
        subscriptions.add(targetFeed.id)
        targetFeed.subscribers.add(id)
        homeFeed.rebuild()
    }

    fun unsubscribeFrom(targetFeed: Feed) {
        if (targetFeed.id !in subscriptions) return
        feeds.users.removeSubscription(this, targetFeed)
        subscriptions.remove(targetFeed.id)
        targetFeed.subscribers.remove(id)
        homeFeed.rebuild()
    }

    fun publishPost(body: String): Post {
        val post = feeds.posts.createPost(id, intArrayOf(id), body)
        ownPosts.addPost(post)
        propagateToSubscribers { it.homeFeed.addPost(post) }
        return post
    }

    fun deletePost(post: Post) {
        feeds.posts.deletePost(post, this)
        ownPosts.removePost(post)
        propagateToThoseWhoSeePost(post) { it.homeFeed.removePost(post) }
    }

    fun likePost(post: Post) {
        feeds.posts.createLike(this, post)
        post.likes.add(id)
        feeds.posts.updatePost(post)
        likesTimeline.addPost(post)
        propagateToSubscribers { it.homeFeed.addPost(post, ShowReason(id, ShowReasonAction.Like)) }
        bumpPostInAllTimelines(post)
    }

    private fun propagateToSubscribers(callback: (User) -> Unit) {
        subscribers.asSequence().map { feeds.users[it] }.forEach { callback(it) }
    }

    private fun propagateToThoseWhoSeePost(post: Post, callback: (User) -> Unit) {
        val author = feeds.users[post.authorId]
        val likers = feeds.users.getAll(post.likes)
        val allSeeds = setOf(author) + likers.toSet()
        val allRecipientIds = allSeeds.flatMapTo(hashSetOf()) { it.subscribers.ids }
        val allRecipients = feeds.users.getAll(allRecipientIds)
        allRecipients.forEach(callback)
    }

    private fun bumpPostInAllTimelines(post: Post) {
        propagateToThoseWhoSeePost(post) {
            if (id in it.subscriptions) {
                it.homeFeed.bumpPost(post)
            }
        }
    }
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

    fun getAll(userIdList: UserIdList): List<User> = userIdList.ids.map { get(it) }
    fun getAll(userIdList: Collection<Int>): List<User> = userIdList.map { get(it) }

    fun createUser(name: String, private: Boolean = false): User {
        val userId = userStore.createUser(UserData(name, name, "", private))
        val user = User(feeds, userId, name, name, "", private)
        allUsers[user.id] = user
        return user
    }
    fun createSubscription(fromUser: User, toUser: Feed) = userStore.createSubscription(fromUser.id, toUser.id)
    fun removeSubscription(fromUser: User, toUser: Feed) = userStore.removeSubscription(fromUser.id, toUser.id)
}
