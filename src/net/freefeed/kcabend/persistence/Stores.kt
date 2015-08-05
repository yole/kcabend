package net.freefeed.kcabend.persistence

data class UserData(val userName: String, val screenName: String, val profile: String, val private: Boolean)

interface UserStore {
    fun createUser(data: UserData): Int
    fun loadUser(id: Int): UserData?
}

data class PostData(val author: Int, val toFeeds: IntArray, val body: String)

interface PostStore {
    fun createPost(data: PostData): Int
    fun loadUserPostIds(author: Int): List<Int>
    fun loadPost(id: Int): PostData?
}

