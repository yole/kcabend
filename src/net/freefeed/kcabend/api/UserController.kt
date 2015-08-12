package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.Feeds
import net.freefeed.kcabend.model.User

class CreateUserRequest(val username: String, val email: String, val password: String)

class UserController(val feeds: Feeds, val authenticator: Authenticator) {
    public fun createUser(request: CreateUserRequest): ObjectListResponse {
        val user = authenticator.createUser(request.username, request.email, request.password)
        val response = ObjectListResponse().withRootObject(user, MyProfileSerializer)
        response["authToken"] = authenticator.createAuthToken(user)
        return response
    }

    public fun whoami(requestingUser: User): ObjectListResponse =
            ObjectListResponse().withRootObject(requestingUser, MyProfileSerializer)
}

object UserSerializer : ObjectSerializer<User>() {
    override val key: String get() = "users"

    override fun serialize(response: ObjectResponse, value: User) {
        response.serializeProperties(value, "id")
        response["username"] = value.userName
        response["type"] = "user"
    }
}

object MyProfileSerializer : ObjectSerializer<User>() {
    override val key: String get() = "users"

    override fun serialize(response: ObjectResponse, value: User) {
        UserSerializer.serialize(response, value)
    }
}
