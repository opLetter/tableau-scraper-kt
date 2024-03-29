package io.github.opletter.tableau

import io.github.opletter.tableau.data.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.abs

internal fun getSelectedFilters(presModel: JsonObject, worksheetName: String): List<JsonObject> {
    val zones = getZones(presModel) ?: return emptyList()
    return zones.values
        .mapNotNull { z ->
            z as JsonObject
            if (z["worksheet"]?.jsonPrimitive?.content != worksheetName) return@mapNotNull null

            val categoricalFilter = z["presModelHolder"]?.jsonObject?.get("quickFilterDisplay")
                ?.jsonObject?.get("quickFilter")?.jsonObject?.get("categoricalFilter")?.jsonObject
                ?: return@mapNotNull null

            buildJsonObject {
                put("fn", categoricalFilter["fn"]!!)
                put("columnFullNames", categoricalFilter["columnFullNames"]!!)
                put("domainTables", categoricalFilter["domainTables"]!!)
            }
        }.ifEmpty {
            val storyPoints = zones.values.firstNotNullOfOrNull {
                it.jsonObject["presModelHolder"]?.jsonObject?.get("flipboard")?.jsonObject?.get("storyPoints")?.jsonObject
            } ?: return emptyList()
            storyPoints.values.flatMap { key ->
                val dashboardZones = key.jsonObject["dashboardPresModel"]!!.jsonObject["zones"]!!.jsonObject
                getSelectedFilters(
                    buildJsonObject {
                        putJsonObject("workbookPresModel") { put("dashboardPresModel", dashboardZones) }
                    },
                    worksheetName
                )
            }
        }
}

@OptIn(ExperimentalSerializationApi::class)
private fun processFilters(filters: List<JsonObject>, selectedFilters: List<JsonObject>): List<JsonObject> {
    return filters.flatMap { t ->
        val table = t["table"]?.jsonObject ?: return@flatMap emptyList()
        val columns = table["schema"]?.jsonArray?.map { z ->
            Triple(
                z.jsonObject["caption"]!!.jsonPrimitive.content,
                z.jsonObject["name"]!!.jsonArray,
                z.jsonObject["ordinal"]!!.jsonPrimitive.content
            )
        } ?: return@flatMap emptyList()
        val tuples = table["tuples"]?.jsonArray ?: return@flatMap emptyList()
        val values = tuples.mapNotNull { z ->
            val x = z.jsonObject["t"]?.jsonArray?.getOrNull(0) ?: return@mapNotNull null
            x.jsonObject["v"]!!.jsonPrimitive.content
        }
        val selection = tuples.mapNotNull { z ->
            val x = z.jsonObject["t"]?.jsonArray?.getOrNull(0)
            if (x != null && z.jsonObject["s"]?.jsonPrimitive?.booleanOrNull == true) {
                x.jsonObject["v"]!!.jsonPrimitive.content
            } else null
        }
        columns.map { c ->
            val globalFieldName = "[${c.second[0].jsonPrimitive.content}].[${c.second[1].jsonPrimitive.content}]"
            val selectionAlt = selectedFilters.filter { it["fn"]!!.jsonPrimitive.content == globalFieldName }
            buildJsonObject {
                put("column", c.first)
                put("ordinal", c.third)
                putJsonArray("values") { addAll(values) }
                put("globalFieldName", globalFieldName)
                putJsonArray("selection") {
                    val selections = if (
                        t["all"]?.jsonPrimitive?.booleanOrNull == true ||
                        t["allChecked"]?.jsonPrimitive?.booleanOrNull == true
                    ) values + "all" else selection
                    addAll(selections)
                }
                putJsonArray("selectionAlt") { addAll(selectionAlt) }
            }
        }
    }
}

private fun getFilterValues(zones: JsonObject, worksheetName: String): List<List<JsonObject>> {
    return zones.values.mapNotNull {
        if (it.jsonObject["worksheet"]?.jsonPrimitive?.content != worksheetName) return@mapNotNull null

        val content = it.jsonObject["presModelHolder"]?.jsonObject?.get("visual")?.jsonObject
            ?.get("filtersJson")?.jsonPrimitive?.content
            ?: return@mapNotNull null

        Json.decodeFromString<List<JsonObject>>(content)
    }
}

internal fun listFilters(
    presModel: JsonObject,
    worksheetName: String,
    selectedFilters: List<JsonObject>,
    rootDashboard: String,
): List<JsonObject> {
    val zones = getZones(presModel)!!
    val filters = getFilterValues(zones, worksheetName).flatten()
    if (filters.isNotEmpty()) return processFilters(filters, selectedFilters)

    val storypoint = zones.values.firstNotNullOfOrNull { z ->
        z.jsonObject["presModelHolder"]?.jsonObject?.get("flipboard")?.jsonObject?.get("storyPoints")?.jsonObject
    } ?: return emptyList()

    return storypoint.values.flatMap { i ->
        val storyboardId = i.jsonObject["storyPointId"]!!.jsonPrimitive.content
        val dashboardPresModel = i.jsonObject["dashboardPresModel"]!!.jsonObject

        val sheetPath = dashboardPresModel["sheetPath"]?.jsonObject
        val visualIds = dashboardPresModel["visualIds"]

        val (storyboard, dashboard) = if (sheetPath != null) {
            val storyboard = sheetPath["storyboard"]!!.jsonPrimitive.content
            val dashboard = if (sheetPath["isDashboard"]!!.jsonPrimitive.boolean) {
                sheetPath["sheetName"]!!.jsonPrimitive.content
            } else rootDashboard

            storyboard to dashboard
        } else if (visualIds != null) {
            val k = if (visualIds is JsonArray) visualIds[0] else visualIds
            val storyboard = k.jsonObject["storyboard"]!!.jsonPrimitive.content
            val dashboard = k.jsonObject["dashboard"]!!.jsonPrimitive.content

            storyboard to dashboard
        } else {
            println("sheetPath and visualIds not found in dashboardPresModel")
            return emptyList()
        }

        val zones2 = dashboardPresModel["zones"]!!.jsonObject
        val filters2 = getFilterValues(zones2, worksheetName).flatten()

        processFilters(filters2, selectedFilters).map {
            buildJsonObject {
                it.forEach { (k, v) -> put(k, v) }
                put("storyboard", storyboard)
                put("dashboard", dashboard)
                put("storyboardId", storyboardId)
            }
        }
    }
}

internal fun getFiltersForAllWorksheet(
    data: JsonObject,
    info: JsonObject,
    rootDashboard: String,
    cmdResponse: Boolean = false,
): Map<String, List<JsonObject>> {
    return if (cmdResponse) {
        val presModel = data["vqlCmdResponse"]!!
            .jsonObject["layoutStatus"]!!
            .jsonObject["applicationPresModel"]!!
            .jsonObject
        val worksheets = listWorksheetCmdResponse(presModel).ifEmpty { listStoryPointsCmdResponse(presModel) }
        worksheets.associate { worksheet ->
            val wsName = worksheet["worksheet"]!!.jsonPrimitive.content
            wsName to listFilters(presModel, wsName, getSelectedFilters(presModel, wsName), rootDashboard)
        }
    } else {
        val presModelMapVizData = getPresModelVizData(data)
        val presModelMapVizInfo = getPresModelVizInfo(info) ?: return emptyMap()
        val worksheets = presModelMapVizData?.let { listWorksheet(it) }
            ?: listWorksheetInfo(presModelMapVizInfo).ifEmpty { listStoryPointsInfo(presModelMapVizInfo) }

        worksheets.associateWith { worksheet ->
            listFilters(
                presModelMapVizInfo,
                worksheet,
                getSelectedFilters(presModelMapVizInfo, worksheet),
                rootDashboard
            )
        }
    }
}

internal fun listStoryPointsInfo(presModel: JsonObject): List<String> {
    val storypoint = getZones(presModel)!!.values.firstNotNullOfOrNull {
        it.jsonObject["presModelHolder"]?.jsonObject?.get("flipboard")?.jsonObject?.get("storyPoints")?.jsonObject
    }?.values?.firstOrNull() ?: return emptyList()
    return storypoint.jsonObject["dashboardPresModel"]!!.jsonObject["zones"]!!.jsonObject.values
        .mapNotNull { z ->
            z.jsonObject.takeIf {
                "vizData" in it["presModelHolder"]?.jsonObject?.get("visual")?.jsonObject.orEmpty()
            }?.get("worksheet")?.jsonPrimitive?.content
        }
}

internal fun selectWorksheet(
    data: JsonObject,
    single: Boolean = false,
): List<String> {
    val presModelmap = getPresModelVizData(data)!!
    val worksheets = listWorksheet(presModelmap).ifEmpty { return emptyList() }

    val addText = if (single) "" else "(enter for all)"
    print("select worksheet by index $addText: ")
    val selected = readlnOrNull()

    return when {
        !selected.isNullOrEmpty() -> listOf(worksheets[selected.toInt()])
        single -> throw Exception("you must select one worksheet")
        else -> worksheets
    }
}

internal fun getPresModelVizData(data: JsonObject): JsonObject? {
    return getPresModelVizDataWithoutViz(data)?.takeIf { "vizData" in it }
}

internal fun getPresModelVizDataWithoutViz(data: JsonObject): JsonObject? {
    return data["secondaryInfo"]?.jsonObject?.get("presModelMap")?.jsonObject
}

internal fun getPresModelVizInfo(info: JsonObject): JsonObject? {
    return info["worldUpdate"]?.jsonObject?.get("applicationPresModel")?.jsonObject
        ?.takeIf { "workbookPresModel" in it }
}

internal fun listWorksheetCmdResponse(presModel: JsonObject): List<JsonObject> {
    return getZones(presModel)?.values?.filter { zone ->
        "vizData" in zone.jsonObject["presModelHolder"]?.jsonObject?.get("visual")?.jsonObject.orEmpty()
    }?.map { it.jsonObject }.orEmpty()
}

internal fun listWorksheet(presModelMap: JsonObject): List<String> {
    val presModelHolder = presModelMap["vizData"]?.jsonObject?.get("presModelHolder")?.jsonObject
        ?: throw NoSuchElementException("Missing presModelMap[\"vizData\"][\"presModelHolder\"] field")

    val genPresModelMapPresModel = presModelHolder["genPresModelMapPresModel"]?.jsonObject
        ?: throw NoSuchElementException("Missing presModelMap[\"vizData\"][\"presModelHolder\"][\"genPresModelMapPresModel\"] field")

    val presModelMapObj = genPresModelMapPresModel["presModelMap"]?.jsonObject
        ?: throw NoSuchElementException("Missing presModelMap[\"vizData\"][\"presModelHolder\"][\"genPresModelMapPresModel\"][\"presModelMap\"] field")

    return presModelMapObj.keys.toList()
}

internal fun listWorksheetInfo(presModel: JsonObject): List<String> {
    return getZones(presModel)!!.values.mapNotNull { z ->
        z.jsonObject.takeIf { "visual" in it["presModelHolder"]?.jsonObject.orEmpty() }
            ?.get("worksheet")?.jsonPrimitive?.content
    }
}

internal fun getZones(jsonObject: JsonObject): JsonObject? =
    jsonObject["workbookPresModel"]?.jsonObject
        ?.get("dashboardPresModel")?.jsonObject
        ?.get("zones")?.jsonObject
        ?.filterNotNullValues()

internal fun getIndicesInfo(
    presModelMap: JsonObject,
    worksheet: String,
    noSelectFilter: Boolean = true,
    noFieldCaption: Boolean = false,
): List<IndicesInfo> {
    val genVizDataPresModel = presModelMap["vizData"]!!
        .jsonObject["presModelHolder"]!!
        .jsonObject["genPresModelMapPresModel"]!!
        .jsonObject["presModelMap"]!!
        .jsonObject[worksheet]!!
        .jsonObject["presModelHolder"]!!
        .jsonObject["genVizDataPresModel"]!!
        .jsonObject

    val columnsData = genVizDataPresModel["paneColumnsData"]?.jsonObject ?: return emptyList()

    return columnsData["vizDataColumns"]!!.jsonArray.flatMap {
        it as JsonObject
        val fieldCaption = it["fieldCaption"]
        val isAutoSelect = it["isAutoSelect"]?.jsonPrimitive?.boolean == true

        if ((fieldCaption != null || noFieldCaption) && (noSelectFilter || isAutoSelect)) {
            it["paneIndices"]!!.jsonArray.mapIndexed { index, jsonElement ->
                val paneIndex = jsonElement.jsonPrimitive.int
                val columnIndex = it["columnIndices"]!!.jsonArray[index].jsonPrimitive.int
                val paneColumnsList = columnsData["paneColumnsList"]!!.jsonArray[paneIndex]
                    .jsonObject["vizPaneColumns"]!!.jsonArray[columnIndex].jsonObject

                IndicesInfo(
                    fieldCaption = fieldCaption?.jsonPrimitive?.content.orEmpty(),
                    tupleIds = paneColumnsList["tupleIds"]!!.jsonArray.map { it.jsonPrimitive.int },
                    valueIndices = paneColumnsList["valueIndices"]!!.jsonArray.map { it.jsonPrimitive.int },
                    aliasIndices = paneColumnsList["aliasIndices"]!!.jsonArray.map { it.jsonPrimitive.int },
                    dataType = it["dataType"]?.jsonPrimitive?.content.orEmpty(),
                    fn = it["fn"]?.jsonPrimitive?.content.orEmpty(),
                )
            }
        } else emptyList()
    }
}

private fun getIndicesInfoSpecial(
    zones: List<JsonObject>,
    worksheet: String,
    noSelectFilter: Boolean = true,
    noFieldCaption: Boolean = false,
): List<IndicesInfo> {
    val zone = zones.firstOrNull { it["worksheet"]!!.jsonPrimitive.content == worksheet }
        ?: return emptyList()

    val columnsData = zone["presModelHolder"]!!.jsonObject["visual"]!!.jsonObject["vizData"]!!
        .jsonObject["paneColumnsData"]?.jsonObject ?: return emptyList()

    return columnsData["vizDataColumns"]!!.jsonArray.mapNotNull {
        it as JsonObject
        val fieldCaption = it["fieldCaption"]
        val isAutoSelect = it["isAutoSelect"]?.jsonPrimitive?.boolean == true

        if ((fieldCaption != null || noFieldCaption) && (noSelectFilter || isAutoSelect)) {
            val paneIndex = it["paneIndices"]!!.jsonArray[0].jsonPrimitive.int
            val columnIndex = it["columnIndices"]!!.jsonArray[0].jsonPrimitive.int
            val vizPaneColumn = columnsData["paneColumnsList"]!!.jsonArray[paneIndex].jsonObject["vizPaneColumns"]!!
                .jsonArray[columnIndex].jsonObject
            val tupleIds = vizPaneColumn["tupleIds"]!!.jsonArray.map { it.jsonPrimitive.int }
            val valueIndices = vizPaneColumn["valueIndices"]!!.jsonArray.map { it.jsonPrimitive.int }
            val aliasIndices = vizPaneColumn["aliasIndices"]!!.jsonArray.map { it.jsonPrimitive.int }

            IndicesInfo(
                fieldCaption = fieldCaption?.jsonPrimitive?.content.orEmpty(),
                tupleIds = tupleIds,
                valueIndices = valueIndices,
                aliasIndices = aliasIndices,
                dataType = it["dataType"]?.jsonPrimitive?.content.orEmpty(),
                fn = it["fn"]?.jsonPrimitive?.content.orEmpty(),
            )
        } else null
    }
}

internal fun getIndicesInfoStoryPoint(
    presModel: JsonObject,
    worksheet: String,
    noSelectFilter: Boolean = true,
    noFieldCaption: Boolean = false,
): List<IndicesInfo> =
    getIndicesInfoSpecial(listWorksheetStoryPoint(presModel), worksheet, noSelectFilter, noFieldCaption)

internal fun listWorksheetStoryPoint(
    presModel: JsonObject,
    hasWorksheet: Boolean = true,
    scraper: Scraper? = null,
): List<JsonObject> {
    val actualZones = scraper?.zones ?: getZones(presModel) ?: return emptyList()
    val storyPoint = actualZones.values.firstNotNullOfOrNull {
        it.jsonObject["presModelHolder"]?.jsonObject?.get("flipboard")?.jsonObject?.get("storyPoints")?.jsonObject
    }?.values?.firstOrNull() ?: return emptyList()

    val innerZones = storyPoint.jsonObject["dashboardPresModel"]!!.jsonObject["zones"]!!.jsonObject

    return innerZones.values.mapNotNull { innerZone ->
        innerZone as JsonObject
        if (hasWorksheet) {
            innerZone.takeIf {
                "worksheet" in it &&
                        "vizData" in it.jsonObject["presModelHolder"]?.jsonObject?.get("visual")?.jsonObject.orEmpty()
            }
        } else innerZone.takeIf { "presModelHolder" in it }
    }
}

private fun getDataFullHelper(dataSegments: JsonObject, originSegments: JsonObject): JsonObject {
    val a = originSegments.filterNotNullValues().flatMap { it.value.jsonObject["dataColumns"]!!.jsonArray }
    val b = dataSegments.filterNot { it.key in originSegments || it.value == JsonNull }
        .flatMap { it.value.jsonObject["dataColumns"]!!.jsonArray }
    return (a + b)
        .groupBy({ it.jsonObject["dataType"]!!.jsonPrimitive.content }, { it.jsonObject["dataValues"]!!.jsonArray })
        .mapValues { (_, value) -> JsonArray(value.flatten()) }
        .let { JsonObject(it) }
}

internal fun getDataFull(presModelMap: JsonObject, originSegments: JsonObject): JsonObject {
    val dataSegments = presModelMap["dataDictionary"]?.jsonObject
        ?.get("presModelHolder")?.jsonObject
        ?.get("genDataDictionaryPresModel")?.jsonObject
        ?.get("dataSegments")?.jsonObject
        ?: return JsonObject(emptyMap())

    return getDataFullHelper(dataSegments, originSegments)
}

internal fun onDataValue(valueIndex: Int, data: JsonArray, cstring: JsonArray): JsonElement {
    return if (valueIndex >= 0) data[valueIndex] else cstring[abs(valueIndex) - 1]
}

internal enum class IndexMode {
    VALUE, ALIAS
}

internal fun getData(dataFull: JsonObject, indicesInfo: List<LimitedIndicesInfo>): Map<String, JsonArray> {
    val cstring = dataFull["cstring"]?.jsonArray ?: JsonArray(emptyList())

    fun processIndices(index: LimitedIndicesInfo, mode: IndexMode, useFn: Boolean): Pair<String, JsonArray>? {
        val x = when (mode) {
            IndexMode.VALUE -> index.valueIndices
            IndexMode.ALIAS -> index.aliasIndices
        }
        val t = dataFull[index.dataType]?.jsonArray ?: cstring
        return x.mapNotNull { idx ->
            if (idx < t.size) onDataValue(idx, t, cstring) else null
        }.takeIf { it.isNotEmpty() }?.let { arr ->
            val fn = index.fn.takeIf { useFn && it.isNotEmpty() }?.let { "-$it" }.orEmpty()
            val key = "${index.fieldCaption}$fn-${mode.name.lowercase()}"
            key to JsonArray(arr)
        }
    }
    // original code handles case with multiple indicesInfo with same fieldCaption by adding fn
    // This should do the same thing, but by handling duplicates at same time
    return indicesInfo
        .groupBy { it.fieldCaption }
        .flatMap { (_, indices) ->
            val a = indices.filter { it.valueIndices.isNotEmpty() }
            val b = indices.filter { it.aliasIndices.isNotEmpty() }
            listOfNotNull(
                a.firstOrNull()?.let { processIndices(it, IndexMode.VALUE, false) },
                b.firstOrNull()?.let { processIndices(it, IndexMode.ALIAS, false) },
                (if (a.size > 1) a.last() else null)?.let { processIndices(it, IndexMode.VALUE, true) },
                (if (b.size > 1) b.last() else null)?.let { processIndices(it, IndexMode.ALIAS, true) },
            )
        }.toMap()
}

internal fun getIndicesInfoVqlResponse(
    presModel: JsonObject,
    worksheet: String,
    noSelectFilter: Boolean = true,
    noFieldCaption: Boolean = false,
): List<IndicesInfo> =
    getIndicesInfoSpecial(listWorksheetCmdResponse(presModel), worksheet, noSelectFilter, noFieldCaption)

internal fun listStoryPointsCmdResponse(presModel: JsonObject, scraper: Scraper? = null): List<JsonObject> =
    listWorksheetStoryPoint(presModel, true, scraper)

internal fun getDataFullCmdResponse(
    presModel: JsonObject,
    originSegments: JsonObject,
    dataSegments: Map<String, JsonObject> = emptyMap(),
): JsonObject {
    val altDataSegments = dataSegments.ifEmpty {
        presModel["dataDictionary"]?.jsonObject?.get("dataSegments")?.jsonObject?.filterNotNullValues().orEmpty()
    }
    return getDataFullHelper(JsonObject(altDataSegments), originSegments)
}

internal fun getWorksheetCmdResponse(selectedZone: JsonObject, dataFull: JsonObject): Map<String, JsonArray>? {
    val details = selectedZone["presModelHolder"]!!.jsonObject["visual"]!!.jsonObject["vizData"]!!.jsonObject
    val columnsData = details["paneColumnsData"]?.jsonObject ?: return null

    val result = columnsData["vizDataColumns"]!!.jsonArray.mapNotNull {
        it as JsonObject
        val fieldCaption = it["fieldCaption"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val paneIndex = it["paneIndices"]!!.jsonArray[0].jsonPrimitive.int
        val columnIndex = it["columnIndices"]!!.jsonArray[0].jsonPrimitive.int
        val vizPaneColumn = columnsData["paneColumnsList"]!!.jsonArray[paneIndex].jsonObject["vizPaneColumns"]!!
            .jsonArray[columnIndex].jsonObject
        val valueIndices = vizPaneColumn["valueIndices"]!!.jsonArray.map { it.jsonPrimitive.int }
        val aliasIndices = vizPaneColumn["aliasIndices"]!!.jsonArray.map { it.jsonPrimitive.int }
        val dataType = it["dataType"]?.jsonPrimitive?.content.orEmpty()
        val fn = it["fn"]?.jsonPrimitive?.content.orEmpty()

        LimitedIndicesInfo(
            fieldCaption = fieldCaption,
            valueIndices = valueIndices,
            aliasIndices = aliasIndices,
            dataType = dataType,
            fn = fn,
        )
    }
    return getData(dataFull, result)
}

internal fun hasVizData(zone: JsonObject): Boolean {
    return "vizData" in zone["presModelHolder"]?.jsonObject?.get("visual")?.jsonObject.orEmpty()
}

private inline fun getParameterControl(
    getData: () -> JsonObject,
    altData: (JsonObject) -> Collection<JsonElement>,
): List<ParameterInfo> {
    val presModel = getData()
    return listWorksheetStoryPoint(presModel, hasWorksheet = false)
        .ifEmpty { altData(presModel) }
        .mapNotNull {
            it.jsonObject["presModelHolder"]?.jsonObject?.get("parameterControl")?.jsonObject?.let {
                ParameterInfo(
                    column = it["fieldCaption"]!!.jsonPrimitive.content,
                    values = it["formattedValues"]!!.jsonArray.map { it.jsonPrimitive.content },
                    parameterName = it["parameterName"]!!.jsonPrimitive.content,
                )
            }
        }
}

internal fun getParameterControlInput(info: JsonObject): List<ParameterInfo> =
    getParameterControl({ getPresModelVizInfo(info)!! }, { getZones(it)!!.values })


internal fun getParameterControlVqlResponse(presModel: JsonObject): List<ParameterInfo> =
    getParameterControl({ getZones(presModel)!! }, { it.values })

internal fun getStoryPointsFromInfo(info: JsonObject): StoryPointHolder {
    val storyBoard = info["sheetName"]?.jsonPrimitive?.content.orEmpty()
    val result = StoryPointHolder(storyBoard, emptyList())

    if ("sheetName" !in info) {
        println("sheet name not found")
        return result
    }

    val zones = getZones(getPresModelVizInfo(info)!!) ?: return result
    val storyPointsList = zones.values.mapNotNull { zone ->
        val t = zone.jsonObject["presModelHolder"]?.jsonObject?.get("flipboardNav")?.jsonObject
            ?.get("storypointNavItems")
            ?: return@mapNotNull null
        t.jsonArray.map {
            StoryPointEntry(
                it.jsonObject["storyPointId"]!!.jsonPrimitive.int,
                it.jsonObject["storyPointCaption"]!!.jsonPrimitive.content
            )
        }
    }

    return StoryPointHolder(storyBoard, storyPointsList)
}

internal fun getWorksheetDownloadCmdResponse(
    dataFull: JsonObject,
    underlyingDataTableColumns: JsonArray,
): Map<String, JsonArray> {
    val result = underlyingDataTableColumns.mapNotNull { t ->
        t as JsonObject
        t["fieldCaption"]?.let {
            LimitedIndicesInfo(
                fieldCaption = it.jsonPrimitive.content,
                valueIndices = t["valueIndices"]!!.jsonArray.map { it.jsonPrimitive.int },
                aliasIndices = t["aliasIndices"]!!.jsonArray.map { it.jsonPrimitive.int },
                dataType = t["dataType"]!!.jsonPrimitive.content,
                fn = t["fn"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
    }
    return getData(dataFull, result)
}

internal fun getTooltipText(tooltipServerCmdResponse: JsonObject): String {
    val tooltipText = tooltipServerCmdResponse["vqlCmdResponse"]!!.jsonObject["cmdResultList"]!!.jsonArray[0]
        .jsonObject["commandReturn"]!!.jsonObject["tooltipText"]!!.jsonPrimitive.content

    return if (tooltipText.isNotEmpty()) {
        Json.parseToJsonElement(tooltipText).jsonObject["htmlTooltip"]!!.jsonPrimitive.content
    } else ""
}