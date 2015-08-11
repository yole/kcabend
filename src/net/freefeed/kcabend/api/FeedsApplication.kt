package net.freefeed.kcabend.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.freefeed.kcabend.model.Feeds
import net.freefeed.kcabend.model.ForbiddenException
import net.freefeed.kcabend.model.User
import net.freefeed.kcabend.persistence.TestPostStore
import net.freefeed.kcabend.persistence.TestUserStore
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.http.status
import org.jetbrains.ktor.locations.get
import org.jetbrains.ktor.locations.handle
import org.jetbrains.ktor.locations.location
import org.jetbrains.ktor.locations.locations
import org.jetbrains.ktor.routing.*
import kotlin.reflect.jvm.java

location("/v1/users") data class user()
location("/v1/posts") data class post()
location("/v1/timelines/home") data class homeTimeline(val offset: Int?, val limit: Int?)

public class FeedsApplication(config: ApplicationConfig) : Application(config) {
    private val feeds = Feeds(TestUserStore(), TestPostStore())
    val authenticator = Authenticator(feeds, "secret")
    private val userController = UserController(feeds, authenticator)
    private val postController = PostController(feeds)
    private val timelineController = TimelineController(feeds)
    val objectMapper = ObjectMapper().registerModule(KotlinModule())

    init {
        locations {
            jsonPost<user, CreateUserRequest>() { request -> userController.createUser(request) }
            jsonPostWithUser<post, CreatePostRequest>() { user, request -> postController.createPost(user, request) }
            jsonGetWithUser<homeTimeline>() {
                user, location -> timelineController.home(user, location.offset ?: 0, location.limit ?: 30)
            }
        }
    }

    inline fun RoutingEntry.locationWithMethod<reified T : Any>(method: String, noinline body: ApplicationResponse.(ApplicationRequest, T) -> Unit) {
        location(T::class) {
            methodParam(method) {
                handle<T> { location ->
                    respond {
                        try {
                            body(this@handle, location)
                            status(HttpStatusCode.OK)
                        }
                        catch (e: ForbiddenException) {
                            status(HttpStatusCode.Forbidden)
                        }
                        send()
                    }
                }
            }
        }
    }

    inline fun RoutingEntry.jsonGet<reified LocationT : Any>(noinline handler: (LocationT) -> ObjectListResponse) {
        locationWithMethod<LocationT>(HttpMethod.Get) { request, location ->
            val response = handler(location)
            content(response.toJson())
        }
    }

    inline fun RoutingEntry.jsonGetWithUser<reified LocationT : Any>(noinline handler: (User, LocationT) -> ObjectListResponse) {
        locationWithMethod<LocationT>(HttpMethod.Get) { request, location ->
            val authToken = request.header("X-Authentication-Token") ?: throw ForbiddenException()
            val user = authenticator.verifyAuthToken(authToken)

            val response = handler(user, location)
            content(response.toJson())
        }
    }

    inline fun RoutingEntry.jsonPost<reified LocationT : Any, reified RequestT: Any>(noinline handler: (RequestT) -> ObjectListResponse) {
        locationWithMethod<LocationT>(HttpMethod.Post) { request, location ->
            val jsonRequest = objectMapper.readValue(request.body, RequestT::class.java)
            val response = handler(jsonRequest)
            content(response.toJson())
        }
    }

    inline fun RoutingEntry.jsonPostWithUser<reified LocationT : Any, reified RequestT: Any>(noinline handler: (User, RequestT) -> ObjectListResponse) {
        locationWithMethod<LocationT>(HttpMethod.Post) { request, location ->
            val authToken = request.header("X-Authentication-Token") ?: throw ForbiddenException()
            val user = authenticator.verifyAuthToken(authToken)

            val jsonRequest = objectMapper.readValue(request.body, RequestT::class.java)
            val response = handler(user, jsonRequest)
            content(response.toJson())
        }
    }
}
