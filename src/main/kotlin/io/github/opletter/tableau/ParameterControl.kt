package io.github.opletter.tableau

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

object ParameterControl {
    suspend fun get(scraper: Scraper, info: JsonObject): TableauWorkbook {
        val parameterControl = getParameterControlInput(info)
        for ((idx, item) in parameterControl.withIndex()) {
            println("[$idx] ${item["column"]}")
        }
        print("select parameter control by index: ")
        val selectedPC = readlnOrNull()
        if (selectedPC == null || selectedPC == "") {
            throw Exception("you must select at least one parameter control")
        }
        val parameterControl2 = parameterControl[selectedPC.toInt()]
        println("you selected : ${parameterControl2["column"]}")
        for ((idx, item) in parameterControl2["values"]!!.jsonArray.withIndex()) {
            println("[$idx] $item")
        }
        print("select parameter control by index: ")
        val selectedValue = readlnOrNull()
        if (selectedValue == null || selectedValue == "") {
            throw Exception("you must select at least one value")
        }
        val value = parameterControl2["values"]!!.jsonArray[selectedValue.toInt()].jsonPrimitive.content
        println("you selected : $value")
        println(scraper.host)
        val r = scraper.setParameterValue(parameterControl2["parameterName"]!!.jsonPrimitive.content, value)
        return Dashboard.getCmdResponse(scraper, r)
    }
}