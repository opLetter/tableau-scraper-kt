package io.github.opletter.tableau

import io.github.opletter.tableau.data.Sheet
import io.github.opletter.tableau.data.StoryPointHolder
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
        val applicationPresModel = cmdResponse["vqlCmdResponse"]?.jsonObject?.get("layoutStatus")
            ?.jsonObject?.get("applicationPresModel")?.jsonObject
        val dataDictionary = applicationPresModel?.get("dataDictionary")?.jsonObject
        if (dataDictionary != null) {
            val dataSegments = dataDictionary["dataSegments"]?.jsonObject
            if (dataSegments != null) {
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

        if (applicationPresModel != null) {
            // Update parameters if present
            val newParameters = getParameterControlVqlResponse(applicationPresModel)
            val scraperParams = scraper.parameters.map { it.parameterName }
            scraper.parameters += newParameters.filter { it.parameterName !in scraperParams }

            // Update filters if present
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
                newValue!!.jsonObject
            }
        }.orEmpty()
        scraper.zones = JsonObject(newZones)
    }

    fun getParameters() = scraper.parameters

    suspend fun setParameter(inputName: String, value: String, inputParameter: String? = null): TableauWorkbook {
        val parameterNames = if (inputParameter == null) {
            scraper.parameters.filter { it.column == inputName }.map { it.parameterName }
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

        val response = scraper.setParameterValue(parameterNames[0], value)
        updateFullData(response)
        return Dashboard.getWorksheetsCmdResponse(scraper, response)
    }

    fun getSheets(): List<Sheet> {
        val presModel = getPresModelVizInfo(originalInfo)!!
        return presModel["workbookPresModel"]?.jsonObject?.get("sheetsInfo")?.jsonArray?.map {
            it as JsonObject
            Sheet(
                sheet = it["sheet"]!!.jsonPrimitive.content,
                isDashboard = it["isDashboard"]!!.jsonPrimitive.boolean,
                isVisible = it["isVisible"]!!.jsonPrimitive.boolean,
                namesOfSubsheets = it["namesOfSubsheets"]!!.jsonArray.map { it.jsonPrimitive.content },
                windowId = it["windowId"]!!.jsonPrimitive.content,
            )
        }.orEmpty()
    }

    suspend fun goToSheet(sheetName: String): TableauWorkbook {
        val windowId = getSheets().firstNotNullOfOrNull {
            if (it.sheet == sheetName) it.windowId else null
        }
        if (windowId == null) {
            println("sheet $sheetName not found")
            return TableauWorkbook(
                scraper = scraper,
                originalData = originalData,
                originalInfo = originalInfo,
                worksheets = emptyList(),
                cmdResponse = cmdResponse,
            )
        }
        val r = scraper.goToSheet(windowId)
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
            ?.firstNotNullOfOrNull {
                if (it.jsonObject["sheetName"]!!.jsonPrimitive.content == sheetName)
                    it.jsonObject["sheetdocId"]
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

    fun getStoryPoints(): StoryPointHolder {
        return getStoryPointsFromInfo(originalInfo)
    }

    suspend fun goToStoryPoint(storyPointId: Int): TableauWorkbook {
        val storyPointResult = getStoryPoints()
        val r = scraper.setActiveStoryPoint(
            storyBoard = storyPointResult.storyBoard,
            storyPointId = storyPointId.toString()
        )
        updateFullData(r)
        return Dashboard.getWorksheetsCmdResponse(scraper, r)
    }
}