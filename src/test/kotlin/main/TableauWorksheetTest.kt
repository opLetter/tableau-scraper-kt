package main

import io.github.opletter.tableau.Dashboard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class TableauWorksheetTest {
    private val ts = object : FakeScraper() {
        var selectEmpty = false
        override suspend fun select(worksheetName: String, selection: List<Int>): JsonObject {
            return if (selectEmpty) vqlCmdResponseDictionaryEmpty else vqlCmdResponse
        }
    }

    @Test
    fun `initial creation`() = runTest {
        val tableauDataFrame = Dashboard.getWorksheet(ts, data, info, "[WORKSHEET1]")

        assertEquals("[WORKSHEET1]", tableauDataFrame.name)
        assertEquals(4, tableauDataFrame.data.rowsCount())
        assertEquals(2, tableauDataFrame.data.columnsCount())
    }

    @Test
    fun `get columns`() = runTest {
        val tableauDataFrame = Dashboard.getWorksheet(ts, data, info, "[WORKSHEET1]")
        val columns = tableauDataFrame.getColumns()

        assertEquals(listOf("[FIELD1]", "[FIELD2]"), columns)
    }

    @Test
    fun `get selectable items`() = runTest {
        val tableauDataFrame = Dashboard.getWorksheet(ts, data, info, "[WORKSHEET1]")
        val selectableColumns = tableauDataFrame.getSelectableItems()

        assertEquals(
            listOf(
                buildJsonObject {
                    put("column", "[FIELD1]")
                    putJsonArray("values") {
                        add("2"); add("3"); add("4"); add("5")
                    }
                },
                buildJsonObject {
                    put("column", "[FIELD2]")
                    putJsonArray("values") {
                        add("6"); add("7"); add("8"); add("9")
                    }
                }
            ),
            selectableColumns
        )
    }

    @Test
    fun `get selectable values`() = runTest {
        val tableauDataFrame = Dashboard.getWorksheet(ts, data, info, "[WORKSHEET1]")
        val values = tableauDataFrame.getSelectableValues("[FIELD1]")

        assertEquals(
            buildJsonArray {
                (2..5).forEach { add(JsonPrimitive(it.toString())) }
            },
            values
        )
    }

    @Test
    fun `get selectable values with non-existent column`() = runTest {
        val tableauDataFrame = Dashboard.getWorksheet(ts, data, info, "[WORKSHEET1]")
        val values = tableauDataFrame.getSelectableValues("XXX")

        assertEquals(JsonArray(emptyList()), values)
    }

    @Test
    fun `get selectable values with no values`() = runTest {
        val tableauDataFrame = Dashboard.getWorksheet(ts, emptyValues, info, "[WORKSHEET1]")
        val values = tableauDataFrame.getSelectableValues("[FIELD1]")

        assertEquals(JsonArray(emptyList()), values)
    }

    @Test
    fun `select and get worksheet`() = runTest {
        ts.loads(fakeUri)
        val tableauDataFrame = Dashboard.getWorksheet(ts, data, info, "[WORKSHEET1]")
        val tableauDataFrameGroup = tableauDataFrame.select("[FIELD1]", "2")
        val newWorksheet = tableauDataFrameGroup.worksheets[0]

        assertEquals(1, tableauDataFrameGroup.worksheets.size)
        assertEquals("[WORKSHEET1]", newWorksheet.name)
        assertEquals(4, newWorksheet.data.rowsCount())
        assertEquals(2, newWorksheet.data.columnsCount())
    }

    @Test
    fun `select with non-existent column`() = runTest {
        val tableauDataFrame = Dashboard.getWorksheet(ts, data, info, "[WORKSHEET1]")
        val tableauDataFrameGroup = tableauDataFrame.select("XXXX", "2")

        assertEquals(0, tableauDataFrameGroup.worksheets.size)
    }

    @Test
    fun `vql cmd response`() = runTest {
        // Your code for mocking the necessary API calls
        val tableauDataFrame = Dashboard.getWorksheet(ts, data, info, "[WORKSHEET1]")
            .select("[FIELD1]", "2")
            .getWorksheet("[WORKSHEET1]")

        assertEquals(true, tableauDataFrame.cmdResponse)

        val columns = tableauDataFrame.getColumns()
        assertEquals(listOf("[FIELD1]", "[FIELD2]"), columns)

        val selectableColumns = tableauDataFrame.getSelectableItems()
        assertEquals(
            listOf(
                buildJsonObject {
                    put("column", "[FIELD1]")
                    putJsonArray("values") {
                        add("2"); add("3"); add("4"); add("5")
                    }
                },
                buildJsonObject {
                    put("column", "[FIELD2]")
                    putJsonArray("values") {
                        add("6"); add("7"); add("8"); add("9")
                    }
                }
            ),
            selectableColumns
        )

        val values = tableauDataFrame.getSelectableValues("[FIELD1]")
        assertEquals(
            buildJsonArray { add("2"); add("3"); add("4"); add("5") },
            values
        )

        val nonExistentValues = tableauDataFrame.getSelectableValues("XXX")
        assertEquals(JsonArray(emptyList()), nonExistentValues)
    }

    @Test
    fun `vql cmd response with no values`() = runTest {
        ts.selectEmpty = true
        val tableauDataFrame = Dashboard.getWorksheet(ts, data, info, "[WORKSHEET1]")
            .select("[FIELD1]", "2")
            .getWorksheet("[WORKSHEET1]")

        val values = tableauDataFrame.getSelectableValues("[FIELD1]")
        assertEquals(JsonArray(emptyList()), values)
    }

    @Test
    fun `get selectable values from story point`() = runTest {
        val scraper = FakeScraper()
        val tableauDataFrameGroup =
            Dashboard.getWorksheets(scraper, dataWithoutPresModelWithDictionary, storyPointsInfo)
        val ws = tableauDataFrameGroup.getWorksheet("[WORKSHEET1]")
        val values = ws.getSelectableValues("[FIELD1]")

        assertFalse(tableauDataFrameGroup.cmdResponse)
        assertEquals(
            buildJsonArray { add("2"); add("3"); add("4"); add("5") },
            values
        )
    }
}
