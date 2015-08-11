package net.freefeed.kcabend.api

import org.junit.Test
import org.junit.Assert.assertEquals

class TimelineControllerTest : AbstractControllerTest() {
    Test fun userHomeFeed() {
        val user = createUser("alpha")
        val postId = createPost(user, "Hello World")
        val response = timelineController.home(user, 0, 30)

        val timelines = response.getRootObject("timelines")
        val idList = timelines.getIdList("posts")
        assertEquals(1, idList.size())
        assertEquals(postId, idList[0])

        val posts = response.getObject("posts", Integer.parseInt(postId))
        assertEquals("Hello World", posts["body"])
    }
}
