package io.github.opletter.tableau

import kotlinx.serialization.json.*
import org.jetbrains.kotlinx.dataframe.DataFrame

class TableauWorksheet(
    private val scraper: Scraper,
    private val originalData: JsonObject,
    private val originalInfo: JsonObject,
    val name: String,
    dataFrame: DataFrame<*>,
    dataFull: JsonObject,
    internal val cmdResponse: Boolean = false,
) {
    val data = dataFrame
    private val dataDictionary = dataFull

    fun getSelectableItems(): List<JsonObject> {
        val indices = if (cmdResponse) {
            val presModel = originalData["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!
                .jsonObject["applicationPresModel"]!!.jsonObject
            getIndicesInfoVqlResponse(presModel, name, noSelectFilter = true)
                .ifEmpty { getIndicesInfoStoryPoint(presModel, name, noSelectFilter = true) }
        } else {
            getPresModelVizData(originalData)
                ?.let { getIndicesInfo(it, name, noSelectFilter = true) }
                ?: getIndicesInfoStoryPoint(getPresModelVizInfo(originalInfo)!!, name, noSelectFilter = true)
        }
        return indices.map { index ->
            buildJsonObject {
                put("column", index["fieldCaption"]!!.jsonPrimitive.content)
                put(
                    "values",
                    getData(dataDictionary, listOf(index)).values.firstOrNull() ?: JsonArray(emptyList())
                )
            }
        }
    }

    fun getSelectableValues(column: String): JsonArray {
        val columnObj = if (cmdResponse) {
            val presModel = originalData["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!
                .jsonObject["applicationPresModel"]!!.jsonObject
            getIndicesInfoVqlResponse(presModel, name, noSelectFilter = true)
                .filter { it["fieldCaption"]!!.jsonPrimitive.content == column }
                .ifEmpty { getIndicesInfoStoryPoint(presModel, name, noSelectFilter = true) }
        } else {
            val presModel = getPresModelVizData(originalData)
                ?: getPresModelVizInfo(originalInfo)!!

            if (presModel == getPresModelVizData(originalData))
                getIndicesInfo(presModel, name, noSelectFilter = true)
            else getIndicesInfoStoryPoint(presModel, name, noSelectFilter = true)
        }
        return columnObj.firstOrNull { it["fieldCaption"]!!.jsonPrimitive.content == column }
            ?.let { getData(dataDictionary, listOf(it)).values.firstOrNull() }
            ?: JsonArray(emptyList())
    }

    fun getTupleIds(): List<List<Int>> {
        val data = if (cmdResponse) {
            val presModel = originalData["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!
                .jsonObject["applicationPresModel"]!!.jsonObject
            getIndicesInfoVqlResponse(
                presModel,
                name,
                noSelectFilter = true,
                noFieldCaption = true
            )
        } else {
            getIndicesInfo(
                getPresModelVizData(originalData)!!,
                name,
                noSelectFilter = true,
                noFieldCaption = true
            )
        }
        return data
            .filter { it["fn"]!!.jsonPrimitive.content == "[system:visual].[tuple_id]" }
            .map { t -> t["tupleIds"]!!.jsonArray.map { it.jsonPrimitive.int } }
    }

    suspend fun select(column: String, value: String): TableauWorkbook {
        val values = getSelectableValues(column)
        val tupleItems = getTupleIds()

        val index: Int = try {
            var indexedByTuple = false
            var foundIndex = -1

            for (tupleItem in tupleItems) {
                if (tupleItem.size >= values.size) {
                    foundIndex = values.indexOfFirst { it.jsonPrimitive.content == value }.takeIf { it != -1 }
                        ?: throw NoSuchElementException("Value not found in column")
                    foundIndex = tupleItem[foundIndex]
                    indexedByTuple = true
                    break
                }
            }

            if (!indexedByTuple) {
                foundIndex = (values.indexOfFirst { it.jsonPrimitive.content == value }.takeIf { it != -1 }
                    ?: throw NoSuchElementException("Value not found in column")) + 1
            }
            foundIndex
        } catch (e: NoSuchElementException) {
            e.printStackTrace()
            return TableauWorkbook(scraper, JsonObject(emptyMap()), JsonObject(emptyMap()), emptyList())
        }

        val r = scraper.select(name, listOf(index))
        updateFullData(r)
        return Dashboard.getWorksheetsCmdResponse(scraper, r)
    }

    private fun updateFullData(cmdResponse: JsonObject) {
        val applicationPresModel = cmdResponse["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!
            .jsonObject["applicationPresModel"]?.jsonObject

        // Update data dictionary if present
        val dataDictionary = applicationPresModel?.get("dataDictionary")?.jsonObject
        if (applicationPresModel != null && dataDictionary != null) {
            val dataSegments = dataDictionary.jsonObject["dataSegments"]?.jsonObject
            if (dataSegments != null) {
                val dataSegmentscp = dataSegments.toMutableMap()
                for (key in dataSegmentscp.keys) {
                    if (dataSegmentscp[key] != null) {
                        val a = dataSegments.toMutableMap()
                        a[key] = dataSegmentscp[key]!!
                        scraper.dataSegments = JsonObject(scraper.dataSegments + a)
                    }
                }
            } else {
                println("no data dictionary present in response3")
            }
        } else {
            val dataSegments = cmdResponse["vqlCmdResponse"]!!.jsonObject["cmdResultList"]?.jsonArray?.getOrNull(0)
                ?.jsonObject?.get("commandReturn")?.jsonObject?.get("underlyingDataTable")?.jsonObject
                ?.get("dataDictionary")?.jsonObject?.get("dataSegments")?.jsonObject
            if (dataSegments != null) {
                val dataSegmentscp = dataSegments.toMutableMap()
                for (key in dataSegmentscp.keys) {
                    if (dataSegmentscp[key] != null) {
                        val a = dataSegments.toMutableMap()
                        a[key] = dataSegmentscp[key]!!
                        scraper.dataSegments = JsonObject(scraper.dataSegments + a)
                    }
                }
            } else println("no data dictionary present in response4")
        }

        // Update filters if present
        if (applicationPresModel != null) {
            val newFilters = getFiltersForAllWorksheet(
                data = cmdResponse,
                info = JsonObject(emptyMap()),
                rootDashboard = scraper.dashboard,
                cmdResponse = true
            )
            newFilters.forEach { (ws, newFilters) ->
                val scraperWorksheetFilters = scraper.filters[ws] ?: mutableListOf()
                newFilters.forEach { newFilter ->
                    val foundFilterIndex = scraperWorksheetFilters
                        .indexOfFirst { it["globalFieldName"] == newFilter["globalFieldName"] }
                    if (foundFilterIndex != -1) {
                        scraperWorksheetFilters.removeAt(foundFilterIndex)
                    }
                    scraperWorksheetFilters.add(newFilter)
                }
                scraper.filters[ws] = scraperWorksheetFilters
            }
        }

        // Persist zones
        val newZones = applicationPresModel?.let {
            getZones(it)?.filterNotNullValues()?.mapValues { (key, value) ->
                val zoneHasVizData = hasVizData(value.jsonObject)
                val newValue = if (!zoneHasVizData && key in scraper.zones) scraper.zones[key] else value
                newValue!!.jsonObject.deepCopy()
            }
        }.orEmpty()
        scraper.zones = JsonObject(newZones)
    }


    fun getColumns(): List<String> {
        val presModel = if (cmdResponse) {
            originalData["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!.jsonObject["applicationPresModel"]!!.jsonObject
        } else {
            getPresModelVizData(originalData)!!
        }

        return if (cmdResponse) {
            getIndicesInfoVqlResponse(presModel, name, noSelectFilter = true)
        } else {
            getIndicesInfo(presModel, name, noSelectFilter = true)
        }.map { it["fieldCaption"]!!.jsonPrimitive.content }
    }

    fun getFilters(): List<JsonObject> {
        return scraper.filters[name].orEmpty()
    }

    suspend fun setFilter(
        columnName: String,
        value: Any,
        dashboardFilter: Boolean = false,
        membershipTarget: Boolean = true,
        filterDelta: Boolean = false,
        indexValues: List<Int>? = null,
        noCheck: Boolean = false,
    ): TableauWorkbook {
        return try {
            val r = if ((!noCheck && dashboardFilter) || !dashboardFilter) {
                val filter = getFilters().firstOrNull { it["column"]!!.jsonPrimitive.content == columnName }
                    ?: return TableauWorkbook(scraper, JsonObject(emptyMap()), JsonObject(emptyMap()), emptyList())
                        .also { println("column $columnName not found") }

                val indices = indexValues ?: run {
                    val valuesList = if (value !is List<*>) listOf(value) else value
                    valuesList.map { value ->
                        filter["values"]!!.jsonArray.indexOfFirst { it.jsonPrimitive.content == value.toString() }
                    }.takeIf { i -> i.none { it == -1 } } ?: throw Exception("value $value not found")
                }

                // Note: the original code gets these indices among the selections themselves, but from what I can tell
                // they should be the indices from "values", so that's what's used here
                val selectedIndex = when {
                    filter["selection"]!!.jsonArray.isNotEmpty() -> {
                        filter["selection"]!!.jsonArray.map { selection ->
                            filter["values"]!!.jsonArray.indexOfFirst { it == selection }
                        }
                    }

                    else -> {
                        val data = filter["selectionAlt"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("domainTables")
                            ?.jsonArray.orEmpty()
                        data.filter { it.jsonObject["isSelected"]?.jsonPrimitive?.booleanOrNull == true }
                            .map { selection ->
                                filter["values"]!!.jsonArray.indexOfFirst { it == selection }
                            }
                    }
                }

                if (dashboardFilter) {
                    scraper.dashboardFilter(columnName, if (value is List<*>) value else listOf(value))
                } else {
                    val toRemove = if (!filterDelta) emptyList() else selectedIndex.distinct() - (indices.toSet() + -1)
                    // Note: this check isn't in the original code, but it seems useful to prevent some crashes
                    if (filterDelta && indices.isEmpty() && toRemove.isEmpty()) {
                        return scraper.getWorkbook()
                    }
                    scraper.filter(
                        worksheetName = name,
                        globalFieldName = filter["globalFieldName"]!!.jsonPrimitive.content,
                        selection = indices,
                        selectionToRemove = toRemove,
                        membershipTarget = membershipTarget,
                        filterDelta = filterDelta,
                        storyboard = filter["storyboard"]?.jsonPrimitive?.content,
                        storyboardId = filter["storyboardId"]?.jsonPrimitive?.content,
                        dashboard = filter["dashboard"]?.jsonPrimitive?.content ?: scraper.dashboard
                    )
                }
            } else { // dashboardFilter must be true
                scraper.dashboardFilter(columnName, if (value is List<*>) value else listOf(value))
            }
            updateFullData(r)
            Dashboard.getWorksheetsCmdResponse(scraper, r)
        } catch (e: Exception) {
            e.printStackTrace()
            TableauWorkbook(scraper, JsonObject(emptyMap()), JsonObject(emptyMap()), emptyList())
        }
    }

    @Suppress("unused") // public API
    suspend fun clearFilter(columnName: String): TableauWorkbook {
        return try {
            val filter = getFilters().firstOrNull { it["column"]!!.jsonPrimitive.content == columnName }
                ?: return TableauWorkbook(scraper, JsonObject(emptyMap()), JsonObject(emptyMap()), emptyList())
                    .also { println("column $columnName not found") }

            val r = scraper.clearFilter(
                worksheetName = name,
                globalFieldName = filter["globalFieldName"]!!.jsonPrimitive.content,
                dashboard = filter["dashboard"]?.jsonPrimitive?.content ?: scraper.dashboard
            )
            updateFullData(r)
            Dashboard.getWorksheetsCmdResponse(scraper, r)
        } catch (e: Exception) {
            e.printStackTrace()
            TableauWorkbook(scraper, JsonObject(emptyMap()), JsonObject(emptyMap()), emptyList())
        }
    }

    suspend fun getDownloadableSummaryData(numRows: Int = 200): DataFrame<*> {
        val response = scraper.getDownloadableSummaryData(name, scraper.dashboard, numRows)
        updateFullData(response)
        return Dashboard.getWorksheetDownloadCmdResponse(scraper, response)
    }

    suspend fun getDownloadableUnderlyingData(numRows: Int = 200): DataFrame<*> {
        val response = scraper.getDownloadableUnderlyingData(name, scraper.dashboard, numRows)
        updateFullData(response)
        return Dashboard.getWorksheetDownloadCmdResponse(scraper, response)
    }

    suspend fun levelDrill(drillDown: Boolean, position: Int = 0): TableauWorkbook {
        val response = scraper.levelDrill(name, drillDown, position)
        updateFullData(response)
        return Dashboard.getWorksheetsCmdResponse(scraper, response)
    }

    suspend fun renderTooltip(x: Int, y: Int): String {
        val response = scraper.renderTooltipServer(name, x, y)
        return getTooltipText(response)
    }
}