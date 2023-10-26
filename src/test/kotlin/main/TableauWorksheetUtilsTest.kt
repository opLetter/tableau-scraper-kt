package main

import io.github.opletter.tableau.Dashboard
import io.github.opletter.tableau.TableauWorkbook
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TableauWorksheetUtilsTest {
    @Test
    fun getParameters() = runTest {
        val ts = FakeScraper()
        ts.loads(fakeUri)
        val tableauDataFrameGroup = Dashboard.getWorksheets(ts, data, info)
        assertFalse(tableauDataFrameGroup.cmdResponse)

        val parameters = tableauDataFrameGroup.getParameters()
        assertEquals(listOf(
            buildJsonObject {
                put("column", "[INPUT_NAME1]")
                putJsonArray("values") {
                    add("select1"); add("select2"); add("select3")
                }
                put("parameterName", "[Parameters].[Parameter 1]")
            },
            buildJsonObject {
                put("column", "[INPUT_NAME2]")

                putJsonArray("values") {
                    add("select4"); add("select5"); add("select6")
                }
                put("parameterName", "[Parameters].[Parameter 1]")
            }
        ), parameters)

        // In vql cmd response
        val tableauDataFrameGroupCmdResponse = tableauDataFrameGroup.getWorksheet("[WORKSHEET1]")
            .select("[FIELD1]", "2")
        assertTrue(tableauDataFrameGroupCmdResponse.cmdResponse)

        val parametersCmdResponse = tableauDataFrameGroupCmdResponse.getParameters()
        assertEquals(listOf(
            buildJsonObject {
                put("column", "[INPUT_NAME1]")
                putJsonArray("values") {
                    add("select1"); add("select2"); add("select3")
                }
                put("parameterName", "[Parameters].[Parameter 1]")
            },
            buildJsonObject {
                put("column", "[INPUT_NAME2]")
                putJsonArray("values") {
                    add("select4"); add("select5"); add("select6")
                }
                put("parameterName", "[Parameters].[Parameter 1]")
            }
        ), parametersCmdResponse)
    }

    @Test
    fun setParameter() = runTest {
        val ts = FakeScraper()
        ts.loads(fakeUri)
        var tableauDataFrameGroup = Dashboard.getWorksheets(ts, data, info)
        tableauDataFrameGroup = tableauDataFrameGroup.setParameter("[INPUT_NAME1]", "select1")
        assertEquals(1, tableauDataFrameGroup.worksheets.size)
        assertEquals("[WORKSHEET1]", tableauDataFrameGroup.worksheets[0].name)
        assertEquals(4, tableauDataFrameGroup.worksheets[0].data.rowsCount())
        assertEquals(2, tableauDataFrameGroup.worksheets[0].data.columnsCount())
        assertEquals(
            listOf("[FIELD1]-value", "[FIELD2]-alias"),
            tableauDataFrameGroup.worksheets[0].data.columnNames()
        )

        // Chain
        tableauDataFrameGroup = tableauDataFrameGroup.setParameter("[INPUT_NAME1]", "select1")
        assertEquals(1, tableauDataFrameGroup.worksheets.size)
        assertEquals("[WORKSHEET1]", tableauDataFrameGroup.worksheets[0].name)
        assertEquals(4, tableauDataFrameGroup.worksheets[0].data.rowsCount())
        assertEquals(2, tableauDataFrameGroup.worksheets[0].data.columnsCount())
        assertEquals(
            listOf("[FIELD1]-value", "[FIELD2]-alias"),
            tableauDataFrameGroup.worksheets[0].data.columnNames()
        )

        // Wrong input name
        tableauDataFrameGroup = tableauDataFrameGroup.setParameter("XXXXXXXX", "select1")
        assertEquals(0, tableauDataFrameGroup.worksheets.size)
    }

    @Test
    fun getSelectableItems() = runTest {
        val ts = FakeScraper()
        ts.loads(fakeUri)

        val tableauDataFrameGroup = Dashboard.getWorksheets(ts, data, info)
        assertFalse(tableauDataFrameGroup.cmdResponse)

        val ws = tableauDataFrameGroup.getWorksheet("[WORKSHEET1]")
        val selection = ws.getSelectableItems()
        assertEquals(listOf(
            buildJsonObject {
                put("column", "[FIELD1]")
                putJsonArray("values") { add("2"); add("3"); add("4"); add("5") }
            },
            buildJsonObject {
                put("column", "[FIELD2]")
                putJsonArray("values") { add("6"); add("7"); add("8"); add("9") }
            }
        ), selection)

        // In vql cmd response
        val tableauDataFrameGroupCmdResponse = ws.select("[FIELD1]", "2")
        assertTrue(tableauDataFrameGroupCmdResponse.cmdResponse)

        val wsCmdResponse = tableauDataFrameGroupCmdResponse.getWorksheet("[WORKSHEET1]")
        val selectionCmdResponse = wsCmdResponse.getSelectableItems()
        assertEquals(listOf(
            buildJsonObject {
                put("column", "[FIELD1]")
                putJsonArray("values") { add("2"); add("3"); add("4"); add("5") }
            },
            buildJsonObject {
                put("column", "[FIELD2]")
                putJsonArray("values") { add("6"); add("7"); add("8"); add("9") }
            }
        ), selectionCmdResponse)

        // Story point
        val tableauDataFrameGroupStoryPoint =
            Dashboard.getWorksheets(ts, dataWithoutPresModelWithDictionary, storyPointsInfo)
        assertFalse(tableauDataFrameGroupStoryPoint.cmdResponse)
        val wsStoryPoint = tableauDataFrameGroupStoryPoint.getWorksheet("[WORKSHEET1]")
        val selectionStoryPoint = wsStoryPoint.getSelectableItems()
        assertEquals(listOf(
            buildJsonObject {
                put("column", "[FIELD1]")
                putJsonArray("values") { add("2"); add("3"); add("4"); add("5") }
            },
            buildJsonObject {
                put("column", "[FIELD2]")
                putJsonArray("values") { add("6"); add("7"); add("8"); add("9") }
            }
        ), selectionStoryPoint)
    }

    @Test
    fun getFilters() = runTest {
        val ts = FakeScraper()
        ts.loads(fakeUri)

        val tableauDataFrameGroup = Dashboard.getWorksheets(ts, data, info)
        assertFalse(tableauDataFrameGroup.cmdResponse)

        val ws = tableauDataFrameGroup.getWorksheet("[WORKSHEET1]")
        val filters = ws.getFilters()
        assertEquals(listOf(
            buildJsonObject {
                put("column", "FILTER_1")
                put("ordinal", "0")
                putJsonArray("values") { add("FITLTER_VALUE_1"); add("FITLTER_VALUE_2"); add("FITLTER_VALUE_3") }
                put("globalFieldName", "[FILTER].[FILTER_1]")
                putJsonArray("selection") {}
                putJsonArray("selectionAlt") {}
            }
        ), filters)
    }

    @Test
    fun setFilter() = runTest {
        val ts = FakeScraper()
        ts.loads(fakeUri)
        val tableauDataFrameGroup = Dashboard.getWorksheets(ts, data, info)

        val ws = tableauDataFrameGroup.getWorksheet("[WORKSHEET1]")
        val tableauDataFrameGroupFiltered = ws.setFilter("FILTER_1", "FITLTER_VALUE_1")
        assertEquals(1, tableauDataFrameGroupFiltered.worksheets.size)
        assertEquals("[WORKSHEET1]", tableauDataFrameGroupFiltered.worksheets[0].name)
        assertEquals(4, tableauDataFrameGroupFiltered.worksheets[0].data.rowsCount())
        assertEquals(2, tableauDataFrameGroupFiltered.worksheets[0].data.columnsCount())
        assertEquals(
            listOf("[FIELD1]-value", "[FIELD2]-alias"),
            tableauDataFrameGroupFiltered.worksheets[0].data.columnNames()
        )

        // Column not found
        val tableauDataFrameGroupUnknown = ws.setFilter("UNKNOWN", "FITLTER_VALUE_1")
        assertEquals(0, tableauDataFrameGroupUnknown.worksheets.size)

        // Incorrect value
        val tableauDataFrameGroupIncorrectValue = ws.setFilter("FILTER_1", "FITLTER_VALUE_X")
        assertEquals(0, tableauDataFrameGroupIncorrectValue.worksheets.size)
    }

    @Test
    fun selectWithTupleIds() = runTest {
        // Mocks: api.getTableauViz, api.getTableauData, api.select
        val ts = object : FakeScraper() {
            var selectWithTupleIds = false
            override suspend fun getTableauData(): String = tableauDataResponseWithTupleIds
            override suspend fun select(worksheetName: String, selection: List<Int>): JsonObject =
                if (selectWithTupleIds) vqlCmdResponseWithTupleIds else vqlCmdResponse
        }
        ts.loads(fakeUri)
        var tableauDataFrameGroup = Dashboard.getWorksheets(ts, dataWithTupleIds, info)
        assertEquals(TableauWorkbook::class, tableauDataFrameGroup::class)
        assertFalse(tableauDataFrameGroup.cmdResponse)

        var ws = tableauDataFrameGroup.getWorksheet("[WORKSHEET1]")

        // getTupleIds before select
        var tupleIds = ws.getTupleIds()
        assertEquals(listOf(listOf(2, 4, 6, 8)), tupleIds)

        tableauDataFrameGroup = ws.select("[FIELD1]", "2")
        assertEquals(1, tableauDataFrameGroup.worksheets.size)
        assertEquals("[WORKSHEET1]", tableauDataFrameGroup.worksheets[0].name)

        ws = tableauDataFrameGroup.getWorksheet("[WORKSHEET1]")

        // getTuplesIds empty after select
        tupleIds = ws.getTupleIds()
        assertEquals(emptyList(), tupleIds)

        ts.selectWithTupleIds = true
        tableauDataFrameGroup = ws.select("[FIELD1]", "2")
        ws = tableauDataFrameGroup.getWorksheet("[WORKSHEET1]")

        // getTuplesIds full after select
        tupleIds = ws.getTupleIds()
        assertEquals(listOf(listOf(2, 4, 6, 8)), tupleIds)
    }

    @Test
    fun getDownloadableSummaryData() = runTest {
        val ts = FakeScraper()
        ts.loads(fakeUri)
        val wb = ts.getWorkbook()
        val data = wb.getWorksheet("[WORKSHEET1]").getDownloadableSummaryData()
        assertEquals(200, data.rowsCount())
        assertEquals(8, data.columnsCount())
    }

    @Test
    fun getDownloadableUnderlyingData() = runTest {
        val ts = FakeScraper()
        ts.loads(fakeUri)
        val wb = ts.getWorkbook()
        val data = wb.getWorksheet("[WORKSHEET1]").getDownloadableUnderlyingData()
        assertEquals(200, data.rowsCount())
        assertEquals(42, data.columnsCount())
    }

    @Test
    fun levelDrill() = runTest {
        val ts = FakeScraper()
        ts.loads(fakeUri)
        var wb = ts.getWorkbook()
        wb = wb.getWorksheet("[WORKSHEET1]").levelDrill(drillDown = true)
        assertEquals(1, wb.worksheets.size)
        assertEquals("[WORKSHEET1]", wb.worksheets[0].name)
    }

    @Test
    fun renderTooltip() = runTest {
        val ts = FakeScraper()
        ts.loads(fakeUri)
        val wb = ts.getWorkbook()
        val ws = wb.getWorksheet("[WORKSHEET1]")
        val tableauDataFrameGroup = ws.renderTooltip(x = 0, y = 0)
        assertEquals("<div></div>", tableauDataFrameGroup)
    }
}