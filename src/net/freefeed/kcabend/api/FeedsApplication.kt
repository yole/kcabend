package net.freefeed.kcabend.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.freefeed.kcabend.model.*
import net.freefeed.kcabend.persistence.JdbcStore
import net.freefeed.kcabend.persistence.TestPostStore
import net.freefeed.kcabend.persistence.TestUserStore
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.handle
import org.jetbrains.ktor.locations.location
import org.jetbrains.ktor.locations.locations
import org.jetbrains.ktor.routing.*
import kotlin.reflect.jvm.java

location("/v1/users") data class user(val username: String, val password: String)
location("/v1/users") data class userRoot()
location("/v1/users/whoami") data class whoami()
location("/v1/users/{username}/subscribe") data class subscribe(val username: String)
location("/v1/users/{username}/unsubscribe") data class unsubscribe(val username: String)

location("/v1/session") data class session(val username: String, val password: String)
location("/v1/session") data class sessionRoot()

location("/v1/posts") data class post() {
    location("/{id}") data class id(val id: String) {
        location("/like") data class like(val postId: id)
    }
}

location("/v1/comments") data class comment()

location("/v1/timelines/home") data class homeTimeline(val offset: Int?, val limit: Int?)
location("/v1/timelines/{username}") data class postsTimeline(val username: String, val offset: Int?, val limit: Int?)

public class FeedsApplication(config: ApplicationConfig) : Application(config) {
    private fun ApplicationConfig.isTest() = get("ktor.deployment.environment") == "test"

    private fun createFeeds(config: ApplicationConfig): Feeds {
        if (config.isTest()) {
            return Feeds(TestUserStore(), TestPostStore())
        }

        val jdbcStore = JdbcStore(config.get("freefeed.database.driver"),
                config.get("freefeed.database.connection"))
        return Feeds(jdbcStore, jdbcStore)
    }

    private fun getSecret(config: ApplicationConfig): String {
        if (config.isTest()) return "secret"
        return config.get("freefeed.secret")
    }

    private val feeds = createFeeds(config)
    val authenticator = Authenticator(feeds, getSecret(config))
    private val userController = UserController(feeds, authenticator)
    private val postController = PostController(feeds)
    private val commentsController = CommentsController(feeds, postController)
    private val timelineController = TimelineController(feeds)
    val objectMapper = ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(KotlinModule())

    init {
        locations {
            formPost<user>() { location -> userController.createUser(location.username, location.password) }
            handleOptions<userRoot>()

            formPost<session> { location -> userController.signin(location.username, location.password) }
            handleOptions<sessionRoot>()

            jsonGetWithUser<whoami>() { user, location -> userController.whoami(user) }

            formPostWithUser<subscribe> { user, location -> userController.subscribe(user, location.username) }
            formPostWithUser<unsubscribe> { user, location -> userController.unsubscribe(user, location.username) }

            jsonPostWithUser<post, CreatePostRequest>() { user, request -> postController.createPost(user, request) }
            jsonGetWithOptionalUser<post.id>() { user, location -> postController.getPost(user, location.id) }

            formPostWithUser<post.id.like>() { user, location ->
                postController.like(user, location.postId.id )
            }

            jsonPostWithUser<comment, CreateCommentRequest>() { user, request ->
                commentsController.createComment(user, request)
            }

            jsonGetWithUser<homeTimeline>() { user, location ->
                timelineController.home(user, location.offset ?: 0, location.limit ?: 30)
            }
            jsonGetWithOptionalUser<postsTimeline>() { user, location ->
                timelineController.posts(user, location.username, location.offset ?: 0, location.limit ?: 30)
            }
        }
    }

    inline fun RoutingEntry.locationWithMethod<reified T : Any>(method: HttpMethod, noinline body: ApplicationResponse.(ApplicationRequest, T) -> ApplicationRequestStatus) {
        location(T::class) {
            method(method) {
                handle<T> { location ->
                    try {
                        response.sendCorsHeaders()
                        response.body(request, location)
                    }
                    catch (e: ForbiddenException) {
                        response.sendError(HttpStatusCode.Forbidden.value, e.getMessage() ?: "Forbidden")
                    }
                    catch (e: BadRequestException) {
                        response.sendError(HttpStatusCode.BadRequest.value, e.getMessage() ?: "Bad request")
                    }
                    catch (e: ValidationException) {
                        response.sendError(HttpStatusCode.BadRequest.value, e.errorMessage)
                    }
                    catch (e: NotFoundException) {
                        response.sendError(HttpStatusCode.NotFound.value, e.getMessage() ?: "Not found")
                    }
            }
            }
        }
    }

    inline fun RoutingEntry.jsonGet<reified LocationT : Any>(noinline handler: (LocationT) -> ObjectListResponse) {
        locationWithMethod<LocationT>(HttpMethod.Get) { request, location ->
            sendJson(handler(location))
        }
        handleOptions<LocationT>()
    }

    inline fun RoutingEntry.jsonGetWithUser<reified LocationT : Any>(noinline handler: (User, LocationT) -> ObjectListResponse) {
        locationWithMethod<LocationT>(HttpMethod.Get) { request, location ->
            val user = request.requireAuthentication()
            sendJson(handler(user, location))
        }
        handleOptions<LocationT>()
    }

    inline fun RoutingEntry.jsonGetWithOptionalUser<reified LocationT : Any>(noinline handler: (User?, LocationT) -> ObjectListResponse) {
        locationWithMethod<LocationT>(HttpMethod.Get) { request, location ->
            val user = request.allowAuthentication()
            sendJson(handler(user, location))
        }
        handleOptions<LocationT>()
    }

    inline fun RoutingEntry.formPost<reified LocationT : Any>(noinline handler: (LocationT) -> ObjectListResponse) {
        locationWithMethod<LocationT>(HttpMethod.Post) { request, location ->
            sendJson(handler(location))
        }
        handleOptions<LocationT>()
    }

    inline fun RoutingEntry.formPostWithUser<reified LocationT : Any>(noinline handler: (User, LocationT) -> ObjectListResponse) {
        locationWithMethod<LocationT>(HttpMethod.Post) { request, location ->
            val user = request.requireAuthentication()
            sendJson(handler(user, location))
        }
        handleOptions<LocationT>()
    }

    inline fun RoutingEntry.jsonPostWithUser<reified LocationT : Any, reified RequestT: Any>(noinline handler: (User, RequestT) -> ObjectListResponse) {
        locationWithMethod<LocationT>(HttpMethod.Post) { request, location ->
            val user = request.requireAuthentication()
            val jsonRequest = objectMapper.readValue(request.body, RequestT::class.java)
            sendJson(handler(user, jsonRequest))
        }
        handleOptions<LocationT>()
    }

    inline fun RoutingEntry.handleOptions<reified LocationT : Any>() {
        locationWithMethod<LocationT>(HttpMethod.Options) { request, location ->
            status(HttpStatusCode.OK)
            sendCorsHeaders()
            sendText("")
        }
    }

    fun ApplicationRequest.requireAuthentication(): User {
        val authToken = header("X-Authentication-Token") ?: throw ForbiddenException()
        return authenticator.verifyAuthToken(authToken)
    }

    fun ApplicationRequest.allowAuthentication(): User? {
        val authToken = header("X-Authentication-Token") ?: return null
        return authenticator.verifyAuthToken(authToken)
    }

    fun ApplicationResponse.sendJson(response: ObjectListResponse): ApplicationRequestStatus {
        status(HttpStatusCode.OK)
        return sendText(ContentType.Application.Json, response.toJson())
    }

    fun ApplicationResponse.sendCorsHeaders() {
        if (!config.isTest()) {
            header("Access-Control-Allow-Origin", config.get("freefeed.origin"))
            header("Access-Control-Allow-Methods", "GET,PUT,POST,DELETE,OPTIONS")
            header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, X-Authentication-Token, Access-Control-Request-Method")
        }
    }
}
