package io.github.opletter.tableau

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class TableauScraper : Scraper {
    override var host: String = ""
    override var info = JsonObject(emptyMap())
    override var data = JsonObject(emptyMap())
    override var dashboard: String = ""
    override var tableauData = JsonObject(emptyMap())
    override var dataSegments = JsonObject(emptyMap()) // persistent data dictionary
    override var parameters = mutableListOf<JsonObject>() // persist parameter controls
    override var filters = mutableMapOf<String, MutableList<JsonObject>>() // persist filters per worksheet
    override var zones = JsonObject(emptyMap()) // persist zones
    override val session: HttpClient = HttpClient(CIO) {
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.INFO
        }
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
    }

    private val hostAndRoot get() = "$host${tableauData["vizql_root"]!!.jsonPrimitive.content}"
    private val sessionId get() = tableauData["sessionid"]!!.jsonPrimitive.content
    private val routePrefix
        get() = "$hostAndRoot/sessions/$sessionId/commands"

    override suspend fun getTableauVizForSession(url: String): String {
        return session.get(url) {
            parameter(":embed", "y")
            parameter(":showVizHome", "no")
        }.bodyAsText()
    }

    override suspend fun getTableauViz(url: String, params: Map<String, String>): String {
        return session.get(url) {
            params.forEach { (key, value) -> parameter(key, value) }
            if (params.isEmpty()) {
                parameter(":embed", "y")
                parameter(":showVizHome", "no")
            }
        }.bodyAsText()
    }

    override suspend fun getSessionUrl(url: String): String {
        return session.get(url).bodyAsText()
    }

    override suspend fun getTableauData(): String {
        val dataUrl = "$hostAndRoot/bootstrapSession/sessions/$sessionId"
        return session.submitFormWithBinaryData(
            dataUrl,
            formData {
                append("sheet_id", tableauData["sheetId"]!!.jsonPrimitive.content)
                append("clientDimension", Json.encodeToString(mapOf("w" to 1920, "h" to 1080)))
            }
        ).bodyAsText()
    }

    override suspend fun select(
        worksheetName: String,
        selection: List<Int>,
    ): JsonObject {
        val payload = formData {
            append("worksheet", worksheetName)
            append("dashboard", dashboard)
            append(
                "selection",
                Json.encodeToString(buildJsonObject {
                    this.put("objectIds", JsonArray(selection.map { JsonPrimitive(it) }))
                    this.put("selectionType", "tuples")
                })
            )
            append("selectOptions", "select-options-simple")
        }

        val url = "$routePrefix/tabdoc/select"
        val response = session.submitFormWithBinaryData(url, payload)

        return try {
            val jsonResponse = response.bodyAsText()
            Json.parseToJsonElement(jsonResponse).jsonObject
        } catch (e: Exception) {
            println(e)
            throw Exception("Error B")
        }
    }

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
    ): JsonObject {
        val visualIdPresModel = buildMap {
            this["worksheet"] = worksheetName
            this["dashboard"] = dashboard
            storyboard?.let { this["storyboard"] = it }
            storyboardId?.let { this["storyPointId"] = it }
        }
        val payload = formData {
            append("visualIdPresModel", Json.encodeToString(visualIdPresModel))
            append("globalFieldName", globalFieldName)
            append("filterUpdateType", if (!filterDelta) "filter-replace" else "filter-delta")
            if (membershipTarget) {
                append("membershipTarget", "filter")
            }
            if (filterDelta) {
                append("filterAddIndices", Json.encodeToString(selection))
                append("filterRemoveIndices", Json.encodeToString(selectionToRemove))
            } else {
                append("filterIndices", Json.encodeToString(selection))
            }
        }

        val url = "$routePrefix/tabdoc/categorical-filter-by-index"
        val response = session.submitFormWithBinaryData(url, payload)

        return try {
            val jsonResponse = response.bodyAsText()
            Json.parseToJsonElement(jsonResponse).jsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception(response.bodyAsText())
        }
    }

    override suspend fun dashboardFilter(
        columnName: String,
        selection: List<Any?>,
    ): JsonObject {
        val payload = formData {
            append("dashboard", dashboard)
            append("qualifiedFieldCaption", columnName)
            append("exclude", "false")
            append("filterUpdateType", "filter-replace")
            append("filterValues", Json.encodeToString(selection))
        }

        val url = "$routePrefix/tabdoc/dashboard-categorical-filter"
        val response = session.submitFormWithBinaryData(url, payload)

        return try {
            val jsonResponse = response.bodyAsText()
            Json.parseToJsonElement(jsonResponse).jsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception(response.bodyAsText())
        }
    }

    override suspend fun setParameterValue(parameterName: String, value: String): JsonObject {
        val payload = formData {
            append("globalFieldName", parameterName)
            append("valueString", value)
            append("useUsLocale", "false")
        }

        val url = "$routePrefix/tabdoc/set-parameter-value"
        val response = session.submitFormWithBinaryData(url, payload)

        return try {
            val jsonResponse = response.bodyAsText()
            Json.parseToJsonElement(jsonResponse).jsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception(response.bodyAsText())
        }
    }

    override suspend fun goToSheet(windowId: String): JsonObject {
        val payload = formData {
            append("windowId", windowId)
        }

        val url = "$routePrefix/tabdoc/goto-sheet"
        val response = session.submitFormWithBinaryData(url, payload)

        return try {
            val jsonResponse = response.bodyAsText()
            Json.parseToJsonElement(jsonResponse).jsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception(response.bodyAsText())
        }
    }

    override suspend fun exportCrosstabServerDialog(): JsonObject {
        val payload = formData {
            append("thumbnailUris", Json.encodeToString(emptyMap<String, String>()))
        }

        val url = "$routePrefix/tabsrv/export-crosstab-server-dialog"
        val response = session.submitFormWithBinaryData(url, payload)

        return try {
            val jsonResponse = response.bodyAsText()
            Json.parseToJsonElement(jsonResponse).jsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception(response.bodyAsText())
        }
    }

    override suspend fun exportCrosstabToCsvServer(sheetId: String): JsonObject {
        val payload = formData {
            append("sheetdocId", sheetId)
            append("useTabs", "true")
            append("sendNotifications", "true")
        }

        val url = "$routePrefix/tabsrv/export-crosstab-to-csv-server"
        val response = session.submitFormWithBinaryData(url, payload)

        return try {
            val jsonResponse = response.bodyAsText()
            Json.parseToJsonElement(jsonResponse).jsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception(response.bodyAsText())
        }
    }

    override suspend fun downloadCrossTabData(resultKey: String): String {
        val response = session.get("$hostAndRoot/tempfile/sessions/$sessionId/") {
            parameter("key", resultKey)
            parameter("keepfile", "yes")
            parameter("attachment", "yes")
        }

        return response.bodyAsText(Charsets.UTF_16)
    }

    override suspend fun setActiveStoryPoint(storyBoard: String, storyPointId: String): JsonObject {
        val payload = formData {
            append("storyboard", storyBoard)
            append("storyPointId", storyPointId)
            append("shouldAutoCapture", "false")
            append("shouldAutoRevert", "true")
        }

        val url = "$routePrefix/tabdoc/set-active-story-point"
        val response = session.submitFormWithBinaryData(url, payload)

        return try {
            val jsonResponse = response.bodyAsText()
            Json.parseToJsonElement(jsonResponse).jsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception(response.bodyAsText())
        }
    }

    override suspend fun getCsvData(viewId: String, prefix: String): String {
        val dataUrl = "$hostAndRoot/$prefix/sessions/$sessionId/views/$viewId"
        val response = session.get(dataUrl) {
            parameter("csv", "true")
            parameter("showall", "true")
        }
        return response.bodyAsText(Charsets.UTF_8)
    }

    override suspend fun getDownloadableData(
        worksheetName: String,
        dashboardName: String,
        viewId: String,
    ): String {
        val input = Json.encodeToString(buildJsonObject {
            put("worksheet", worksheetName)
            put("dashboard", dashboardName)
        })

        val dataUrl = "$hostAndRoot/download/sessions/$sessionId/views/$viewId"
        val response = session.get(dataUrl) {
            parameter("maxrows", "200")
            parameter("viz", input)
        }

        return response.bodyAsText()
    }

    override suspend fun getDownloadableSummaryData(
        worksheetName: String,
        dashboardName: String,
        numRows: Int,
    ): JsonObject {
        val payload = formData {
            append("maxRows", numRows.toString())
            append("visualIdPresModel", Json.encodeToString(buildJsonObject {
                put("worksheet", worksheetName)
                put("dashboard", dashboardName)
                put("flipboardZoneId", 0)
                put("storyPointId", 0)
            }))
        }

        val url = "$routePrefix/tabdoc/get-summary-data"
        val response = session.submitFormWithBinaryData(url, payload)

        return try {
            val jsonResponse = response.bodyAsText()
            Json.parseToJsonElement(jsonResponse).jsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception(response.bodyAsText())
        }
    }

    override suspend fun getDownloadableUnderlyingData(
        worksheetName: String,
        dashboardName: String,
        numRows: Int,
    ): JsonObject {
        val payload = formData {
            append("maxRows", numRows.toString())
            append("includeAllColumns", "true")
            append("visualIdPresModel", Json.encodeToString(buildJsonObject {
                put("worksheet", worksheetName)
                put("dashboard", dashboardName)
                put("flipboardZoneId", 0)
                put("storyPointId", 0)
            }))
        }

        val url = "$routePrefix/tabdoc/get-underlying-data"
        val response = session.submitFormWithBinaryData(url, payload)

        return try {
            val jsonResponse = response.bodyAsText()
            Json.parseToJsonElement(jsonResponse).jsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception(response.bodyAsText())
        }
    }

    override suspend fun levelDrill(
        worksheetName: String,
        drillDown: Boolean,
        position: Int,
    ): JsonObject {
        val payload = formData {
            append("worksheet", worksheetName)
            append("dashboard", dashboard)
            append("boolAggregateDrillUp", drillDown.toString())
            append("shelfType", "columns-shelf")
            append("position", position.toString())
        }

        val url = "$routePrefix/tabdoc/level-drill-up-down"
        val response = session.submitFormWithBinaryData(url, payload)

        return try {
            val jsonResponse = response.bodyAsText()
            Json.parseToJsonElement(jsonResponse).jsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception(response.bodyAsText())
        }
    }

    override suspend fun renderTooltipServer(worksheetName: String, x: Number, y: Number): JsonObject {
        val payload = formData {
            append("worksheet", worksheetName)
            append("dashboard", dashboard)
            append("vizRegionRect", Json.encodeToString(buildJsonObject {
                put("r", "viz")
                put("x", x)
                put("y", y)
                put("w", 0)
                put("h", 0)
                put("fieldVector", JsonNull)
            }))
            append("allowHoverActions", "true")
            append("allowPromptText", "true")
            append("allowWork", "false")
            append("useInlineImages", "true")
        }

        val url = "$routePrefix/tabsrv/render-tooltip-server"
        val response = session.submitFormWithBinaryData(url, payload)

        return try {
            val jsonResponse = response.bodyAsText()
            Json.parseToJsonElement(jsonResponse).jsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception(response.bodyAsText())
        }
    }
}
