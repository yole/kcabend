package net.freefeed.kcabend.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.freefeed.kcabend.model.BadRequestException
import net.freefeed.kcabend.model.Feeds
import net.freefeed.kcabend.model.ForbiddenException
import net.freefeed.kcabend.model.User
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
location("/v1/session") data class session(val username: String, val password: String)
location("/v1/session") data class sessionRoot()
location("/v1/posts") data class post()
location("/v1/timelines/home") data class homeTimeline(val offset: Int?, val limit: Int?)

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
    private val timelineController = TimelineController(feeds)
    val objectMapper = ObjectMapper().registerModule(KotlinModule())

    init {
        locations {
            formPost<user>() { request, location -> userController.createUser(location.username, location.password) }
            handleOptions<userRoot>()

            formPost<session> { request, location -> userController.signin(location.username, location.password) }
            handleOptions<sessionRoot>()

            jsonGetWithUser<whoami>() { user, location -> userController.whoami(user) }

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
                            sendCorsHeaders()
                            body(this@handle, location)
                            status(HttpStatusCode.OK)
                        }
                        catch (e: ForbiddenException) {
                            status(HttpStatusCode.Forbidden)
                        }
                        catch (e: BadRequestException) {
                            status(HttpStatusCode.BadRequest)
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
        handleOptions<LocationT>()
    }

    inline fun RoutingEntry.jsonGetWithUser<reified LocationT : Any>(noinline handler: (User, LocationT) -> ObjectListResponse) {
        locationWithMethod<LocationT>(HttpMethod.Get) { request, location ->
            val authToken = request.header("X-Authentication-Token") ?: throw ForbiddenException()
            val user = authenticator.verifyAuthToken(authToken)
            sendJson(handler(user, location))
        }
        handleOptions<LocationT>()
    }

    inline fun RoutingEntry.formPost<reified LocationT : Any>(noinline handler: (ApplicationRequest, LocationT) -> ObjectListResponse) {
        locationWithMethod<LocationT>(HttpMethod.Post) { request, location ->
            sendJson(handler(request, location))
        }
        handleOptions<LocationT>()
    }

    inline fun RoutingEntry.jsonPost<reified LocationT : Any, reified RequestT: Any>(noinline handler: (RequestT) -> ObjectListResponse) {
        locationWithMethod<LocationT>(HttpMethod.Post) { request, location ->
            val jsonRequest = objectMapper.readValue(request.body, RequestT::class.java)
            val response = handler(jsonRequest)
            content(response.toJson())
        }
        handleOptions<LocationT>()
    }

    inline fun RoutingEntry.jsonPostWithUser<reified LocationT : Any, reified RequestT: Any>(noinline handler: (User, RequestT) -> ObjectListResponse) {
        locationWithMethod<LocationT>(HttpMethod.Post) { request, location ->
            val authToken = request.header("X-Authentication-Token") ?: throw ForbiddenException()
            val user = authenticator.verifyAuthToken(authToken)

            val jsonRequest = objectMapper.readValue(request.body, RequestT::class.java)
            sendJson(handler(user, jsonRequest))
        }
        handleOptions<LocationT>()
    }

    inline fun RoutingEntry.handleOptions<reified LocationT : Any>() {
        locationWithMethod<LocationT>(HttpMethod.Options) { request, location ->
            sendCorsHeaders()
        }
    }

    fun ApplicationResponse.sendJson(response: ObjectListResponse) {
        contentType(ContentType.Application.Json)
        contentStream { response.toJson(this) }
    }

    fun ApplicationResponse.sendCorsHeaders() {
        header("Access-Control-Allow-Origin", config.get("freefeed.origin"))
        header("Access-Control-Allow-Methods", "GET,PUT,POST,DELETE,OPTIONS")
        header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, X-Authentication-Token, Access-Control-Request-Method")
    }
}
