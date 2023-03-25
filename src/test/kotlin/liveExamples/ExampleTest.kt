package liveExamples

import io.github.opletter.tableau.TableauScraper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

// Here we test known working examples, just to ensure that they don't crash
@OptIn(ExperimentalCoroutinesApi::class)
class ExampleTest {
    @Test
    fun getWorksheetsData() = runTest {
        val url = "https://public.tableau.com/views/PlayerStats-Top5Leagues20192020/OnePlayerSummary"

        val ts = TableauScraper()
        ts.loads(url)
        val workbook = ts.getWorkbook()

        for (t in workbook.worksheets) {
            println("worksheet name : ${t.name}") //show worksheet name
            println(t.data) //show dataframe for this worksheet
        }
    }

    @Test
    fun getSpecificWorksheet() = runTest {
        val url = "https://public.tableau.com/views/PlayerStats-Top5Leagues20192020/OnePlayerSummary"

        val ts = TableauScraper()
        ts.loads(url)

        val ws = ts.getWorksheet("ATT MID CREATIVE COMP")
        println(ws.data)
    }

    @Test
    fun selectSelectableItem() = runTest {
        val url = "https://public.tableau.com/views/PlayerStats-Top5Leagues20192020/OnePlayerSummary"

        val ts = TableauScraper()
        ts.loads(url)

        val ws = ts.getWorksheet("ATT MID CREATIVE COMP")

        // show selectable values
        val selections = ws.getSelectableItems()
        println(selections)

        // select that value
        val dashboard = ws.select("ATTR(Player)", "Vinicius Júnior")

        // display worksheets
        for (t in dashboard.worksheets) {
            println(t.data)
        }
    }

    @Test
    fun setParameter() = runTest {
        val url = "https://public.tableau.com/views/PlayerStats-Top5Leagues20192020/OnePlayerSummary"

        val ts = TableauScraper()
        ts.loads(url)
        val workbook = ts.getWorkbook()

        // show parameters values / column
        val parameters = workbook.getParameters()
        println(parameters)

        // set parameters column / value
        val updatedWorkbook = workbook.setParameter("P.League 2", "Ligue 1")

        // display worksheets
        for (t in updatedWorkbook.worksheets) {
            println(t.data)
        }
    }

    @Test
    fun setFilter() = runTest {
        val url = "https://public.tableau.com/views/WomenInOlympics/Dashboard1"
        val ts = TableauScraper()
        ts.loads(url)

        // show original data for worksheet
        val ws = ts.getWorksheet("Bar Chart")
        println(ws.data)

        // get filters columns and values
        val filters = ws.getFilters()
        println(filters)

        // set filter value
        val wb = ws.setFilter("Olympics", "Winter")

        // show the new data for worksheet
        val countyWs = wb.getWorksheet("Bar Chart")
        println(countyWs.data)
    }

    @Test
    fun storyPoints() = runTest {
        val url = "https://public.tableau.com/views/EarthquakeTrendStory2/Finished-Earthquakestory"
        val ts = TableauScraper()
        ts.loads(url)
        val wb = ts.getWorkbook()

        print(wb.getStoryPoints())
        print("go to specific storypoint")
        val sp = wb.goToStoryPoint(storyPointId = 10)

        print(sp.getWorksheetNames())
        print(sp.getWorksheet("Timeline").data)
    }

    //    suspend fun levelDrillUpDown() = runTest {
//        val url = "https://tableau.azdhs.gov/views/ELRv2testlevelandpeopletested/PeopleTested"
//        val ts = TableauScraper2()
//        ts.loads(url)
//        val wb = ts.getWorkbook()
//        val sheetName = "P1 - Tests by Day W/ % Positivity (Both) (2)"
//        val drillDown1 = wb.getWorksheet(sheetName).levelDrill(drillDown = true, position = 1)
//        val drillDown2 = drillDown1.getWorksheet(sheetName).levelDrill(drillDown = true, position = 1)
//        val drillDown3 = drillDown2.getWorksheet(sheetName).levelDrill(drillDown = true, position = 1)
//        println(drillDown1.getWorksheet(sheetName).data)
//        println(drillDown2.getWorksheet(sheetName).data)
//        println(drillDown3.getWorksheet(sheetName).data)
//    }
    // Note: example in the readme is broken, so we instead use
    // https://github.com/bertrandmartel/tableau-scraping/issues/38#issuecomment-943759828
    @Test
    fun levelDrillUpDown() = runTest {
        val url = "https://tableau.azdhs.gov/views/ELR/TestsConducted?%3Aembed=y&"
        val ts = TableauScraper()
        ts.loads(url)
        val sheetName = "P1 - Tests by Day W/ % Positivity (Both)"
        val ws = ts.getWorkbook().getWorksheet(sheetName)
        val drillDown1 = ws.levelDrill(drillDown = true)
        val drillDown2 = ws.levelDrill(drillDown = true)
        val drillDown3 = ws.levelDrill(drillDown = true)
        println(drillDown1.getWorksheet(sheetName).data)
        println(drillDown2.getWorksheet(sheetName).data)
        println(drillDown3.getWorksheet(sheetName).data)
    }

    @Test
    fun downloadCsvData() = runTest {
        val url = "https://public.tableau.com/views/WYCOVID-19Dashboard/WyomingCOVID-19CaseDashboard"
        val ts = TableauScraper()
        ts.loads(url)
        val wb = ts.getWorkbook()
        val data = wb.getCsvData(sheetName = "case map")
        println(data)
    }

    @Test
    fun downloadCrossTabData() = runTest {
        val url =
            "https://tableau.soa.org/t/soa-public/views/USPostLevelTermMortalityExperienceInteractiveTool/DataTable2"
        val ts = TableauScraper()
        ts.loads(url)
        val wb = ts.getWorkbook()
        wb.setParameter(inputName = "Count or Amount", value = "Amount")
        val data = wb.getCrossTabData(sheetName = "Data Table 2 - Premium Jump & PLT Duration")
        println(data)
    }

//    @Test
//    fun goToSheet() = runTest {
//        val url = "https://public.tableau.com/views/COVID-19VaccineTrackerDashboard_16153822244270/Dosesadministered"
//        val ts = TableauScraper2()
//        ts.loads(url)
//        val workbook = ts.getWorkbook()
//        val sheets = workbook.getSheets()
//        println(sheets)
//        val nycAdults = workbook.goToSheet("NYC Adults")
//        for (t in nycAdults.worksheets) {
//            println("worksheet name: ${t.name}") // show worksheet name
//            println(t.data) // show dataframe for this worksheet
//        }
//    }

    @Test
    fun renderTooltip() = runTest {
        val url = "https://public.tableau.com/views/CMI-2_0/CMI"
        val ts = TableauScraper()
        ts.loads(url)
        val workbook = ts.getWorkbook()
        val ws = workbook.getWorksheet("US Map - State - CMI")
        val tooltipHtml = ws.renderTooltip(x = 387, y = 196)
        println(tooltipHtml)
    }
}