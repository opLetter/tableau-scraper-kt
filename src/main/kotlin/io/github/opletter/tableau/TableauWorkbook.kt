package io.github.opletter.tableau


import kotlinx.serialization.json.*
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.io.readCSV

class TableauWorkbook(
    private val scraper: Scraper,
    private val originalData: JsonObject,
    private val originalInfo: JsonObject,
    val worksheets: List<TableauWorksheet>,
    internal val cmdResponse: Boolean = false,
) {

    fun getWorksheetNames(): List<String> {
        return worksheets.map { it.name }
    }

    fun getWorksheet(worksheetName: String): TableauWorksheet {
        val foundWorksheet = worksheets.firstOrNull { it.name == worksheetName }
        return foundWorksheet ?: TableauWorksheet(
            scraper = scraper,
            originalData = JsonObject(emptyMap()),
            originalInfo = JsonObject(emptyMap()),
            name = worksheetName,
            dataFrame = DataFrame.empty(),
            dataFull = JsonObject(emptyMap()),
            cmdResponse = cmdResponse
        )
    }

    private fun updateFullData(cmdResponse: JsonObject) {
        // Update data dictionary if present
        val vqlCmdResponse = cmdResponse["vqlCmdResponse"]?.jsonObject
        val layoutStatus = vqlCmdResponse?.get("layoutStatus")?.jsonObject
        val applicationPresModel = layoutStatus?.get("applicationPresModel")?.jsonObject

        if (applicationPresModel != null && "dataDictionary" in applicationPresModel) {
            val dataDictionary = applicationPresModel["dataDictionary"]!!.jsonObject
            if ("dataSegments" in dataDictionary) {
                val dataSegments = dataDictionary["dataSegments"]!!.jsonObject
                val dataSegmentscp = dataSegments.toMutableMap()
                val keys = dataSegmentscp.keys.toList()
                for (key in keys) {
                    if (dataSegmentscp[key] != null) {
                        val mutableDataSegment = scraper.dataSegments.toMutableMap()
                        mutableDataSegment[key] = dataSegmentscp[key]!!
                        scraper.dataSegments = JsonObject(mutableDataSegment)
                    }
                }
            } else {
                println("no data dictionary present in response1")
            }
        } else {
            println("no data dictionary present in response2")
        }

        // Update parameters if present
        if (applicationPresModel != null) {
            val newParameters = getParameterControlVqlResponse(applicationPresModel)
            val newParameterscsp = newParameters.toMutableList()
            for (newParam in newParameterscsp) {
                var found = false
                for (param in scraper.parameters) {
                    if (newParam["parameterName"] == param["parameterName"]) {
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
            for (worksheet in newFilterscsp.keys) {
                if (worksheet !in scraper.filters) {
                    scraper.filters[worksheet] = newFilters[worksheet]!!.toMutableList()
                } else {
                    for (newFilter in newFilters[worksheet]!!) {
                        var found = false
                        var foundFilterIndex = -1
                        for ((idx, filter) in scraper.filters[worksheet]!!.withIndex()) {
                            if (newFilter["globalFieldName"] == filter["globalFieldName"]) {
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
            val newZonesStorage = mutableMapOf<String, JsonObject>()
            for (zone in newZones.keys) {
                if (newZones[zone] != null && newZones[zone] != JsonNull) {
                    val zoneHasVizData = hasVizData(newZones[zone]!!.jsonObject)
                    if (!zoneHasVizData && zone in scraper.zones) {
                        newZonesStorage[zone] = scraper.zones[zone]!!.jsonObject.deepCopy()
                    } else {
                        newZonesStorage[zone] = newZones[zone]!!.jsonObject.deepCopy()
                    }
                }
            }
            scraper.zones = JsonObject(newZonesStorage)
        } else {
            scraper.zones = JsonObject(emptyMap())
        }
    }

    fun getParameters() = scraper.parameters

    suspend fun setParameter(inputName: String, value: String, inputParameter: String? = null): TableauWorkbook {
        val parameterNames = if (inputParameter == null) {
            scraper.parameters.filter { it["column"]?.jsonPrimitive?.content == inputName }
                .mapNotNull { it["parameterName"]?.jsonPrimitive?.content }
        } else {
            listOf(inputParameter)
        }

        if (parameterNames.isEmpty()) {
            println("column $inputName not found")
            return TableauWorkbook(
                scraper = scraper,
                originalData = originalData,
                originalInfo = originalInfo,
                worksheets = emptyList(),
                cmdResponse = cmdResponse
            )
        }

        println(parameterNames[0])
        val response = scraper.setParameterValue(parameterNames[0], value)
        updateFullData(response)
        return Dashboard.getWorksheetsCmdResponse(scraper, response)
    }

    fun getSheets(): List<JsonObject> {
        val presModel = getPresModelVizInfo(originalInfo)!!
        return presModel["workbookPresModel"]?.jsonObject?.get("sheetsInfo")?.jsonArray?.map { t ->
            t.jsonObject.let {
                buildJsonObject {
                    put("sheet", it["sheet"]!!)
                    put("isDashboard", it["isDashboard"]!!)
                    put("isVisible", it["isVisible"]!!)
                    put("namesOfSubsheets", it["namesOfSubsheets"]!!)
                    put("windowId", it["windowId"]!!)
                }
            }
        } ?: emptyList()
    }

    suspend fun goToSheet(sheetName: String): TableauWorkbook {
        val windowId = getSheets().mapNotNull { t ->
            if (t["sheet"]!!.jsonPrimitive.content == sheetName) t["windowId"] else null
        }.ifEmpty {
            println("sheet $sheetName not found")
            return TableauWorkbook(
                scraper = scraper,
                originalData = originalData,
                originalInfo = originalInfo,
                worksheets = emptyList(),
                cmdResponse = cmdResponse,
            )
        }
        val r = scraper.goToSheet(windowId[0].jsonPrimitive.content)
        updateFullData(r)
        scraper.dashboard = sheetName
        return Dashboard.getWorksheetsCmdResponse(scraper, r)
    }

    suspend fun getDownloadableData(sheetName: String) {
        val presModel = getPresModelVizInfo(originalInfo)!!
        val viewIds = presModel["workbookPresModel"]?.jsonObject?.get("dashboardPresModel")?.jsonObject
            ?.get("viewIds")?.jsonObject
        if (viewIds != null && sheetName in viewIds) {
            scraper.getDownloadableData(sheetName, scraper.dashboard, viewIds[sheetName]!!.jsonPrimitive.content)
        } else {
            println("$sheetName not present in viewIds list")
        }
    }

    suspend fun getCsvData(sheetName: String, prefix: String = "vudcsv"): DataFrame<*>? {
        val presModel = getPresModelVizInfo(originalInfo)!!
        val viewIds = presModel["workbookPresModel"]?.jsonObject?.get("dashboardPresModel")?.jsonObject
            ?.get("viewIds")?.jsonObject
        return if (viewIds != null && sheetName in viewIds) {
            val r = scraper.getCsvData(viewIds[sheetName]!!.jsonPrimitive.content, prefix = prefix)
            DataFrame.readCSV(r.byteInputStream())
        } else {
            println("$sheetName not present in viewIds list")
            null
        }
    }

    suspend fun getCrossTabData(sheetName: String): DataFrame<*>? {
        val r = scraper.exportCrosstabServerDialog()
        val sheetId = r["vqlCmdResponse"]?.jsonObject?.get("layoutStatus")?.jsonObject
            ?.get("applicationPresModel")?.jsonObject
            ?.get("presentationLayerNotification")?.jsonArray?.get(0)?.jsonObject
            ?.get("presModelHolder")?.jsonObject?.get("genExportCrosstabOptionsDialogPresModel")?.jsonObject
            ?.get("thumbnailSheetPickerItems")?.jsonArray
            ?.firstNotNullOfOrNull { t ->
                if (t.jsonObject["sheetName"]!!.jsonPrimitive.content == sheetName)
                    t.jsonObject["sheetdocId"]
                else null
            }?.jsonPrimitive?.content
        if (sheetId == null) {
            println("sheet $sheetName not found in API result")
            return null
        }

        val r2 = scraper.exportCrosstabToCsvServer(sheetId)
        val presModelHandler = r2["vqlCmdResponse"]?.jsonObject?.get("layoutStatus")?.jsonObject
            ?.get("applicationPresModel")?.jsonObject?.get("presentationLayerNotification")?.jsonArray
            ?.get(0)?.jsonObject?.get("presModelHolder")?.jsonObject

        val resultKey = presModelHandler?.get("genExportFilePresModel")?.jsonObject?.get("resultKey")
            ?: presModelHandler?.get("genFileDownloadPresModel")?.jsonObject?.get("tempfileKey")
        if (resultKey == null) {
            println("no genExportFilePresModel or genFileDownloadPresModel found in result")
            return null
        }

        val r3 = scraper.downloadCrossTabData(resultKey.toString())
        return DataFrame.readCSV(r3.byteInputStream(), delimiter = '\t')
    }

    fun getStoryPoints(): JsonObject {
        return getStoryPointsFromInfo(originalInfo)
    }

    suspend fun goToStoryPoint(storyPointId: Int): TableauWorkbook {
        val storyPointResult = getStoryPoints()
        val r = scraper.setActiveStoryPoint(
            storyBoard = storyPointResult["storyBoard"]!!.jsonPrimitive.content,
            storyPointId = storyPointId.toString()
        )
        updateFullData(r)
        return Dashboard.getWorksheetsCmdResponse(scraper, r)
    }
}