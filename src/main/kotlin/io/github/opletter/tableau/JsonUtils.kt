package io.github.opletter.tableau

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.toDataFrame

internal fun JsonObject.filterNotNullValues(): JsonObject {
    return JsonObject(filterValues { it != JsonNull })
}

internal fun Map<String, JsonArray>.toDataFrameFill(): DataFrame<*> {
    val size = values.maxOfOrNull { it.size } ?: 0
    return mapValues { (_, value) ->
        value + List(size - value.size) { null }
    }.toDataFrame()
}