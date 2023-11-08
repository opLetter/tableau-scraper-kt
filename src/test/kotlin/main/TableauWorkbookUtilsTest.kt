package main

import io.github.opletter.tableau.data.Sheet
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TableauWorkbookUtilsTest {
    @Test
    fun `storybook filters`() = runTest {
        val scraper = FakeScraperStoryPoints()
        scraper.loads(fakeUri)

        val workbook = scraper.getWorkbook()
        val worksheetNames = workbook.getWorksheetNames()
        assertFalse(workbook.cmdResponse)
        assertEquals(listOf("[WORKSHEET1]"), worksheetNames)
    }

    @Test
    fun sheets() = runTest {
        val scraper = FakeScraper()
        scraper.loads(fakeUri)

        val workbook = scraper.getWorkbook()

        val sheets = workbook.getSheets()
        assertEquals(
            listOf(
                Sheet(
                    sheet = "[WORKSHEET1]",
                    isDashboard = false,
                    isVisible = true,
                    namesOfSubsheets = emptyList(),
                    windowId = "{XXXXX}",
                )
            ),
            sheets
        )

        val workbookRes1 = workbook.goToSheet("[WORKSHEET1]")
        assertEquals(1, workbookRes1.worksheets.size)
        assertEquals("[WORKSHEET1]", workbookRes1.worksheets[0].name)

        val workbookRes2 = workbook.goToSheet("XXXXXX")
        assertEquals(emptyList(), workbookRes2.worksheets)
    }

    @Test
    fun getCsvData() = runTest {
        val scraper = FakeScraper()
        scraper.loads(fakeUri)

        val workbook = scraper.getWorkbook()
        val data = workbook.getCsvData("[WORKSHEET1]")!!

        assertEquals(3, data.rowsCount())
        assertEquals(1, data.columnsCount())
    }

    @Test
    fun `getCsvData no view ids`() = runTest {
        val scraper = object : FakeScraper() {
            override suspend fun getTableauData(): String = tableauDataResponseNoViewIds
        }
        scraper.loads(fakeUri)

        val workbook = scraper.getWorkbook()
        val data = workbook.getCsvData("[WORKSHEET1]")

        assertNull(data)
    }

    @Test
    fun `getCsvData view ids no sheet`() = runTest {
        val scraper = object : FakeScraper() {
            override suspend fun getTableauData(): String = tableauDataResponseViewIdsNoSheet
        }
        scraper.loads(fakeUri)

        val workbook = scraper.getWorkbook()
        val data = workbook.getCsvData("[WORKSHEET1]")

        assertNull(data)
    }

    @Test
    fun `get downloadable data`() = runTest {
        val scraper = FakeScraper()
        scraper.loads(fakeUri)

        val workbook = scraper.getWorkbook()
        workbook.getDownloadableData(sheetName = "[WORKSHEET1]")

        assertEquals(2, workbook.worksheets.size)
    }

    @Test
    fun `get story points`() = runTest {
        val scraper = FakeScraperStoryPointsNav()
        scraper.loads(fakeUri)

        val workbook = scraper.getWorkbook()
        val storyPointResult = workbook.getStoryPoints()

        assertEquals("[WORKSHEET1]", storyPointResult.storyBoard)
        assertEquals(1, storyPointResult.storyPoints.size)
        assertEquals(12, storyPointResult.storyPoints[0].size)

        val scraper2 = FakeScraper()
        scraper2.loads(fakeUri)

        val workbook2 = scraper2.getWorkbook()
        val storyPointResult2 = workbook2.getStoryPoints()

        assertEquals(emptyList(), storyPointResult2.storyPoints)
    }

    @Test
    fun `go to story point`() = runTest {
        val scraper = object : FakeScraper() {
            override suspend fun getTableauData(): String = tableauDataResponseWithStoryPointsNav
        }
        scraper.loads(fakeUri)

        val workbook = scraper.getWorkbook()
        val storyWb = workbook.goToStoryPoint(storyPointId = 1)

        assertEquals(1, storyWb.worksheets.size)
    }

    @Test
    fun `get cross tab data`() = runTest {
        val scraper = object : FakeScraper() {
            var genExportMode = true
            override suspend fun exportCrosstabToCsvServer(sheetId: String): JsonObject {
                return if (genExportMode)
                    Json.parseToJsonElement(tableauExportCrosstabToCsvServerGenExportFile).jsonObject
                else
                    Json.parseToJsonElement(tableauExportCrosstabToCsvServerGenFileDownload).jsonObject
            }
        }
        scraper.loads(fakeUri)

        val workbook = scraper.getWorkbook()

        val data1 = workbook.getCrossTabData(sheetName = "[WORKSHEET1]")!!
        assertEquals(3, data1.rowsCount())
        assertEquals(2, data1.columnsCount())

        scraper.genExportMode = false

        val data2 = workbook.getCrossTabData(sheetName = "[WORKSHEET1]")!!
        assertEquals(3, data2.rowsCount())
        assertEquals(2, data2.columnsCount())
    }

    @Nested
    inner class TableauWorkbookTest {
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

//        @Before
//        fun setUp() {
//            runTest { ts.loads(fakeUri) }
//        }

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
}