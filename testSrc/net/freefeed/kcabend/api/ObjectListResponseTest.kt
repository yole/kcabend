package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.IdObject
import org.junit.Test
import org.junit.Assert.assertEquals

class ObjectListResponseTest {
    class DummyIdObject(id: Int, val name: String) : IdObject(id)
    object DummyIdObjectSerializer : ObjectSerializer<DummyIdObject>() {
        override val key: String get() = "dummy"

        override fun serialize(response: ObjectResponse, value: DummyIdObject) {
            response.serializeProperties(value, "id", "name")
        }
    }

    Test public fun toJson() {
        val response = ObjectListResponse()
        response["foo"] = "bar"
        val rootObj = response.createRootObject("posts")
        rootObj.set("body", "hello world")
        rootObj.serializeObjectProperty("dummy", DummyIdObject(15, "vasya"), DummyIdObjectSerializer)
        val json = response.toJson()
        assertEquals("""{"dummy":[{"name":"vasya","id":"15"}],"posts":{"body":"hello world","dummy":"15"},"foo":"bar"}""", json)
    }
}
