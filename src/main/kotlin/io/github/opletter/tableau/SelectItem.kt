package io.github.opletter.tableau

import kotlinx.serialization.json.JsonObject

internal object SelectItem {
    suspend fun get(scraper: Scraper, data: JsonObject, info: JsonObject): TableauWorkbook {
        val selectedWorksheet = selectWorksheet(data, single = true).firstOrNull()
            ?: return TableauWorkbook(
                scraper = scraper,
                originalData = data,
                originalInfo = info,
                worksheets = emptyList()
            )
        val presModel = getPresModelVizData(data)!!
        val result = getIndicesInfo(presModel, selectedWorksheet, noSelectFilter = false)

        for (idx in result.indices) {
            println("[$idx] ${result[idx].fieldCaption}")
        }

        print("select field by index : ")
        val selectedIndex = readlnOrNull()
        if (selectedIndex == null || selectedIndex == "") {
            throw Exception("you must select at least one field")
        }
        val field = result[selectedIndex.toInt()]
        println("you have selected ${field.fieldCaption}")

        val dataFull = getDataFull(presModel, scraper.dataSegments)
        val frameData = getData(dataFull, listOf(field))
            .ifEmpty { throw Exception("no data extracted") }

        val dataValues = frameData.values.first()
        for (idx in dataValues.indices) {
            println("[$idx] ${dataValues[idx]}")
        }

        print("select value by index : ")
        val selectedValueIndex = readlnOrNull()
        if (selectedValueIndex == null || selectedValueIndex == "") {
            throw Exception("you must select at least one value")
        }
        val value = dataValues[selectedValueIndex.toInt()]
        println("you have selected $value")

        val r = scraper.select(selectedWorksheet, listOf(selectedValueIndex.toInt() + 1))
        return Dashboard.getCmdResponse(scraper, r)
    }
}