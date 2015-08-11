package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.Feeds
import net.freefeed.kcabend.model.User

class CreateUserRequest(val username: String, val email: String, val password: String)

class UserController(val feeds: Feeds, val authenticator: Authenticator) {
    public fun createUser(request: CreateUserRequest): ObjectListResponse {
        val user = authenticator.createUser(request.username, request.email, request.password)
        val response = ObjectListResponse()
        response.serializeMyProfile(user)
        response["authToken"] = authenticator.createAuthToken(user)
        return response
    }
}

fun ObjectListResponse.serializeMyProfile(user: User) {
    val userResponse = createRootObject("users")
    UserSerializer.serialize(userResponse, user)
}

object UserSerializer : ObjectSerializer<User>() {
    override val key: String get() = "users"

    override fun serialize(response: ObjectResponse, value: User) {
        response.serializeProperties(value, "id")
        response["username"] = value.userName
        response["type"] = "user"
    }
}
