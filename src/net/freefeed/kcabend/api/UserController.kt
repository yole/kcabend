package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.Feed
import net.freefeed.kcabend.model.Feeds
import net.freefeed.kcabend.model.ForbiddenException
import net.freefeed.kcabend.model.User

class UserController(val feeds: Feeds, val authenticator: Authenticator) {
    private val myProfileSerializer = MyProfileSerializer(feeds)

    public fun createUser(username: String, password: String): ObjectListResponse {
        val user = authenticator.createUser(username, "", password)
        val response = ObjectListResponse().withRootObject(user, myProfileSerializer)
        response["authToken"] = authenticator.createAuthToken(user)
        return response
    }

    public fun signin(username: String, password: String): ObjectListResponse {
        val user = feeds.users.findByUserName(username) as? User ?: throw ForbiddenException()
        val authToken = authenticator.verifyPassword(user, password)
        val response = ObjectListResponse().withRootObject(user, UserSerializer)
        response["authToken"] = authToken
        return response
    }

    public fun whoami(requestingUser: User): ObjectListResponse =
            ObjectListResponse().withRootObject(requestingUser, myProfileSerializer)

    public fun subscribe(requestingUser: User, targetUserName: String): ObjectListResponse {
        val targetFeed = feeds.users.getByUserName(targetUserName)
        requestingUser.subscribeTo(targetFeed)
        return ObjectListResponse().withRootObject(requestingUser, myProfileSerializer)
    }
}

object UserSerializer : ObjectSerializer<Feed>() {
    override val key: String get() = "users"

    override fun serialize(response: ObjectResponse, value: Feed) {
        response["username"] = value.userName
        response["type"] = if (value is User) "user" else "group"
    }
}

class MyProfileSerializer(val feeds: Feeds) : ObjectSerializer<User>() {
    override val key: String get() = "users"

    override fun serialize(response: ObjectResponse, value: User) {
        UserSerializer.serialize(response, value)
        response.serializeObjectList("subscriptions", feeds.users.getAll(value.subscriptions),
                SubscriptionSerializer(value))
    }
}

class SubscriptionSerializer(val owner: User) : ObjectSerializer<Feed>() {
    override val key: String = "subscriptions"
    override fun serializedId(value: Feed): String = "${owner.id}:${value.id}"

    override fun serialize(response: ObjectResponse, value: Feed) {
        response.serializeObjectProperty("user", value, SubscriberSerializer)
    }
}

object SubscriberSerializer : ObjectSerializer<Feed>() {
    override val key: String get() = "subscribers"

    override fun serialize(response: ObjectResponse, value: Feed) {
        UserSerializer.serialize(response, value)
    }
}