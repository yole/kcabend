package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.Feeds
import net.freefeed.kcabend.model.Timeline
import net.freefeed.kcabend.model.User

public class TimelineController(val feeds: Feeds) {
    fun home(user: User, offset: Int, limit: Int): ObjectListResponse =
            serializeTimeline(user, user.homeFeed, offset, limit)

    private fun serializeTimeline(requestingUser: User, timeline: Timeline, offset: Int, limit: Int): ObjectListResponse {
        val posts = timeline.getPosts(requestingUser)
        val response = ObjectListResponse()
        val timelineResponse = response.createRootObject("timelines")
        timelineResponse.serializeObjectList("posts", posts, PostSerializer(feeds))
        return response
    }
}
