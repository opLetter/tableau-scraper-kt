package io.github.opletter.tableau

import io.github.opletter.tableau.data.ParameterInfo
import io.ktor.client.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder
import java.util.regex.Pattern

interface Scraper {
    var host: String
    var info: JsonObject
    var data: JsonObject
    var dashboard: String
    var tableauData: JsonObject
    var dataSegments: JsonObject
    var parameters: List<ParameterInfo>
    var filters: MutableMap<String, MutableList<JsonObject>>
    var zones: JsonObject
    val session: HttpClient

    fun getWorkbook(): TableauWorkbook {
        return Dashboard.getWorksheets(this, data, info)
    }

    fun getWorksheet(worksheetName: String): TableauWorksheet {
        return Dashboard.getWorksheet(this, data, info, worksheetName)
    }

    fun promptDashboard(): TableauWorkbook {
        return Dashboard.get(this, data, info)
    }

    suspend fun promptParameters(): TableauWorkbook {
        return ParameterControl.get(this, info)
    }

    suspend fun promptSelect(): TableauWorkbook {
        return SelectItem.get(this, data, info)
    }

    suspend fun loads(url: String, params: Map<String, String> = emptyMap()) {
        val r = getTableauViz(url, params)
        val soup = Jsoup.parse(r)

        val tableauPlaceHolder = soup.select("div.tableauPlaceholder").first()

        val tableauDataResponse = tableauPlaceHolder?.let {
            val paramMap = tableauPlaceHolder.select("param").associate { param ->
                param.attr("name") to URLDecoder.decode(param.attr("value"), "UTF-8")
            }

            if ("host_url" !in paramMap || "site_root" !in paramMap || "name" !in paramMap) {
                println("no params found in placeholder")
                return
            }

            paramMap["ticket"]?.let {
                val sessionUrl = "${paramMap["host_url"]}trusted/$it${paramMap["site_root"]}/views/${paramMap["name"]}"
                getSessionUrl(sessionUrl)
            }

            val newUrl = "${paramMap["host_url"]}${paramMap["site_root"]}/views/${paramMap["name"]}"
            val newR = getTableauVizForSession(newUrl)
            val newSoup = Jsoup.parse(newR)

            newSoup.select("textarea#tsConfigContainer").first()?.text()
        } ?: soup.select("textarea#tsConfigContainer").first()?.text()

        tableauDataResponse?.takeIf { it.isNotEmpty() }?.let {
            tableauData = Json.parseToJsonElement(it).jsonObject
        } ?: run {
            // It seems there were changes in the Tableau API, at least for public.tableau.com dashboards
            // See also: https://github.com/bertrandmartel/tableau-scraping/issues/77
            val tableauDataResponseNewFormat = (this as? TableauScraper)?.getTableauViz2(url, params)
            if (tableauDataResponseNewFormat != null) {
                tableauData = Json.parseToJsonElement(tableauDataResponseNewFormat).jsonObject
            }
        }

        val uri = URI(url)
        host = "${uri.scheme}://${uri.host}"

        val rData = getTableauData()

        try {
            val dataRegPattern = Pattern.compile("\\d+;(\\{.*})\\d+;(\\{.*})", Pattern.MULTILINE)
            val dataRegMatcher = dataRegPattern.matcher(rData)
            if (dataRegMatcher.find()) {
                info = Json.parseToJsonElement(dataRegMatcher.group(1)).jsonObject
                data = Json.parseToJsonElement(dataRegMatcher.group(2)).jsonObject

                val presModelMap = data["secondaryInfo"]?.jsonObject?.get("presModelMap")?.jsonObject
                presModelMap?.let { modelMap ->
                    dataSegments = modelMap["dataDictionary"]!!.jsonObject["presModelHolder"]!!
                        .jsonObject["genDataDictionaryPresModel"]!!.jsonObject["dataSegments"]!!.jsonObject
                    parameters = getParameterControlInput(info)
                }
                dashboard = info["sheetName"]?.jsonPrimitive?.content ?: ""
                filters = getFiltersForAllWorksheet(data, info, rootDashboard = dashboard)
                    .mapValues { it.value.toMutableList() }
                    .toMutableMap()
            } else {
                throw Exception(rData)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Error A")
        }
    }

    suspend fun getTableauVizForSession(url: String): String

    suspend fun getTableauViz(url: String, params: Map<String, String> = emptyMap()): String

    suspend fun getSessionUrl(url: String): String

    suspend fun getTableauData(): String

    suspend fun select(worksheetName: String, selection: List<Int>): JsonObject

    suspend fun filter(
        worksheetName: String,
        globalFieldName: String,
        dashboard: String,
        selection: List<Int> = emptyList(),
        selectionToRemove: List<Int> = emptyList(),
        membershipTarget: Boolean = true,
        filterDelta: Boolean = false,
        storyboard: String? = null,
        storyboardId: String? = null,
    ): JsonObject

    suspend fun dashboardFilter(
        columnName: String,
        selection: List<Any?>,
    ): JsonObject

    suspend fun setParameterValue(parameterName: String, value: String): JsonObject

    suspend fun goToSheet(windowId: String): JsonObject

    suspend fun exportCrosstabServerDialog(): JsonObject

    suspend fun exportCrosstabToCsvServer(sheetId: String): JsonObject

    suspend fun downloadCrossTabData(resultKey: String): String

    suspend fun setActiveStoryPoint(storyBoard: String, storyPointId: String): JsonObject

    suspend fun getCsvData(viewId: String, prefix: String = "vudcsv"): String

    suspend fun getDownloadableData(
        worksheetName: String,
        dashboardName: String,
        viewId: String,
    ): String

    suspend fun getDownloadableSummaryData(
        worksheetName: String,
        dashboardName: String,
        numRows: Int = 200,
    ): JsonObject

    suspend fun getDownloadableUnderlyingData(
        worksheetName: String,
        dashboardName: String,
        numRows: Int = 200,
    ): JsonObject

    suspend fun levelDrill(
        worksheetName: String,
        drillDown: Boolean,
        position: Int = 0,
    ): JsonObject

    suspend fun renderTooltipServer(worksheetName: String, x: Number, y: Number): JsonObject

    suspend fun clearFilter(
        worksheetName: String,
        globalFieldName: String,
        dashboard: String,
    ): JsonObject
}