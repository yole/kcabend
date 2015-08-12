package net.freefeed.kcabend.model

import net.freefeed.kcabend.persistence.PostStore
import net.freefeed.kcabend.persistence.FeedData
import net.freefeed.kcabend.persistence.FeedType
import net.freefeed.kcabend.persistence.UserStore
import java.util.TreeMap
import java.util.TreeSet

class Feeds(userStore: UserStore,
            postStore: PostStore,
            val currentTime: () -> Long = { System.currentTimeMillis() }) {
    val posts = Posts(postStore, this)
    val users = Users(userStore, this)

}

open class IdObject(public val id: Int)

public class UserIdList() {
    val ids = TreeSet<Int>()

    constructor(idList: Collection<Int>): this() {
        ids.addAll(idList)
    }

    fun add(id: Int) = ids.add(id)
    fun remove(id: Int) = ids.remove(id)

    fun set(newIds: List<Int>) {
        ids.clear()
        ids.addAll(newIds)
    }

    fun contains(id: Int): Boolean = id in ids
    fun size() = ids.size()
}

public open class Feed(val feeds: Feeds,
                       id: Int,
                       val userName: String,
                       val screenName: String,
                       val profile: String,
                       val private: Boolean) : IdObject(id) {

    val subscribers = UserIdList()

    val ownPosts: Timeline by lazy { PostsTimeline(feeds, this) }

}

public class User(feeds: Feeds, id: Int, userName: String, val hashedPassword: String?,
                  screenName: String, profile: String, private: Boolean)
    : Feed(feeds, id, userName, screenName, profile, private)
{
    val subscriptions = UserIdList()
    val blockedUsers = UserIdList()

    val homeFeed: RiverOfNewsTimeline by lazy { RiverOfNewsTimeline(feeds, this) }
    val likesTimeline: Timeline by lazy { LikesTimeline(feeds, this) }
    val commentsTimeline: Timeline by lazy { CommentsTimeline(feeds, this) }
    val directMessagesTimeline: TimelineView = DirectMessagesTimeline(feeds, this)

    fun subscribeTo(targetFeed: Feed) {
        if (targetFeed.id in blockedUsers || (targetFeed is User && id in targetFeed.blockedUsers)) {
            throw ForbiddenException()
        }
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

    fun blockUser(targetUser: User) {
        if (targetUser.id in blockedUsers) return

        unsubscribeFrom(targetUser)
        targetUser.unsubscribeFrom(this)

        blockedUsers.add(targetUser.id)

        feeds.users.createBlock(this, targetUser)

        homeFeed.rebuild()
        targetUser.homeFeed.rebuild()
    }

    fun unblockUser(targetUser: User) {
        if (targetUser.id !in blockedUsers) return

        blockedUsers.remove(targetUser.id)

        feeds.users.removeBlock(this, targetUser)

        homeFeed.rebuild()
        targetUser.homeFeed.rebuild()
    }

    fun isContentBlocked(user: User): Boolean = user.id in blockedUsers || id in user.blockedUsers

    fun publishPost(body: String, toFeeds: IntArray = intArrayOf(id)): Post {
        val post = feeds.posts.createPost(this, toFeeds, body)
        ownPosts.addPost(post)
        propagateToRecipients(post) { it.homeFeed.addPost(post) }
        return post
    }

    fun deletePost(post: Post) {
        feeds.posts.deletePost(post, this)
        ownPosts.removePost(post)
        getUsersWhoSeePost(post).forEach { it.homeFeed.removePost(post) }
    }

    fun likePost(post: Post) {
        feeds.posts.createLike(this, post)
        post.likes.add(id)
        if (!feeds.posts.isDirect(post) && !post.isGroupPost()) {
            likesTimeline.addPost(post)
            feeds.users.getAllUsers(subscribers).forEach {
                it.homeFeed.addPost(post, ShowReason(id, ShowReasonAction.Like))
            }
        }
        bumpPostInAllTimelines(post)
    }

    fun unlikePost(post: Post) {
        val usersWhoSawPost = getUsersWhoSeePost(post)
        feeds.posts.removeLike(this, post)
        post.likes.remove(id)
        likesTimeline.removePost(post)
        checkRemoveFromHomeFeed(post, usersWhoSawPost)
    }

    private fun checkRemoveFromHomeFeed(post: Post, usersWhoSawPost: Collection<User>) {
        usersWhoSawPost.forEach {
            val reasonToSee = it.getHomeFeedReason(post)
            if (reasonToSee != null) {
                it.homeFeed.updateShowReason(post, reasonToSee)
            } else {
                it.homeFeed.removePost(post)
            }
        }
    }

    fun commentOnPost(post: Post, text: String): Comment {
        val comment = feeds.posts.createComment(this, post, text)
        post.comments.add(comment)
        if (!feeds.posts.isDirect(post) && !post.isGroupPost()) {
            commentsTimeline.addPost(post)
            feeds.users.getAllUsers(subscribers).forEach {
                it.homeFeed.addPost(post, ShowReason(id, ShowReasonAction.Comment))
            }
        }
        bumpPostInAllTimelines(post)
        return comment
    }

    fun deleteComment(comment: Comment) {
        val author = feeds.users[comment.author] as User
        val post = feeds.posts.deleteComment(this, comment)
        val usersWhoSawPost = getUsersWhoSeePost(post)
        post.comments.remove(comment)
        if (post.comments.none { it.author == comment.author }) {
            author.commentsTimeline.removePost(post)
            author.checkRemoveFromHomeFeed(post, usersWhoSawPost)
        }
    }

    fun createGroup(userName: String): Group {
        val group = feeds.users.createGroup(this, userName)
        subscribeTo(group)
        return group
    }

    fun addGroupAdmin(group: Group, newAdmin: User) = feeds.users.addAdmin(group, newAdmin, this)
    fun removeGroupAdmin(group: Group, newAdmin: User) = feeds.users.removeAdmin(group, newAdmin, this)

    private fun propagateToRecipients(post: Post, callback: (User) -> Unit) {
        val recipients = setOf(id) + if (feeds.posts.isDirect(post))
            post.data.toFeeds.asIterable()
        else
            feeds.users.getAll(post.data.toFeeds.asList()).flatMap { it.subscribers.ids }

        feeds.users.getAllUsers(recipients).forEach { callback(it) }
    }

    private fun getUsersWhoSeePost(post: Post): Collection<User> {
        val author = feeds.users[post.authorId]
        val toFeeds = feeds.users.getAll(post.data.toFeeds)
        var allSeeds = setOf(author) + toFeeds.toSet()
        if (!post.isGroupPost()) {
            allSeeds += feeds.users.getAll(post.likes)
            allSeeds += feeds.users.getAll(post.comments.map { it.data.author }.toSet())
        }

        val allRecipientIds = allSeeds.flatMapTo(hashSetOf()) { it.subscribers.ids }
        return feeds.users.getAllUsers(allRecipientIds)
    }

    private fun Post.isGroupPost() = feeds.users.getAll(data.toFeeds).all { it is Group }

    private fun bumpPostInAllTimelines(post: Post) {
        getUsersWhoSeePost(post).forEach {
            if (id in it.subscriptions) {
                it.homeFeed.bumpPost(post)
            }
        }
    }

    private fun getHomeFeedReason(post: Post): ShowReason? {
        if (post.authorId in subscriptions) {
            return ShowReason(post.authorId, ShowReasonAction.Subscription)
        }
        for (commenter in post.comments.map { it.author }) {
            if (commenter in subscriptions) {
                return ShowReason(commenter, ShowReasonAction.Comment)
            }
        }
        for (liker in post.likes.ids) {
            if (liker in subscriptions) {
                return ShowReason(liker, ShowReasonAction.Like)
            }
        }
        return null
    }
}

public class BadRequestException() : Exception("The HTTP request isn't valid")
public class NotFoundException(val type: String, val id: Int) : Exception("Can't find $type with ID $id")
public class ForbiddenException() : Exception("This operation is forbidden")
public class ValidationException(val errorMessage: String) : Exception(errorMessage)

public class Group(feeds: Feeds, id: Int, userName: String, screenName: String, profile: String, private: Boolean)
    : Feed(feeds, id, userName, screenName, profile, private)
{
    val admins = UserIdList()

}

public class Users(private val userStore: UserStore, val feeds: Feeds) {
    private var allUsers = TreeMap<Int, Feed>()

    fun get(id: Int): Feed {
        val user = allUsers[id]
        if (user != null) {
            return user
        }
        val data = userStore.loadFeed(id)
        if (data != null) {
            val loadedFeed = createFeedObject(id, data)
            loadedFeed.subscribers.set(userStore.loadSubscribers(id))
            if (loadedFeed is User) {
                loadedFeed.subscriptions.set(userStore.loadSubscriptions(id))
                loadedFeed.blockedUsers.set(userStore.loadBlocks(id))
            }
            else if (loadedFeed is Group) {
                loadedFeed.admins.set(userStore.loadAdmins(id))
            }
            allUsers[id] = loadedFeed
            return loadedFeed
        }
        throw NotFoundException("User", id)
    }

    fun findByUserName(userName: String): Feed? {
        val id = userStore.lookupUserName(userName) ?: return null
        return get(id)
    }

    private fun createFeedObject(id: Int, data: FeedData): Feed = when(data.feedType) {
        FeedType.User -> User(feeds, id, data.userName, data.hashedPassword, data.screenName, data.profile, data.private)
        FeedType.Group -> Group(feeds, id, data.userName, data.screenName, data.profile, data.private)
    }

    fun getUser(id: Int): User = get(id) as User

    fun getAll(userIdList: UserIdList): List<Feed> = userIdList.ids.map { get(it) }
    fun getAll(userIdList: Collection<Int>): List<Feed> = userIdList.map { get(it) }
    fun getAll(userIdList: IntArray): List<Feed> = userIdList.map { get(it) }

    fun getAllUsers(userIdList: UserIdList): List<User> = userIdList.ids.map { get(it) as User }
    fun getAllUsers(userIdList: Collection<Int>): List<User> = userIdList.map { get(it) as User }

    fun createUser(userName: String,
                   email: String,
                   hashedPassword: String? = null,
                   private: Boolean = false) = createFeed(FeedType.User, userName, email, hashedPassword, private) as User

    fun createGroup(owner: User, name: String, private: Boolean = false): Group {
        val group = createFeed(FeedType.Group, name, private = private) as Group
        createAdmin(group, owner)
        return group
    }

    fun addAdmin(group: Group, newAdmin: User, requestingUser: User) {
        if (requestingUser.id !in group.admins) {
            throw ForbiddenException()
        }
        createAdmin(group, newAdmin)
    }

    fun removeAdmin(group: Group, admin: User, requestingUser: User) {
        if (requestingUser.id !in group.admins || group.admins.size() == 1) {
            throw ForbiddenException()
        }
        userStore.removeAdmin(group.id, admin.id)
        group.admins.remove(admin.id)
    }

    private fun createFeed(feedType: FeedType,
                           name: String,
                           email: String? = null,
                           hashedPassword: String? = null,
                           private: Boolean = false): Feed {
        if (userStore.lookupUserName(name) != null) {
            throw ValidationException("User name must be unique")
        }
        val feedData = FeedData(feedType, name, email, hashedPassword, name, "", private)
        val feedId = userStore.createFeed(feedData)
        val feed = createFeedObject(feedId, feedData)
        allUsers[feed.id] = feed
        return feed
    }

    fun createSubscription(fromUser: User, toUser: Feed) = userStore.createSubscription(fromUser.id, toUser.id)
    fun removeSubscription(fromUser: User, toUser: Feed) = userStore.removeSubscription(fromUser.id, toUser.id)

    fun createBlock(fromUser: User, toUser: User) = userStore.createBlock(fromUser.id, toUser.id)
    fun removeBlock(fromUser: User, toUser: User) = userStore.removeBlock(fromUser.id, toUser.id)

    fun createAdmin(group: Group, admin: User) {
        userStore.createAdmin(group.id, admin.id)
        group.admins.add(admin.id)

    }
}
