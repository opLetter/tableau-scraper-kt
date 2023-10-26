package main

import io.github.opletter.tableau.Scraper
import io.ktor.client.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject


class FakeScraperStoryPoints : FakeScraper() {
    override suspend fun getTableauData(): String = tableauDataResponseWithStoryPointsOnlyStoryFilter
}

class FakeScraperStoryPointsNav : FakeScraper() {
    override suspend fun getTableauData(): String = tableauDataResponseWithStoryPointsNav
}

open class FakeScraper : Scraper {
    override var host: String = ""
    override var info = JsonObject(emptyMap())
    override var data = JsonObject(emptyMap())
    override var dashboard: String = ""
    override var tableauData = JsonObject(emptyMap())
    override var dataSegments = JsonObject(emptyMap()) // persistent data dictionary
    override var parameters = mutableListOf<JsonObject>() // persist parameter controls
    override var filters = mutableMapOf<String, MutableList<JsonObject>>() // persist filters per worksheet
    override var zones = JsonObject(emptyMap()) // persist zones
    override val session: HttpClient = HttpClient()

    override suspend fun getTableauVizForSession(url: String): String = tableauVizHtmlResponse

    override suspend fun getTableauViz(url: String, params: Map<String, String>): String =
        tableauVizHtmlResponse

    override suspend fun getSessionUrl(url: String): String = tableauSessionResponse

    override suspend fun getTableauData(): String = tableauDataResponse

    override suspend fun select(worksheetName: String, selection: List<Int>): JsonObject = vqlCmdResponse

    override suspend fun filter(
        worksheetName: String,
        globalFieldName: String,
        dashboard: String,
        selection: List<Int>,
        selectionToRemove: List<Int>,
        membershipTarget: Boolean,
        filterDelta: Boolean,
        storyboard: String?,
        storyboardId: String?,
    ): JsonObject = vqlCmdResponse

    // unused
    override suspend fun dashboardFilter(columnName: String, selection: List<Any?>): JsonObject = JsonObject(emptyMap())

    override suspend fun setParameterValue(parameterName: String, value: String): JsonObject = vqlCmdResponse

    override suspend fun goToSheet(windowId: String): JsonObject = vqlCmdResponse

    override suspend fun exportCrosstabServerDialog(): JsonObject =
        Json.parseToJsonElement(tableauExportCrosstabServerDialog).jsonObject

    override suspend fun exportCrosstabToCsvServer(sheetId: String): JsonObject =
        Json.parseToJsonElement(tableauExportCrosstabToCsvServerGenExportFile).jsonObject

    override suspend fun downloadCrossTabData(resultKey: String): String = tableauCrossTabData

    override suspend fun setActiveStoryPoint(storyBoard: String, storyPointId: String): JsonObject = vqlCmdResponse

    override suspend fun getCsvData(viewId: String, prefix: String): String = tableauDownloadableCsvData

    override suspend fun getDownloadableData(
        worksheetName: String,
        dashboardName: String,
        viewId: String,
    ): String = tableauVizHtmlResponse

    override suspend fun getDownloadableSummaryData(
        worksheetName: String,
        dashboardName: String,
        numRows: Int,
    ): JsonObject = Json.decodeFromString(tableauDownloadableSummaryData)

    override suspend fun getDownloadableUnderlyingData(
        worksheetName: String,
        dashboardName: String,
        numRows: Int,
    ): JsonObject = Json.decodeFromString(tableauDownloadableUnderlyingData)

    override suspend fun levelDrill(
        worksheetName: String,
        drillDown: Boolean,
        position: Int,
    ): JsonObject = vqlCmdResponse

    override suspend fun renderTooltipServer(worksheetName: String, x: Number, y: Number): JsonObject =
        tooltipCmdResponse
}