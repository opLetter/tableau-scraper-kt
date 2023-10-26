package io.github.opletter.tableau

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.toDataFrame

internal object Dashboard {
    fun get(scraper: Scraper, data: JsonObject, info: JsonObject): TableauWorkbook {
        val output = selectWorksheet(data).map { worksheet ->
            getWorksheet(scraper, data, info, worksheet)
        }
        return TableauWorkbook(scraper, data, info, output)
    }

    fun getWorksheet(scraper: Scraper, data: JsonObject, info: JsonObject, worksheet: String): TableauWorksheet {
        var presModelMap = getPresModelVizData(data)
        val indicesInfo = if (presModelMap == null) {
            presModelMap = getPresModelVizInfo(info)!!
                .takeIf { "dataDictionary" in it }
                ?: getPresModelVizDataWithoutViz(data)!!
            getIndicesInfoStoryPoint(presModelMap, worksheet)
        } else {
            getIndicesInfo(presModelMap, worksheet)
        }
        val dataFull = getDataFull(presModelMap, scraper.dataSegments)

        val frameData = getData(dataFull, indicesInfo).toDataFrameFill()

        return TableauWorksheet(
            scraper = scraper,
            originalData = data,
            originalInfo = info,
            name = worksheet,
            dataFull = dataFull,
            dataFrame = frameData,
        )
    }

    fun getWorksheets(scraper: Scraper, data: JsonObject, info: JsonObject): TableauWorkbook {
        val presModelMapVizData = getPresModelVizData(data)
        val presModelMapVizInfo = getPresModelVizInfo(info)
        val worksheets = when {
            presModelMapVizData != null -> listWorksheet(presModelMapVizData)
            presModelMapVizInfo != null -> listWorksheetInfo(presModelMapVizInfo).ifEmpty {
                listStoryPointsInfo(presModelMapVizInfo)
            }

            else -> emptyList()
        }

        val output = worksheets.map { worksheet ->
            getWorksheet(scraper, data, info, worksheet)
        }
        return TableauWorkbook(scraper, data, info, output)
    }

    private fun getCmdResponse(scraper: Scraper, data: JsonObject, altStoryPoints: Boolean): TableauWorkbook {
        val presModel =
            data["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!.jsonObject["applicationPresModel"]!!.jsonObject
        val zonesWithWorksheet = scraper.zones.values.filter { zone ->
            zone as JsonObject
            "worksheet" in zone &&
                    "vizData" in zone["presModelHolder"]?.jsonObject?.get("visual")?.jsonObject.orEmpty()
        }.ifEmpty { if (altStoryPoints) listStoryPointsCmdResponse(presModel, scraper) else emptyList() }

        val dataFull = getDataFullCmdResponse(presModel, scraper.dataSegments)
        val output = zonesWithWorksheet.mapNotNull { selectedZone ->
            val frameData = getWorksheetCmdResponse(selectedZone.jsonObject, dataFull) ?: return@mapNotNull null
            TableauWorksheet(
                scraper = scraper,
                originalData = data,
                originalInfo = JsonObject(emptyMap()),
                name = selectedZone.jsonObject["worksheet"]!!.jsonPrimitive.content,
                dataFrame = frameData.toDataFrameFill(),
                dataFull = dataFull,
                cmdResponse = true
            )
        }

        return TableauWorkbook(
            scraper = scraper,
            originalData = data,
            originalInfo = JsonObject(emptyMap()),
            worksheets = output,
            cmdResponse = true
        )
    }

    fun getCmdResponse(scraper: Scraper, data: JsonObject): TableauWorkbook =
        getCmdResponse(scraper, data, false)

    fun getWorksheetsCmdResponse(scraper: Scraper, data: JsonObject): TableauWorkbook =
        getCmdResponse(scraper, data, true)

    fun getWorksheetDownloadCmdResponse(scraper: Scraper, data: JsonObject): DataFrame<*> {
        val table = data["vqlCmdResponse"]!!.jsonObject["cmdResultList"]!!.jsonArray[0].jsonObject["commandReturn"]!!
            .jsonObject["underlyingDataTable"]!!.jsonObject
        val dataFull = getDataFullCmdResponse(
            JsonObject(emptyMap()),
            scraper.dataSegments,
            table["dataDictionary"]!!.jsonObject["dataSegments"]!!.jsonObject.mapValues { it.value.jsonObject }
        )
        val frameData =
            getWorksheetDownloadCmdResponse(dataFull = dataFull, table["underlyingDataTableColumns"]!!.jsonArray)

        return frameData.toDataFrame()
    }
}