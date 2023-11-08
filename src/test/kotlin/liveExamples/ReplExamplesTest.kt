package liveExamples

import io.github.opletter.tableau.TableauScraper
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.concat
import org.jetbrains.kotlinx.dataframe.io.toJson
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

class ReplExamplesTest {
    @Test
    fun example2() = runTest {
        val url = "https://public.tableau.com/views/2020_04_06_COVID19_India/Dashboard_India_Cases"
        val ts = TableauScraper()
        ts.loads(url)
        val dashboard = ts.getWorkbook()

        dashboard.worksheets.forEach { worksheet ->
            println("WORKSHEET NAME : ${worksheet.name}")
            println(worksheet.data)
        }
    }

    @Test
    fun example4() = runTest {
        val initUrl = "https://idph.illinois.gov/OpioidDataDashboard/"
        val response = Jsoup.connect(initUrl).get()
        val paramTags = response.select("div.tableauPlaceholder param")
            .associate { it.attr("name") to it.attr("value") }

        val url =
            "${paramTags["host_url"]}trusted/${paramTags["ticket"]}${paramTags["site_root"]}/views/${paramTags["name"]}"

        val ts = TableauScraper()
        ts.loads(url)
        val dashboard = ts.getWorkbook()

        dashboard.worksheets.forEach { worksheet ->
            println("WORKSHEET NAME : ${worksheet.name}")
            println(worksheet.data.toJson())
        }
    }

    @Test
    fun example7() = runTest {
        val url = "https://bi.wisconsin.gov/t/DHS/views/VaccinesAdministeredtoWIResidents/VaccinatedWisconsin-County"

        val ts = TableauScraper()
        ts.loads(url)

        val worksheet = ts.getWorksheet("Map")

        var dashboard = worksheet.select("County", "Waukesha County")
        println(dashboard.getWorksheet("Race vax/unvax county").data.toJson())

        dashboard = worksheet.select("County", "Forest County")
        println(dashboard.getWorksheet("Race vax/unvax county").data.toJson())
    }

    @Test
    fun example8() = runTest {
        val url = "https://public.tableau.com/views/NewspapersByCountyCalifornia/Newspaperbycounty"

        val ts = TableauScraper()
        ts.loads(url)
        val dashboard = ts.getWorkbook()

        dashboard.worksheets.forEach { worksheet ->
            println("WORKSHEET NAME : ${worksheet.name}")
            println(worksheet.data.toJson())
        }
    }

    @Test
    fun example9() = runTest {
        val url = "https://tableau.ons.org.br/t/ONS_Publico/views/DemandaMxima/HistricoDemandaMxima"
        val ts = TableauScraper()
        ts.loads(url)
        var wb = ts.getWorkbook()

        var ws = wb.getWorksheet("Simples Demanda Máxima Ano")
        println(ws.data.toJson())

        wb = wb.setParameter("Escala de Tempo DM Simp 4", "Dia")

        ws = wb.getWorksheet("Simples Demanda Máxima Semana Dia")
        println(ws.data.toJson())

        wb = wb.setParameter("Início Primeiro Período DM Simp 4", "01/01/2017")

        ws = wb.getWorksheet("Simples Demanda Máxima Semana Dia")
        println(ws.data.toJson())
    }

    @Test
    fun example10() = runTest {
        val url = "https://public.tableau.com/views/VaccineAdministrationMetricsDashboard/PublicCountyDash"
        val ts = TableauScraper()
        ts.loads(url)
        var wb = ts.getWorkbook()
        val ws = ts.getWorksheet("New Map")

        val counties = ws.getSelectableItems().firstOrNull { it.column == "County" }
            ?.values.orEmpty().take(20)
        println(counties)

        wb.setParameter("Key Metrics", "Race")

        for (county in counties) {
            println(county)
            wb = ws.select("County", county)
            val demographics = wb.getWorksheet("New Demographics")
            println(demographics.data.toJson())
        }
    }

    @Test
    fun example12() = runTest {
        val url = "https://www.nh.gov/t/DHHS/views/COVID19TestingDashboard/TestingDashboard"
        val ts = TableauScraper()
        ts.loads(url)

        val ws = ts.getWorksheet("All Tests COVID19")

        val filters = ws.getFilters()

        val counties = filters.firstOrNull { it["column"]!!.jsonPrimitive.content == "Union Key for CMN" }
            ?.get("values")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        println(counties)

        for (county in counties) {
            println(county)
            val wb = ws.setFilter("Union Key for CMN", county)
            val countyWs = wb.getWorksheet("All Tests COVID19")
            println(countyWs.data.toJson())
        }
    }

    @Test
    fun example13() = runTest {
        val url = "https://dashboards.doh.nj.gov/views/DailyConfirmedCaseSummary7_22_2020/PCRandAntigenPositives"
        val ts = TableauScraper()
        ts.loads(url)

        val ws = ts.getWorksheet("EPI CURVE")

        val selects = ws.getSelectableItems()

        val dates = selects.firstOrNull { it.column == "ATTR(DATE_FOR_REPORT)" }?.values.orEmpty()
        println(dates)

        for (date in dates.slice(dates.indexOf("1/5/2021 12:00:00 AM") + 1..<dates.indexOf("1/1/2021 12:00:00 AM"))) {
            println(date)
            val wb = ws.select("ATTR(DATE_FOR_REPORT)", date)
            println(wb.getWorksheet("BY COUNTY").data.toJson())
        }
    }

    @Test
    fun example15() = runTest {
        val url = "https://analytics.la.gov/t/LDH/views/covid19_hosp_vent_reg/Hosp_vent_c"
        val ts = TableauScraper()
        ts.loads(url)

        val ws = ts.getWorksheet("Hospitalization and Ventilator Usage")
        val regions = ws.getFilters().firstOrNull { it["column"]!!.jsonPrimitive.content == "Region" }
            ?.get("values")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        println(regions)

        for (region in regions) {
            println(region)
            val wb = ws.setFilter("Region", region)
            val regionWs = wb.getWorksheet("Hospitalization and Ventilator Usage")
            println(regionWs.data.toJson())
        }
    }

    @Test
    fun example16() = runTest {
        val url = "https://public.tableau.com/views/InstatIndexRanking/Instatindex"
        val ts = TableauScraper()
        ts.loads(url)
        val workbook = ts.getWorkbook()
        val ws = workbook.getWorksheet("ranking")

        val teams = ws.getFilters().firstOrNull { it["column"]!!.jsonPrimitive.content == "Team" }
            ?.get("values")?.jsonArray?.take(10)?.map { it.jsonPrimitive.content }.orEmpty()

        val pdList = mutableListOf<DataFrame<*>>()
        for (team in teams) {
            println("team: $team")
            val teamResultWb = ws.setFilter("Team", team)
            val df = teamResultWb.getWorksheet("ranking").data
            pdList.add(df)
            println(df.toJson())
        }

        val result = pdList.concat()
        println(result.toJson())
    }

    @Test
    fun example17() = runTest {
        val url = "https://public.tableau.com/views/v_7_14_2020/COVID-19TestingCommons"
        val ts = TableauScraper()
        ts.loads(url)
        val workbook = ts.getWorkbook()

        val ws = workbook.getWorksheet("Diagnostic Target")

        val locations =
            ws.getFilters().firstOrNull { it["column"]!!.jsonPrimitive.content == "Analysis Location" }
                ?.get("values")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

        val pdList = mutableListOf<DataFrame<*>>()
        for (location in locations) {
            println("location: $location")
            val locaResultWb = ws.setFilter("Analysis Location", location)
            val df = locaResultWb.getWorksheet("Company and Tests").data
            pdList.add(df)
        }

        val result = pdList.concat()
        println(result.toJson())
    }

    @Test
    fun example19() = runTest {
        val url = "https://public.tableau.com/views/CBREMigrationAnalysisv1extract/CBREMigrationAnalysis"
        val ts = TableauScraper()
        ts.loads(url)
        val wb = ts.getWorkbook()
        println(wb.getStoryPoints())
        println("go to specific storypoint")
        val sp = wb.goToStoryPoint(storyPointId = 14)

        println(sp.getWorksheetNames())
        println(sp.getWorksheet("P2P Table").data.toJson())
    }

    @Test
    fun example20() = runTest {
        val url = "https://public.tableau.com/views/EarthquakeTrendStory2/Finished-Earthquakestory"
        val ts = TableauScraper()
        ts.loads(url)
        val wb = ts.getWorkbook()
        println(wb.getStoryPoints())
        println("go to specific storypoint")
        val sp = wb.goToStoryPoint(storyPointId = 10)

        println(sp.getWorksheetNames())
        println(sp.getWorksheet("Timeline").data.toJson())
    }

    @Test
    fun example22() = runTest {
        val url = "https://public.tableau.com/views/SATCOVIDDashboard/2-dash-tiles-province"
        val ts = TableauScraper()
        ts.loads(url)
        var wb = ts.getWorkbook()

        val sheetName = "D2_New (2)"

        var ws = wb.getWorksheet(sheetName)
        wb = ws.setFilter("province", "กรุงเทพมหานคร")
        ws = wb.getWorksheet(sheetName)
        println(ws.data.toJson())
        // change date
        wb = wb.setParameter("param_date", "2021-08-16")
        ws = wb.getWorksheet(sheetName)
        println(ws.data.toJson())
        wb = wb.setParameter("param_date", "2021-08-15")
        ws = wb.getWorksheet(sheetName)
        println(ws.data.toJson())
    }

    @Test
    fun example25() = runTest {
        val url = "https://tableau.azdhs.gov/views/ELR/TestsConducted?%3Aembed=y&"
        val ts = TableauScraper()
        ts.loads(url)
        val sheetName = "P1 - Tests by Day W/ % Positivity (Both)"
        val ws = ts.getWorkbook().getWorksheet(sheetName)

        val drillDown1 = ws.levelDrill(drillDown = true)
        val drillDown2 = ws.levelDrill(drillDown = true)
        val drillDown3 = ws.levelDrill(drillDown = true)

        println(drillDown1.getWorksheet(sheetName).data.toJson())
        println(drillDown2.getWorksheet(sheetName).data.toJson())
        println(drillDown3.getWorksheet(sheetName).data.toJson())
    }
}