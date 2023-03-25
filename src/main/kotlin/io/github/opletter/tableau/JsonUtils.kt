package io.github.opletter.tableau

import kotlinx.serialization.json.*
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.toDataFrame

internal fun JsonObject.deepCopy(): JsonObject {
    return JsonObject(toMap().mapValues { (_, value) -> value.deepCopy() })
}

internal fun JsonElement.deepCopy(): JsonElement {
    return when (this) {
        is JsonObject -> deepCopy()
        is JsonArray -> JsonArray(map { it.deepCopy() })
        is JsonPrimitive -> this
        else -> JsonNull
    }
}

internal fun JsonObject.filterNotNullValues(): JsonObject {
    return JsonObject(filterValues { it != JsonNull })
}

internal fun Map<String, JsonArray>.toDataFrameFill(): DataFrame<*> {
    val size = values.maxOfOrNull { it.size } ?: 0
    return mapValues { (_, value) ->
        value + List(size - value.size) { null }
    }.toDataFrame()
}