package net.freefeed.kcabend.model

public open class Timeline(val feeds: Feeds) {
   val postIds: MutableList<Int> = arrayListOf()

    fun getPosts(requestingUser: User?): List<Post> {
        return postIds.map { feeds.posts.getPost(it, requestingUser) }.filterNotNull()
    }

    fun addPost(post: Post) {
        if (post.id !in postIds) {
            postIds.add(0, post.id)
        }
    }
}

public class PostsTimeline(feeds: Feeds, val owner: User) : Timeline(feeds) {
    init {
        postIds.addAll(feeds.posts.loadUserPostIds(owner))
    }
}

public class RiverOfNewsTimeline(feeds: Feeds, val owner: User) : Timeline(feeds) {
    init {
        rebuild()
    }

    fun rebuild() {
        postIds.clear()
        feeds.users.forEachUser(owner.subscriptions) {
            postIds.addAll(it.posts.postIds)
        }
    }
}
