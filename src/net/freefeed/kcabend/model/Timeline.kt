package net.freefeed.kcabend.model

public open class Timeline(val feeds: Feeds) {
   val postIds: MutableList<Int> = arrayListOf()

    public val postCount: Int get() = postIds.size()

    fun getPosts(requestingUser: User?): List<PostView> {
        return postIds.map { feeds.posts.getPost(it, requestingUser) }.filterNotNull().map { createView(it, requestingUser) }
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

    private fun createView(post: Post, requestingUser: User?): PostView =
            PostView(post, filterLikes(post.likes, requestingUser), getShowReason(post))

    protected open fun getShowReason(post: Post): ShowReason? = null

    private fun filterLikes(likes: UserIdList, requestingUser: User?): UserIdList {
        if (requestingUser == null) {
            return likes
        }

        val likers = feeds.users.getAll(likes)
        val visibleLikes = likers.filter {
            requestingUser.id !in it.blockedUsers && it.id !in requestingUser.blockedUsers
        }.map { it.id }
        return UserIdList(visibleLikes)
    }
}

public class PostsTimeline(feeds: Feeds, val owner: User) : Timeline(feeds) {
    init {
        postIds.addAll(feeds.posts.loadUserPostIds(owner))
    }
}

public class LikesTimeline(feeds: Feeds, val owner: User) : Timeline(feeds) {
    init {
        postIds.addAll(feeds.posts.loadUserLikes(owner))
    }
}

public class RiverOfNewsTimeline(feeds: Feeds, val owner: User) : Timeline(feeds) {
    private val reasons = hashMapOf<Int, ShowReason>()

    init {
        rebuild()
    }

    fun rebuild() {
        postIds.clear()
        reasons.clear()

        val unsortedPostIds = hashSetOf<Int>()
        val subscriptions = owner.subscriptions.ids.map { feeds.users[it] }
        subscriptions.forEach {
            unsortedPostIds.addAll(it.ownPosts.postIds)
        }
        subscriptions.forEach { user ->
            user.likesTimeline.postIds.forEach {
                if (unsortedPostIds.add(it)) {
                    reasons[it] = ShowReason(user.id, ShowReasonAction.Like)
                }
            }
        }

        postIds.addAll(unsortedPostIds.toList().sortDescendingBy { feeds.posts.getPost(it, owner)?.updatedAt ?: 0 })
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
