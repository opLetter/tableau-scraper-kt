package io.github.opletter.tableau

import kotlinx.serialization.decodeFromString
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

private fun processFilters(filters: List<List<JsonObject>>, selectedFilters: List<JsonObject>): List<JsonObject> {
    return filters.flatMap { arr ->
        arr.filter { t ->
            t["table"]?.jsonObject?.let { "schema" in it && "tuples" in it } == true
        }.flatMap { t ->
            val table = t["table"]!!.jsonObject
            val columns = table["schema"]!!.jsonArray.map { z ->
                Triple(
                    z.jsonObject["caption"]!!.jsonPrimitive.content,
                    z.jsonObject["name"]!!.jsonArray,
                    z.jsonObject["ordinal"]!!.jsonPrimitive.content
                )
            }
            val values = table["tuples"]!!.jsonArray.mapNotNull { z ->
                val x = z.jsonObject["t"]?.jsonArray?.getOrNull(0) ?: return@mapNotNull null
                x.jsonObject["v"]!!.jsonPrimitive.content
            }
            val selection = table["tuples"]!!.jsonArray.mapNotNull { z ->
                val x = z.jsonObject["t"]?.jsonArray?.getOrNull(0)
                if (x != null && x.jsonObject["s"]?.jsonPrimitive?.booleanOrNull == true) {
                    x.jsonObject["v"]!!.jsonPrimitive.content
                } else null
            }
            columns.map { c ->
                val globalFieldName = "[${c.second[0].jsonPrimitive.content}].[${c.second[1].jsonPrimitive.content}]"
                val selectionAlt = selectedFilters.filter { it["fn"]!!.jsonPrimitive.content == globalFieldName }
                buildJsonObject {
                    put("column", c.first)
                    put("ordinal", c.third)
                    putJsonArray("values") {
                        values.forEach { add(it) }
                    }
                    put("globalFieldName", globalFieldName)
                    putJsonArray("selection") {
                        val selections = if (
                            t["all"]?.jsonPrimitive?.booleanOrNull == true ||
                            t["allChecked"]?.jsonPrimitive?.booleanOrNull == true
                        ) values + "all" else selection
                        selections.forEach { add(it) }
                    }
                    putJsonArray("selectionAlt") {
                        selectionAlt.forEach { add(it) }
                    }
                }
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
    val filters = getFilterValues(zones, worksheetName)
    if (filters.isNotEmpty()) return processFilters(filters, selectedFilters)

    val storypoint = zones.values.firstNotNullOfOrNull { z ->
        z.jsonObject["presModelHolder"]?.jsonObject?.get("flipboard")?.jsonObject?.get("storyPoints")?.jsonObject
    } ?: return emptyList()

    return storypoint.values.flatMap { i ->
        val storyboardId = i.jsonObject["storyPointId"]!!.jsonPrimitive.content
        val dashboardPresModel = i.jsonObject["dashboardPresModel"]!!.jsonObject

        val (storyboard, dashboard) = if ("sheetPath" in dashboardPresModel) {
            val sheetPath = dashboardPresModel["sheetPath"]!!.jsonObject
            val storyboard = sheetPath["storyboard"]!!.jsonPrimitive.content
            val dashboard = if (sheetPath["isDashboard"]!!.jsonPrimitive.boolean)
                sheetPath["sheetName"]!!.jsonPrimitive.content
            else rootDashboard
            storyboard to dashboard
        } else if ("visualIds" in dashboardPresModel) {
            val visualIds = dashboardPresModel["visualIds"]!!
            val k = if (visualIds is JsonArray) visualIds[0] else visualIds
            val storyboard = k.jsonObject["storyboard"]!!.jsonPrimitive.content
            val dashboard = k.jsonObject["dashboard"]!!.jsonPrimitive.content

            storyboard to dashboard
        } else {
            println("sheetPath and visualIds not found in dashboardPresModel")
            return emptyList()
        }

        val zones2 = dashboardPresModel["zones"]!!.jsonObject
        val filters2 = getFilterValues(zones2, worksheetName)

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
        val worksheets = presModelMapVizData?.let(::listWorksheet)
            ?: listWorksheetInfo(presModelMapVizInfo).ifEmpty { listStoryPointsInfo(presModelMapVizInfo) }

        presModelMapVizInfo.let { presModel ->
            worksheets.associateWith { worksheet ->
                listFilters(presModel, worksheet, getSelectedFilters(presModel, worksheet), rootDashboard)
            }
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
                it["presModelHolder"]?.jsonObject?.get("visual")?.jsonObject?.containsKey("vizData") == true
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
    return data["secondaryInfo"]?.jsonObject?.get("presModelMap")?.jsonObject?.takeIf {
        it.containsKey("vizData")
    }
}

internal fun getPresModelVizDataWithoutViz(data: JsonObject): JsonObject? {
    return data["secondaryInfo"]?.jsonObject?.get("presModelMap")?.jsonObject
}

internal fun getPresModelVizInfo(info: JsonObject): JsonObject? {
    return info["worldUpdate"]?.jsonObject?.get("applicationPresModel")?.jsonObject?.takeIf {
        it.containsKey("workbookPresModel")
    }
}

internal fun listWorksheetCmdResponse(presModel: JsonObject): List<JsonObject> {
    return getZones(presModel)?.filter { (_, zone) ->
        zone.jsonObject["presModelHolder"]?.jsonObject?.get("visual")?.jsonObject?.containsKey("vizData") == true
    }?.map { it.value.jsonObject } ?: emptyList()
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
    val zones = getZones(presModel)!!
    return zones.values.mapNotNull { z ->
        z.jsonObject.takeIf {
            "presModelHolder" in it && "visual" in it["presModelHolder"]!!.jsonObject
        }?.get("worksheet")?.jsonPrimitive?.content
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
): List<JsonObject> {
    val genVizDataPresModel = presModelMap["vizData"]!!
        .jsonObject["presModelHolder"]!!
        .jsonObject["genPresModelMapPresModel"]!!
        .jsonObject["presModelMap"]!!
        .jsonObject[worksheet]!!
        .jsonObject["presModelHolder"]!!
        .jsonObject["genVizDataPresModel"]!!
        .jsonObject

    if ("paneColumnsData" !in genVizDataPresModel) return emptyList()

    val columnsData = genVizDataPresModel["paneColumnsData"]!!.jsonObject

    return columnsData["vizDataColumns"]!!.jsonArray.flatMap {
        it as JsonObject
        val hasFieldCaption = "fieldCaption" in it
        val isAutoSelect = it["isAutoSelect"]?.jsonPrimitive?.boolean == true

        if ((hasFieldCaption || noFieldCaption) && (noSelectFilter || isAutoSelect)) {
            it["paneIndices"]!!.jsonArray.mapIndexed { index, jsonElement ->
                val paneIndex = jsonElement.jsonPrimitive.int
                val columnIndex = it["columnIndices"]!!.jsonArray[index].jsonPrimitive.int
                val paneColumnsList = columnsData["paneColumnsList"]!!.jsonArray[paneIndex]
                    .jsonObject["vizPaneColumns"]!!.jsonArray[columnIndex].jsonObject

                buildJsonObject {
                    put("fieldCaption", it["fieldCaption"]?.jsonPrimitive?.content.orEmpty())
                    put("tupleIds", paneColumnsList["tupleIds"]!!)
                    put("valueIndices", paneColumnsList["valueIndices"]!!)
                    put("aliasIndices", paneColumnsList["aliasIndices"]!!)
                    put("dataType", it["dataType"]?.jsonPrimitive?.content.orEmpty())
                    put("paneIndices", paneIndex)
                    put("columnIndices", columnIndex)
                    put("fn", it["fn"]?.jsonPrimitive?.content.orEmpty())
                }
            }
        } else emptyList()
    }
}

private fun getIndicesInfoSpecial(
    zones: List<JsonObject>,
    worksheet: String,
    noSelectFilter: Boolean = true,
    noFieldCaption: Boolean = false,
): List<JsonObject> {
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
            val tupleIds = columnsData["paneColumnsList"]!!.jsonArray[paneIndex].jsonObject["vizPaneColumns"]!!
                .jsonArray[columnIndex].jsonObject["tupleIds"]!!
            val valueIndices = columnsData["paneColumnsList"]!!.jsonArray[paneIndex].jsonObject["vizPaneColumns"]!!
                .jsonArray[columnIndex].jsonObject["valueIndices"]!!
            val aliasIndices = columnsData["paneColumnsList"]!!.jsonArray[paneIndex].jsonObject["vizPaneColumns"]!!
                .jsonArray[columnIndex].jsonObject["aliasIndices"]!!

            buildJsonObject {
                put("fieldCaption", fieldCaption?.jsonPrimitive?.content.orEmpty())
                put("tupleIds", tupleIds)
                put("valueIndices", valueIndices)
                put("aliasIndices", aliasIndices)
                put("dataType", it["dataType"]?.jsonPrimitive?.content.orEmpty())
                put("paneIndices", paneIndex)
                put("columnIndices", columnIndex)
                put("fn", it["fn"]?.jsonPrimitive?.content.orEmpty())
            }
        } else null
    }
}

internal fun getIndicesInfoStoryPoint(
    presModel: JsonObject,
    worksheet: String,
    noSelectFilter: Boolean = true,
    noFieldCaption: Boolean = false,
): List<JsonObject> =
    getIndicesInfoSpecial(listWorksheetStoryPoint(presModel), worksheet, noSelectFilter, noFieldCaption)

internal fun listWorksheetStoryPoint(
    presModel: JsonObject,
    hasWorksheet: Boolean = true,
    scraper: Scraper? = null,
): List<JsonObject> {
    val zones = getZones(presModel) ?: return emptyList()

    val actualZones = scraper?.zones ?: zones
    val storyPoint = actualZones.values.firstNotNullOfOrNull {
        it.jsonObject["presModelHolder"]?.jsonObject?.get("flipboard")?.jsonObject?.get("storyPoints")?.jsonObject
    }?.values?.firstOrNull() ?: return emptyList()

    val innerZones = storyPoint.jsonObject["dashboardPresModel"]!!.jsonObject["zones"]!!.jsonObject

    return innerZones.values.mapNotNull { innerZone ->
        if (hasWorksheet) {
            val presModelHolder = innerZone.jsonObject["presModelHolder"]?.jsonObject
            val visual = presModelHolder?.get("visual")?.jsonObject
            val vizData = visual?.get("vizData")
            if (vizData != null) innerZone.jsonObject else null
        } else innerZone.jsonObject.takeIf { "presModelHolder" in it }
    }
}

private fun getDataFullHelper(dataSegments: JsonObject, originSegments: JsonObject): JsonObject {
    // probably not necessary
    val dataSegmentscp = JsonObject(dataSegments).deepCopy()
    val originSegmentscp = originSegments.deepCopy()

    val a = originSegmentscp.filterNotNullValues().flatMap { it.value.jsonObject["dataColumns"]!!.jsonArray }
    val b = dataSegmentscp.filterNot { it.key in originSegmentscp.keys || it.value == JsonNull }
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

internal fun getData(dataFull: JsonObject, indicesInfo: List<JsonObject>): Map<String, JsonArray> {
    val cstring = dataFull["cstring"]?.jsonArray ?: JsonArray(emptyList())

    fun processIndices(index: JsonObject, suffix: String, useFn: Boolean): Pair<String, JsonArray>? {
        val t = dataFull[index["dataType"]!!.jsonPrimitive.content]?.jsonArray ?: cstring
        return index["${suffix}Indices"]!!.jsonArray.mapNotNull { idx ->
            val intValue = idx.jsonPrimitive.int
            if (intValue < t.size) onDataValue(intValue, t, cstring) else null
        }.takeIf { it.isNotEmpty() }?.let { arr ->
            val fn = index["fn"]!!.jsonPrimitive.content.takeIf { useFn && it.isNotEmpty() }?.let { "-$it" }.orEmpty()
            val key = "${index["fieldCaption"]!!.jsonPrimitive.content}$fn-$suffix"
            key to JsonArray(arr)
        }
    }
    // original code handles case with multiple indicesInfo with same fieldCaption by adding fn
    // This should do the same thing, but by handling duplicates at same time
    return indicesInfo
        .groupBy { it["fieldCaption"]!!.jsonPrimitive.content }
        .flatMap { (_, indices) ->
            val a = indices.filter { it["valueIndices"]!!.jsonArray.isNotEmpty() }
            val b = indices.filter { it["aliasIndices"]!!.jsonArray.isNotEmpty() }
            listOfNotNull(
                a.firstOrNull()?.let { processIndices(it, "value", false) },
                b.firstOrNull()?.let { processIndices(it, "alias", false) },
                (if (a.size > 1) a.last() else null)?.let { processIndices(it, "value", true) },
                (if (b.size > 1) b.last() else null)?.let { processIndices(it, "alias", true) },
            )
        }.toMap()
}

internal fun getIndicesInfoVqlResponse(
    presModel: JsonObject,
    worksheet: String,
    noSelectFilter: Boolean = true,
    noFieldCaption: Boolean = false,
): List<JsonObject> =
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

    if ("paneColumnsData" !in details) {
        return null
    }

    val columnsData = details["paneColumnsData"]!!.jsonObject
    val result = columnsData["vizDataColumns"]!!.jsonArray.mapNotNull {
        it as JsonObject
        val fieldCaption = it["fieldCaption"]?.jsonPrimitive?.content.orEmpty()

        val paneIndex = it["paneIndices"]!!.jsonArray[0].jsonPrimitive.int
        val columnIndex = it["columnIndices"]!!.jsonArray[0].jsonPrimitive.int
        val valueIndices = columnsData["paneColumnsList"]!!.jsonArray[paneIndex].jsonObject["vizPaneColumns"]!!
            .jsonArray[columnIndex].jsonObject["valueIndices"]!!
        val aliasIndices = columnsData["paneColumnsList"]!!.jsonArray[paneIndex].jsonObject["vizPaneColumns"]!!
            .jsonArray[columnIndex].jsonObject["aliasIndices"]!!
        val dataType = it["dataType"]?.jsonPrimitive?.content.orEmpty()
        val fn = it["fn"]?.jsonPrimitive?.content.orEmpty()

        buildJsonObject {
            put("fieldCaption", fieldCaption)
            put("valueIndices", valueIndices)
            put("aliasIndices", aliasIndices)
            put("dataType", dataType)
            put("paneIndices", paneIndex)
            put("columnIndices", columnIndex)
            put("fn", fn)
        }
    }
    return getData(dataFull, result)
}

internal fun hasVizData(zone: JsonObject): Boolean {
    return zone["presModelHolder"]?.jsonObject?.get("visual")?.jsonObject?.get("vizData")?.jsonObject != null
}

private inline fun getParameterControl(
    getData: () -> JsonObject,
    altData: (JsonObject) -> Collection<JsonElement>,
): List<JsonObject> {
    val presModel = getData()
    return listWorksheetStoryPoint(presModel, hasWorksheet = false)
        .ifEmpty { altData(presModel) }
        .mapNotNull {
            it.jsonObject["presModelHolder"]?.jsonObject?.get("parameterControl")?.jsonObject?.let {
                buildJsonObject {
                    put("column", it["fieldCaption"]!!)
                    put("values", it["formattedValues"]!!)
                    put("parameterName", it["parameterName"]!!)
                }
            }
        }
}

internal fun getParameterControlInput(info: JsonObject): List<JsonObject> =
    getParameterControl({ getPresModelVizInfo(info)!! }, { getZones(it)!!.values })


internal fun getParameterControlVqlResponse(presModel: JsonObject): List<JsonObject> =
    getParameterControl({ getZones(presModel)!! }, { it.values })

internal fun getStoryPointsFromInfo(info: JsonObject): JsonObject {
    val storyBoard = info["sheetName"] ?: JsonPrimitive("")
    val result = buildJsonObject {
        put("storyBoard", storyBoard)
        put("storyPoints", JsonArray(emptyList()))
    }

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
            buildJsonObject {
                put("storyPointId", it.jsonObject["storyPointId"]!!)
                put("storyPointCaption", it.jsonObject["storyPointCaption"]!!)
            }
        }.let { JsonArray(it) }
    }

    return buildJsonObject {
        put("storyBoard", storyBoard)
        put("storyPoints", JsonArray(storyPointsList))
    }
}

internal fun getWorksheetDownloadCmdResponse(
    dataFull: JsonObject,
    underlyingDataTableColumns: JsonArray,
): Map<String, JsonArray> {
    val result = underlyingDataTableColumns.mapNotNull { t ->
        t as JsonObject
        t["fieldCaption"]?.let {
            buildJsonObject {
                put("fieldCaption", it)
                put("valueIndices", t["valueIndices"]!!)
                put("aliasIndices", t["aliasIndices"]!!)
                put("dataType", t["dataType"]!!)
                put("fn", t["fn"] ?: JsonPrimitive(""))
            }
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