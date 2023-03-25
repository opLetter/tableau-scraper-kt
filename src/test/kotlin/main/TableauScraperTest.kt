package main

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TableauScraperTest {
    @Test
    fun loads() = runTest {
        val scraper = FakeScraper()
        scraper.loads(fakeUri)

        assertContains(scraper.tableauData, "vizql_root")
        assertContains(scraper.tableauData, "sessionid")
        assertContains(scraper.tableauData, "sheetId")
        assertEquals(data, scraper.data)
        assertEquals(info, scraper.info)
    }

    @Test
    fun `loads with placeholder`() = runTest {
        val scraper = object : FakeScraper() {
            override suspend fun getTableauViz(url: String, params: Map<String, String>): String =
                tableauPlaceHolderData
        }
        scraper.loads(fakeUri)

        assertContains(scraper.tableauData, "vizql_root")
        assertContains(scraper.tableauData, "sessionid")
        assertContains(scraper.tableauData, "sheetId")
        assertEquals(data, scraper.data)
        assertEquals(info, scraper.info)
    }

    @Test
    fun `loads with placeholder with ticket`() = runTest {
        val scraper = object : FakeScraper() {
            override suspend fun getTableauViz(url: String, params: Map<String, String>): String =
                tableauPlaceHolderDataWithTicket
        }
        scraper.loads(fakeUri)

        assertContains(scraper.tableauData, "vizql_root")
        assertContains(scraper.tableauData, "sessionid")
        assertContains(scraper.tableauData, "sheetId")
        assertEquals(data, scraper.data)
        assertEquals(info, scraper.info)
    }

    @Test
    fun `loads with placeholder empty`() = runTest {
        val scraper = object : FakeScraper() {
            override suspend fun getTableauViz(url: String, params: Map<String, String>): String =
                tableauPlaceHolderDataEmpty
        }
        scraper.loads(fakeUri)

        assertEquals(JsonObject(emptyMap()), scraper.tableauData)
        assertEquals(JsonObject(emptyMap()), scraper.data)
        assertEquals(JsonObject(emptyMap()), scraper.info)
    }

    @Test
    fun `get worksheets`() = runTest {
        val scraper = FakeScraper()
        scraper.loads(fakeUri)

        val dashboard = scraper.getWorkbook()
        assertEquals(2, dashboard.worksheets.size)
        assertEquals("[WORKSHEET1]", dashboard.worksheets[0].name)
        assertEquals("[WORKSHEET2]", dashboard.worksheets[1].name)
    }

    @Test
    fun `get worksheet`() = runTest {
        val scraper = FakeScraper()
        scraper.loads(fakeUri)

        val tableauDataFrame = scraper.getWorksheet("[WORKSHEET1]")
        assertEquals("[WORKSHEET1]", tableauDataFrame.name)
        assertEquals(4, tableauDataFrame.data.rowsCount())
        assertEquals(2, tableauDataFrame.data.columnsCount())
    }

    @Test
    fun `prompt dashboard`() = runTest {
        val scraper = FakeScraper()
        scraper.loads(fakeUri)

        System.setIn("\n".byteInputStream())
        val dashboard = scraper.promptDashboard()
        assertEquals(2, dashboard.worksheets.size)
        assertEquals("[WORKSHEET1]", dashboard.worksheets[0].name)
        assertEquals("[WORKSHEET2]", dashboard.worksheets[1].name)
    }

    @Test
    fun `prompt parameters`() = runTest {
        val scraper = FakeScraper().apply {
            zones = vqlCmdResponse["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!.jsonObject["applicationPresModel"]!!
                .jsonObject["workbookPresModel"]!!.jsonObject["dashboardPresModel"]!!.jsonObject["zones"]!!.jsonObject
        }
        scraper.loads(fakeUri)

        System.setIn("0\n0\n0\n\n".byteInputStream())
        val dashboard = scraper.promptParameters()
        assertEquals(1, dashboard.worksheets.size)
        assertEquals("[WORKSHEET1]", dashboard.worksheets[0].name)
    }

    @Test
    fun `prompt select`() = runTest {
        val scraper = FakeScraper().apply {
            zones = vqlCmdResponse["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!.jsonObject["applicationPresModel"]!!
                .jsonObject["workbookPresModel"]!!.jsonObject["dashboardPresModel"]!!.jsonObject["zones"]!!.jsonObject
        }
        scraper.loads(fakeUri)

        System.setIn("0\n0\n0\n\n".byteInputStream())
        val dashboard = scraper.promptSelect()
        assertEquals(1, dashboard.worksheets.size)
        assertEquals("[WORKSHEET1]", dashboard.worksheets[0].name)
    }
}