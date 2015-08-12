package net.freefeed.kcabend.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.testing.handleRequest
import org.jetbrains.ktor.testing.withApplication
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import kotlin.reflect.jvm.java

public class IntegrationTest {
    Test fun testSmoke() {
        var authToken: String? = null

        withApplication<FeedsApplication> {
            val createUserRequestResult = handleRequest(HttpMethod.Post, "/v1/users?username=alpha&password=password")
            with (createUserRequestResult) {
                assertEquals(HttpStatusCode.OK.value, response?.status)
                val responseJson = ObjectMapper().readValue(response?.content!!, Map::class.java)

                authToken = responseJson["authToken"] as String
                assertNotNull(authToken)
                assertEquals("alpha", (responseJson["users"] as Map<*, *>)["username"])
            }

            val createPostRequestResult = handleRequest(HttpMethod.Post, "/v1/posts") {
                body = """{"post": {"body": "Hello World"}}"""
                headers.put("X-Authentication-Token", authToken!!)
            }
            with (createPostRequestResult) {
                assertEquals(HttpStatusCode.OK.value, response?.status)
                val responseJson = ObjectMapper().readValue(response?.content!!, Map::class.java)
                assertEquals("Hello World", (responseJson["posts"] as Map<*, *>)["body"])
            }

            val timelineRequestResult = handleRequest(HttpMethod.Get, "/v1/timelines/home") {
                headers.put("X-Authentication-Token", authToken!!)
            }
            with (timelineRequestResult) {
                assertEquals(HttpStatusCode.OK.value, response?.status)
                val responseJson = ObjectMapper().readValue(response?.content!!, Map::class.java)
                assertEquals("Hello World", ((responseJson["posts"] as List<*>)[0] as Map<*, *>)["body"])
            }
        }
    }
}
