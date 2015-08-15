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

        val post = response.getObject("posts", Integer.parseInt(postId))
        assertEquals("Hello World", post["body"])
    }

    Test fun comments() {
        val user = createUser("alpha")
        val postId = createPost(user, "Hello World")
        createComment(user, postId, "Foo")
        val response = timelineController.home(user, 0, 30)

        val post = response.getObject("posts", Integer.parseInt(postId))
        val idList = post.getIdList("comments")
        assertEquals(1, idList.size())

        val comment = response.getObject("comments", Integer.parseInt(idList[0]))
        assertEquals("Foo", comment["body"])
    }
}
