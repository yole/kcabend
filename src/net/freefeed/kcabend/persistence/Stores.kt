package net.freefeed.kcabend.persistence

data class UserData(val userName: String, val screenName: String, val profile: String, val private: Boolean)

interface UserStore {
    fun createUser(data: UserData): Int
    fun loadUser(id: Int): UserData?
}

interface PostStore {
    fun createPost(author: Int, toFeeds: IntArray, body: String): Int
}

