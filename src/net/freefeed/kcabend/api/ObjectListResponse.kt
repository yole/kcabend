package net.freefeed.kcabend.api

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.freefeed.kcabend.model.IdObject
import java.io.StringWriter
import java.io.Writer
import kotlin.reflect.jvm.kotlin
import kotlin.reflect.memberProperties

public abstract class ObjectSerializer<T : IdObject> {
    abstract val key: String get
    abstract fun serialize(response: ObjectResponse, value: T)
    open fun serializedId(value: T): String = value.id.toString()
}

class ObjectResponse(private val owner: ObjectListResponse) {
    private val properties = hashMapOf<String, Any>()

    public fun get(key: String): String = properties[key] as String

    @suppress("UNCHECKED_CAST")
    public fun getIdList(key: String): List<String> = properties[key] as List<String>

    public fun serializeProperties<T>(value: T, vararg propertyNames: String) {
        val cls = value.javaClass.kotlin
        @suppress("UNCHECKED_CAST")
        propertyNames.forEach { propertyName ->
            val property = cls.memberProperties.find { it.name == propertyName }
            if (property == null) {
                throw IllegalStateException("Can't find property to serialize: $propertyName")
            }
            val propertyValue = property.get(value)
            if (propertyValue != null) {
                properties.put(propertyName, propertyValue.toString())
            }
        }
    }

    public fun set(name: String, value: String) {
        properties[name] = value
    }

    public fun serializeObjectProperty<T : IdObject>(propertyName: String,
                                                     propertyValue: T,
                                                     serializer: ObjectSerializer<T>) {
        properties.put(propertyName, propertyValue.id.toString())
        serializeObjectValue(propertyValue, serializer)
    }

    private fun serializeObjectValue<T : IdObject>(propertyValue: T, serializer: ObjectSerializer<T>) {
        val objectResponseForValue = owner.allocateObjectResponse(serializer.key, propertyValue)
        if (objectResponseForValue != null) {
            objectResponseForValue.set("id", serializer.serializedId(propertyValue))
            serializer.serialize(objectResponseForValue, propertyValue)
        }
    }

    public fun serializeObjectList<T : IdObject>(propertyName: String,
                                                 propertyValue: List<T>,
                                                 serializer: ObjectSerializer<T>) {
        val idList = propertyValue.map { serializer.serializedId(it) }
        properties[propertyName] = idList
        propertyValue.forEach { serializeObjectValue(it, serializer) }
    }

    fun toJsonNode(): ObjectNode {
        val node = JsonNodeFactory.instance.objectNode()
        for ((key, value) in properties) {
            node.set(key, valueToJson(value))
        }
        return node
    }
}

class ObjectList(private val owner: ObjectListResponse) {
    private val objects = hashMapOf<Int, ObjectResponse>()

    fun allocateObjectResponse(obj: IdObject) : ObjectResponse? {
        val existingResponse = objects[obj.id]
        if (existingResponse != null) {
            return null
        }
        val newResponse = ObjectResponse(owner)
        objects[obj.id] = newResponse
        return newResponse
    }

    fun findById(id: Int): ObjectResponse =
            objects[id] ?: throw java.lang.IllegalStateException("Can't find object with ID $id")

    fun toJsonNode(): ArrayNode {
        val node = JsonNodeFactory.instance.arrayNode()
        objects.values().forEach { node.add(it.toJsonNode()) }
        return node
    }
}

public class ObjectListResponse {
    private val rootMap = hashMapOf<String, Any>()

    fun getRootObject(key: String): ObjectResponse = rootMap[key] as ObjectResponse
    fun getObject(key: String, id: Int): ObjectResponse = (rootMap[key] as ObjectList).findById(id)
    fun get(key: String) = rootMap.get(key)

    fun createRootObject(key: String): ObjectResponse =
            rootMap.getOrPut(key) { ObjectResponse(this) } as ObjectResponse

    fun allocateObjectResponse(key: String, value: IdObject): ObjectResponse? {
        val objectList = rootMap.getOrPut(key) { ObjectList(this) } as ObjectList
        return objectList.allocateObjectResponse(value)
    }

    fun set(key: String, value: String) {
        rootMap.put(key, value)
    }

    fun withRootObject<T : IdObject>(value: T, serializer: ObjectSerializer<T>) : ObjectListResponse {
        val obj = createRootObject(serializer.key)
        obj["id"] = serializer.serializedId(value)
        serializer.serialize(obj, value)
        return this
    }

    fun toJson(): String {
        val writer = StringWriter()
        toJson(writer)
        return writer.toString()
    }

    fun toJson(writer: Writer) {
        val factory = JsonNodeFactory.instance
        val node = factory.objectNode()

        for ((key, value) in rootMap) {
            node.set(key, valueToJson(value))
        }

        val mapper = ObjectMapper()
        val generator = JsonFactory(mapper).createGenerator(writer)
        generator.writeTree(node)
    }

    companion object {
        val Empty = ObjectListResponse()
    }
}

private fun valueToJson(value: Any): JsonNode = when(value) {
    is String -> JsonNodeFactory.instance.textNode(value)
    is ObjectResponse -> value.toJsonNode()
    is ObjectList -> value.toJsonNode()
    is Collection<*> -> JsonNodeFactory.instance.arrayNode().addAll(value.map { valueToJson(it!!) })
    else -> throw IllegalStateException("Can't serialize value $value")
}
