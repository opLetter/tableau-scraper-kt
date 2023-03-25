package main

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TableauWorkbookTest {
    private val ts = object : FakeScraper() {
        var selectNoData = false
        var storyPointsData = false
        var storyPointsParam = false
        override suspend fun getTableauData(): String {
            return if (storyPointsData) tableauDataResponseWithStoryPoints else tableauDataResponse
        }

        override suspend fun select(worksheetName: String, selection: List<Int>): JsonObject {
            return if (selectNoData) vqlCmdResponseEmptyValues else vqlCmdResponse
        }

        override suspend fun setParameterValue(parameterName: String, value: String): JsonObject {
            return if (storyPointsParam) storyPointsCmdResponse else vqlCmdResponse
        }
    }.also { runBlocking { it.loads(fakeUri) } }

//    @Before
//    fun setUp() {
//        runTest { ts.loads(fakeUri) }
//    }

    @Test
    fun `get worksheet names (initial response)`() = runTest {
        val dataFrameGroup = ts.getWorkbook()
        val worksheetNames = dataFrameGroup.getWorksheetNames()

        assertEquals(listOf("[WORKSHEET1]", "[WORKSHEET2]"), worksheetNames)
    }

    @Test
    fun `get worksheets (initial response)`() = runTest {
        val dataFrameGroup = ts.getWorkbook()
        val wsList = dataFrameGroup.worksheets

        assertEquals(2, wsList.size)
    }

    @Test
    fun `get worksheet names (vql response)`() = runTest {
        var dataFrameGroup = ts.getWorkbook()
        dataFrameGroup = dataFrameGroup.worksheets[0].select("[FIELD1]", "2")

        val worksheetNames = dataFrameGroup.getWorksheetNames()

        assertEquals(listOf("[WORKSHEET1]"), worksheetNames)
    }

    @Test
    fun `get worksheets (vql response)`() = runTest {
        var dataFrameGroup = ts.getWorkbook()
        dataFrameGroup = dataFrameGroup.worksheets[0].select("[FIELD1]", "2")

        val wsList = dataFrameGroup.worksheets

        assertEquals(1, wsList.size)
    }

    @Test
    fun `get single worksheet (initial response)`() = runTest {
        val dataFrameGroup = ts.getWorkbook()
        dataFrameGroup.getWorksheet("[WORKSHEET1]")

        assertFalse(dataFrameGroup.cmdResponse)
        assertEquals(2, dataFrameGroup.worksheets.size)
    }

    @Test
    fun `get single worksheet (vql response)`() = runTest {
        val dataFrameGroup = ts.getWorkbook()
        val dataFrame = dataFrameGroup.worksheets[0].select("[FIELD1]", "2").getWorksheet("[WORKSHEET1]")

        assertTrue(dataFrame.cmdResponse)
        assertEquals("[WORKSHEET1]", dataFrame.name)
        assertEquals(4, dataFrame.data.rowsCount())
        assertEquals(2, dataFrame.data.columnsCount())
    }

    @Test
    fun `get single worksheet (vql response) wrong sheet name`() = runTest {
        val dataFrameGroup = ts.getWorkbook()
        val dataFrame = dataFrameGroup.worksheets[0].select("[FIELD1]", "2").getWorksheet("XXXX")

        assertTrue(dataFrame.cmdResponse)
        assertEquals("XXXX", dataFrame.name)
        assertEquals(0, dataFrame.data.rowsCount())
        assertEquals(0, dataFrame.data.columnsCount())
    }

    @Test
    fun `get single worksheet (vql response) no data`() = runTest {
        ts.selectNoData = true
        val dataFrameGroup = ts.getWorkbook()
        val dataFrame = dataFrameGroup.worksheets[0].select("[FIELD1]", "2").getWorksheet("[WORKSHEET1]")

        assertTrue(dataFrame.cmdResponse)
        assertEquals("[WORKSHEET1]", dataFrame.name)
        assertEquals(0, dataFrame.data.rowsCount())
        assertEquals(0, dataFrame.data.columnsCount())
    }

    @Test
    fun `get worksheet names (storypoints)`() = runTest {
        ts.storyPointsData = true
        ts.loads(fakeUri)
        val dataFrameGroup = ts.getWorkbook()

        val worksheetNames = dataFrameGroup.getWorksheetNames()

        assertEquals(listOf("[WORKSHEET1]"), worksheetNames)
    }

    @Test
    fun `get parameters with storypoints`() = runTest {
        ts.loads(fakeUri)
        val dataFrameGroup = ts.getWorkbook()

        val parameters = dataFrameGroup.getParameters()

        assertEquals(
            listOf(
                buildJsonObject {
                    put("column", "[INPUT_NAME1]")
                    put("values", buildJsonArray {
                        add("select1")
                        add("select2")
                        add("select3")
                    })
                    put("parameterName", "[Parameters].[Parameter 1]")
                },
                buildJsonObject {
                    put("column", "[INPUT_NAME2]")
                    put("values", buildJsonArray {
                        add("select4")
                        add("select5")
                        add("select6")
                    })
                    put("parameterName", "[Parameters].[Parameter 1]")
                },
            ), parameters
        )
    }

    @Test
    fun `set parameter with story points on vql cmd response`() = runTest {
        ts.storyPointsParam = true
        val dataFrameGroup = ts.getWorkbook()

        val wb = dataFrameGroup.setParameter("[INPUT_NAME1]", "select1")
        val parameters = wb.getParameters()

        assertEquals(
            listOf(
                buildJsonObject {
                    put("column", "[INPUT_NAME1]")
                    put("values", buildJsonArray {
                        add("select1")
                        add("select2")
                        add("select3")
                    })
                    put("parameterName", "[Parameters].[Parameter 1]")
                },
                buildJsonObject {
                    put("column", "[INPUT_NAME2]")
                    put("values", buildJsonArray {
                        add("select4")
                        add("select5")
                        add("select6")
                    })
                    put("parameterName", "[Parameters].[Parameter 1]")
                },
            ), parameters
        )
    }
}