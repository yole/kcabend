package net.freefeed.kcabend.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.testing.TestApplicationHost
import org.jetbrains.ktor.testing.handleRequest
import org.jetbrains.ktor.testing.withApplication
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import kotlin.reflect.jvm.java

public class IntegrationTest {
    var authToken: String? = null

    Before fun setUp() {
        authToken = null
    }

    Test fun testSmoke() {
        withApplication<FeedsApplication> {
            val createUserRequestResult = handleRequest(HttpMethod.Post, "/v1/users?username=alpha&password=password")
            var userId: String = ""
            with (createUserRequestResult) {
                assertEquals(HttpStatusCode.OK.value, response.status())
                val responseJson = ObjectMapper().readValue(response.content!!, Map::class.java)

                authToken = responseJson["authToken"] as String
                assertNotNull(authToken)
                assertEquals("alpha", responseJson.getByPath("users", "username"))
                userId = responseJson.getByPath("users", "id")
            }

            var postId: String = ""
            val createPostRequestResult = handleRequest(HttpMethod.Post, "/v1/posts") {
                body = """{"post": {"body": "Hello World"}}"""
                addHeader("X-Authentication-Token", authToken!!)
            }
            with (createPostRequestResult) {
                assertEquals(HttpStatusCode.OK.value, response.status())
                val responseJson = ObjectMapper().readValue(response.content!!, Map::class.java)
                assertEquals("Hello World", responseJson.getByPath("posts", "body"))
                postId = responseJson.getByPath("posts", "id")
            }

            val timelineResponse = handleAuthenticatedRequest(HttpMethod.Get, "/v1/timelines/home")
            assertEquals("Hello World", timelineResponse.getByPath("posts", 0, "body"))

            handleAuthenticatedRequest(HttpMethod.Post, "/v1/posts/$postId/like")

            val postGetResponse = handleAuthenticatedRequest(HttpMethod.Get, "/v1/posts/$postId")
            assertEquals(userId, postGetResponse.getByPath("posts", "likes", 0))
        }
    }

    private fun TestApplicationHost.handleAuthenticatedRequest(method: HttpMethod, url: String): Map<*, *> {
        val timelineRequestResult = handleRequest(method, url) {
            addHeader("X-Authentication-Token", authToken!!)
        }
        with (timelineRequestResult) {
            assertEquals(HttpStatusCode.OK.value, response.status())
            return ObjectMapper().readValue(response.content!!, Map::class.java)
        }
    }

    private fun Map<*, *>.getByPath<T>(vararg path: Any): T {
        var result : Any = this
        for (pathElement in path) {
            when (pathElement) {
                is String ->
                    result = (result as Map<*, *>)[pathElement]!!

                is Int ->
                    result = (result as List<*>)[pathElement]!!

                else ->
                    throw IllegalArgumentException("Unknown path element type")
            }
        }
        @suppress("UNCHECKED_CAST")
        return result as T
    }
}
