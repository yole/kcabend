package net.freefeed.kcabend.api

import net.freefeed.kcabend.model.IdObject
import kotlin.reflect.jvm.kotlin
import kotlin.reflect.memberProperties

public abstract class ObjectSerializer<T : IdObject> {
    abstract val key: String get
    abstract fun serialize(response: ObjectResponse, value: T)
}

class ObjectResponse(private val owner: ObjectListResponse) {
    private val properties = hashMapOf<String, String>()

    public fun get(key: String): String = properties[key]!!

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
        val objectResponseForValue = owner.allocateObjectResponse(serializer.key, propertyValue)
        if (objectResponseForValue != null) {
            serializer.serialize(objectResponseForValue, propertyValue)
        }
    }
}

class ObjectList(private val owner: ObjectListResponse) {
    private val objects = hashMapOf<IdObject, ObjectResponse>()

    fun allocateObjectResponse(obj: IdObject) : ObjectResponse? {
        val existingResponse = objects[obj]
        if (existingResponse != null) {
            return null
        }
        val newResponse = ObjectResponse(owner)
        objects[obj] = newResponse
        return newResponse
    }

    fun findById(id: Int): ObjectResponse {
        for ((idObj, value) in objects) {
            if (idObj.id == id) {
                return value
            }
        }
        throw IllegalStateException("Can't find object with ID $id")
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
}
