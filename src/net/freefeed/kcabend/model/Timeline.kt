package net.freefeed.kcabend.model

public abstract class TimelineView(val owner: Feed, val id: String) {
    abstract fun getPosts(requestingUser: User?): List<PostView>
}

public open class Timeline(val feeds: Feeds, owner: Feed, id: String) : TimelineView(owner, id) {
   val postIds: MutableList<Int> = arrayListOf()

    public val postCount: Int get() = postIds.size()

    override fun getPosts(requestingUser: User?): List<PostView> = getPosts(requestingUser, { true })

    fun getPosts(requestingUser: User?, predicate: (Post) -> Boolean): List<PostView> {
        return postIds
                .map { feeds.posts.getPost(it, requestingUser) }
                .filterNotNull()
                .filter(predicate)
                .map { feeds.posts.createView(it, requestingUser, getShowReason(it)) }
    }

    fun addPost(post: Post) {
        if (post.id !in postIds) {
            postIds.add(0, post.id)
        }
    }

    fun bumpPost(post: Post) {
        val index = postIds.indexOf(post.id)
        if (index >= 0) {
            postIds.remove(index)
            postIds.add(0, post.id)
        }
    }

    fun removePost(post: Post) {
        val index = postIds.indexOf(post.id)
        if (index >= 0) {
            postIds.remove(index)
        }
    }

    protected open fun getShowReason(post: Post): ShowReason? = null
}

public class PostsTimeline(feeds: Feeds, owner: Feed) : Timeline(feeds, owner, "$owner:posts") {
    init {
        postIds.addAll(feeds.posts.loadUserPostIds(owner))
    }
}

public class LikesTimeline(feeds: Feeds, owner: User) : Timeline(feeds, owner, "$owner:likes") {
    init {
        postIds.addAll(feeds.posts.loadUserLikes(owner))
    }
}

public class CommentsTimeline(feeds: Feeds, owner: User) : Timeline(feeds, owner, "$owner:comments") {
    init {
        postIds.addAll(feeds.posts.loadUserCommentedPosts(owner))
    }
}

public class RiverOfNewsTimeline(feeds: Feeds, val ownerUser: User) : Timeline(feeds, ownerUser, "$ownerUser:RiverOfNews") {
    private val reasons = hashMapOf<Int, ShowReason>()

    init {
        rebuild()
    }

    fun rebuild() {
        postIds.clear()
        reasons.clear()

        val unsortedPostIds = hashSetOf<Int>()

        fun addPostsFromTimeline(user: User, timeline: Timeline, showReasonAction: ShowReasonAction) {
            timeline.postIds.forEach {
                if (unsortedPostIds.add(it)) {
                    reasons[it] = ShowReason(user.id, showReasonAction)
                }
            }
        }

        val subscriptions = arrayListOf(owner)
        ownerUser.subscriptions.ids.mapTo(subscriptions) { feeds.users[it] }

        subscriptions.forEach {
            unsortedPostIds.addAll(it.ownPosts.postIds)
        }
        subscriptions.filterIsInstance<User>().forEach { user ->
            addPostsFromTimeline(user, user.likesTimeline, ShowReasonAction.Like)
            addPostsFromTimeline(user, user.commentsTimeline, ShowReasonAction.Comment)
        }

        postIds.addAll(unsortedPostIds.toList().sortDescendingBy { feeds.posts.getPost(it, ownerUser)?.updatedAt ?: 0 })
    }

    fun addPost(post: Post, reason: ShowReason?) {
        if (post.id !in postIds) {
            super.addPost(post)
            if (reason != null) {
                reasons[post.id] = reason
            }
        }
    }

    override fun getShowReason(post: Post): ShowReason? = reasons[post.id]

    fun updateShowReason(post: Post, reasonToSee: ShowReason) {
        reasons[post.id] = reasonToSee
    }
}

public class DirectMessagesTimeline(val feeds: Feeds, owner: User) : TimelineView(owner, "$owner:directs") {
    override fun getPosts(requestingUser: User?): List<PostView> {
        if (requestingUser != owner) {
            throw ForbiddenException()
        }
        return requestingUser.ownPosts.getPosts(requestingUser, { feeds.posts.isDirect(it) } )
    }
}
