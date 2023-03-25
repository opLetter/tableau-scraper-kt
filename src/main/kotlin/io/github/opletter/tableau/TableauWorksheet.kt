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
        return indices.map { t ->
            buildJsonObject {
                put("column", t["fieldCaption"]!!.jsonPrimitive.content)
                put(
                    "values",
                    getData(dataDictionary, listOf(t)).values.firstOrNull() ?: JsonArray(emptyList())
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
        val layoutStatus = cmdResponse["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!.jsonObject
        val applicationPresModel = layoutStatus["applicationPresModel"] as JsonObject?

        // Update data dictionary if present
        if (applicationPresModel != null && "dataDictionary" in applicationPresModel) {
            val presModel = layoutStatus["applicationPresModel"]!!.jsonObject
            if ("dataSegments" in presModel["dataDictionary"]!!.jsonObject) {
                val dataSegments = presModel["dataDictionary"]!!.jsonObject["dataSegments"]!!.jsonObject
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

        // Update parameters if present
//        scraper.parameters = getParameters() // ?
        if (applicationPresModel != null) {
            val newParameters = getParameterControlVqlResponse(applicationPresModel)
            val newParameterscsp = newParameters.toMutableList()
            for (newParam in newParameterscsp) {
                var found = false
                for (param in scraper.parameters) {
                    if (newParam["parameterName"]!!.jsonPrimitive.content == param["parameterName"]!!.jsonPrimitive.content) {
                        found = true
                    }
                }
                if (!found) {
                    scraper.parameters.add(newParam)
                }
            }
        }

        // Update filters if present
        if (applicationPresModel != null) {
            val newFilters = getFiltersForAllWorksheet(
                data = cmdResponse,
                info = JsonObject(emptyMap()),
                rootDashboard = scraper.dashboard,
                cmdResponse = true
            )
            val newFilterscsp = newFilters.toMutableMap()
            for ((worksheet, filters) in newFilterscsp) {
                if (worksheet !in scraper.filters) {
                    scraper.filters[worksheet] = filters.toMutableList()
                } else {
                    for (newFilter in filters) {
                        var found = false
                        var foundFilterIndex = -1
                        for ((idx, filter) in scraper.filters[worksheet]!!.withIndex()) {
                            if (newFilter["globalFieldName"]!!.jsonPrimitive.content == filter["globalFieldName"]!!.jsonPrimitive.content) {
                                found = true
                                foundFilterIndex = idx
                            }
                        }
                        if (!found) {
                            scraper.filters[worksheet]!!.add(newFilter)
                        } else {
                            scraper.filters[worksheet]!!.removeAt(foundFilterIndex)
                            scraper.filters[worksheet]!!.add(newFilter)
                        }
                    }
                }
            }
        }

        // Persist zones
        if (applicationPresModel != null) {
            val newZones = getZones(applicationPresModel) ?: JsonObject(emptyMap())
            val newZonesStorage = mutableMapOf<String, JsonElement>()
            for ((zone, value) in newZones) {
                val zoneHasVizdata = hasVizData(value.jsonObject)
                if (!zoneHasVizdata && zone in scraper.zones) {
                    newZonesStorage[zone] = scraper.zones[zone]!!.deepCopy()
                } else {
                    newZonesStorage[zone] = value.deepCopy()
                }
            }
            scraper.zones = JsonObject(newZonesStorage)
        } else {
            scraper.zones = JsonObject(emptyMap())
        }
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
        indexValues: List<Int> = emptyList(),
        noCheck: Boolean = false,
    ): TableauWorkbook {
        return try {
            val r = if ((!noCheck && dashboardFilter) || !dashboardFilter) {
                val filter = getFilters().firstOrNull { it["column"]!!.jsonPrimitive.content == columnName }
                    ?: return TableauWorkbook(scraper, JsonObject(emptyMap()), JsonObject(emptyMap()), emptyList())
                        .also { println("column $columnName not found") }

                val indices = indexValues.ifEmpty {
                    if (value !is List<*>) {
                        listOf(filter["values"]!!.jsonArray.map { it.jsonPrimitive.content }
                            .indexOf(value.toString()))
                            .takeIf { i -> i.none { it == -1 } } ?: throw Exception("value $value not found")
                    } else {
                        value.map {
                            filter["values"]!!.jsonArray.map { it.jsonPrimitive.content }.indexOf(value.toString())
                        }.takeIf { i -> i.none { it == -1 } } ?: throw Exception("value $value not found")
                    }
                }

                val selectedIndex = when {
                    filter["selection"]!!.jsonArray.isNotEmpty() -> {
                        filter["selection"]!!.jsonArray
                            .withIndex()
                            .filter { value != it.value }
                            .map { it.index }
                    }

                    else -> {
                        val data = filter["selectionAlt"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("domainTables")
                            ?: JsonArray(emptyList())
                        data.jsonArray
                            .withIndex()
                            .filter { it.value.jsonObject["isSelected"]?.jsonPrimitive?.booleanOrNull == true }
                            .map { it.index }
                    }
                }

                if (dashboardFilter) {
                    scraper.dashboardFilter(columnName, if (value is List<*>) value else listOf(value))
                } else {
                    scraper.filter(
                        worksheetName = name,
                        globalFieldName = filter["globalFieldName"]!!.jsonPrimitive.content,
                        selection = indices,
                        selectionToRemove = if (!filterDelta) emptyList() else selectedIndex,
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
            return Dashboard.getWorksheetsCmdResponse(scraper, r)
        } catch (e: Exception) {
            println(e.message)
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