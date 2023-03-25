package main

import io.github.opletter.tableau.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UtilsTest {
    @Test
    fun listWorksheet() {
        val presModel = getPresModelVizData(data)!!
        val worksheets = listWorksheet(presModel)
        assertEquals(2, worksheets.size)
        assertEquals("[WORKSHEET1]", worksheets[0])
        assertEquals("[WORKSHEET2]", worksheets[1])

        assertFailsWith<Exception> { listWorksheet(JsonObject(emptyMap())) }
        assertFailsWith<Exception> { listWorksheet(emptyData) }
        assertFailsWith<Exception> { listWorksheet(dataWithoutViz) }
        assertFailsWith<Exception> { listWorksheet(dataWithoutPres1) }
        assertFailsWith<Exception> { listWorksheet(dataWithoutMapPresModel) }
        assertFailsWith<Exception> { listWorksheet(dataWithoutMapPres2) }
    }

    @Test
    fun selectWorksheet() {
        System.setIn("\n".byteInputStream())
        val worksheets = selectWorksheet(data)
        assertEquals(2, worksheets.size)
        assertEquals(listOf("[WORKSHEET1]", "[WORKSHEET2]"), worksheets)

        System.setIn("0\n".byteInputStream())
        val singleWorksheet = selectWorksheet(data)
        assertEquals(1, singleWorksheet.size)
        assertEquals(listOf("[WORKSHEET1]"), singleWorksheet)

        System.setIn("\n".byteInputStream())
        assertFailsWith<Exception> { selectWorksheet(data, single = true) }

        System.setIn("0\n".byteInputStream())
        val emptyWorksheetList = selectWorksheet(noWorksheet)
        assertEquals(0, emptyWorksheetList.size)
    }


    @Test
    fun getIndicesInfo() {
        val presModel = getPresModelVizData(data)!!
        val indicesInfo = getIndicesInfo(presModel, "[WORKSHEET1]")
        assertEquals(2, indicesInfo.size)
        assertEquals("[FIELD1]", indicesInfo[0]["fieldCaption"]!!.jsonPrimitive.content)
        assertEquals("[FIELD2]", indicesInfo[1]["fieldCaption"]!!.jsonPrimitive.content)

        // Check noSelectFilter parameter
        val filteredIndicesInfo = getIndicesInfo(presModel, "[WORKSHEET1]", noSelectFilter = false)
        assertEquals(1, filteredIndicesInfo.size)
    }

    @Test
    fun getDataFull() {
        val presModel = getPresModelVizData(data)!!
        val dataFull = getDataFull(presModel, JsonObject(emptyMap()))
        assertEquals(2, dataFull.keys.size)
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9"),
            dataFull["cstring"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf(1, 2, 3, 4, 5), dataFull["real"]!!.jsonArray.map { it.jsonPrimitive.int })
    }

    @Test
    fun onDataValue() {
        val newVal1 = onDataValue(
            1,
            buildJsonArray { (1..5).forEach { add(it) } },
            buildJsonArray { add("string1"); add("string2"); add("string3") }
        )
        assertEquals(2, newVal1.jsonPrimitive.int)
        val newVal2 = onDataValue(
            -1,
            buildJsonArray { (1..5).forEach { add(it) } },
            buildJsonArray { add("string1"); add("string2"); add("string3") }
        )
        assertEquals("string1", newVal2.jsonPrimitive.content)
    }

    @Test
    fun getData() {
        val presModel = getPresModelVizData(data)!!
        val dataFull = getDataFull(presModel, JsonObject(emptyMap()))
        val indicesInfo = getIndicesInfo(presModel, "[WORKSHEET1]")
        val frameData = getData(dataFull, indicesInfo)

        assertEquals(2, frameData.keys.size)
        assertEquals(
            buildJsonArray { add("2"); add("3"); add("4"); add("5") },
            frameData["[FIELD1]-value"]
        )
        assertEquals(
            buildJsonArray { add("6"); add("7"); add("8"); add("9") },
            frameData["[FIELD2]-alias"]
        )
    }

    @Test
    fun getDataFullCmdResponse() {
        val presModel = vqlCmdResponse["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!
            .jsonObject["applicationPresModel"]!!.jsonObject
        val dataFull = getDataFullCmdResponse(presModel, JsonObject(emptyMap()))

        assertEquals(2, dataFull.keys.size)
        assertEquals(
            buildJsonArray { (1..9).forEach { add(it.toString()) } },
            dataFull["cstring"]
        )
        assertEquals(
            buildJsonArray { (1..5).forEach { add(it) } },
            dataFull["real"]
        )
    }

    @Test
    fun listWorksheetCmdResponse() {
        val presModel = vqlCmdResponse["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!
            .jsonObject["applicationPresModel"]!!.jsonObject
        val worksheetList = listWorksheetCmdResponse(presModel)

        assertEquals(2, worksheetList.size)
        assertEquals("[WORKSHEET1]", worksheetList[0]["worksheet"]!!.jsonPrimitive.content)
        assertEquals("[WORKSHEET2]", worksheetList[1]["worksheet"]!!.jsonPrimitive.content)
    }

    @Test
    fun getWorksheetCmdResponse() {
        val presModel = vqlCmdResponse["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!
            .jsonObject["applicationPresModel"]!!.jsonObject
        val dataFull = getDataFullCmdResponse(presModel, JsonObject(emptyMap()))
        val worksheet = listWorksheetCmdResponse(presModel)[0]
        val frameData = getWorksheetCmdResponse(worksheet, dataFull)!!

        assertEquals(2, frameData.keys.size)
        assertEquals(
            buildJsonArray { add("2"); add("3"); add("4"); add("5") },
            frameData["[FIELD1]-value"]
        )
        assertEquals(
            buildJsonArray { add("6"); add("7"); add("8"); add("9") },
            frameData["[FIELD2]-alias"]
        )
    }

    //    @Test
//    fun selectWorksheetCmdResponse() {
//        val presModel = vqlCmdResponse["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!
//            .jsonObject["applicationPresModel"]!!.jsonObject
//
//        // all worksheets
//        System.setIn("\n".byteInputStream())
//        val worksheetList1 = selectWorksheetCmdResponse(presModel)
//
//        assertEquals(2, worksheetList1.size)
//        assertEquals("[WORKSHEET1]", worksheetList1[0]["worksheet"])
//        assertEquals("[WORKSHEET2]", worksheetList1[1]["worksheet"])
//
//        // one worksheet
//        System.setIn("0\n".byteInputStream())
//        val worksheetList2 = selectWorksheetCmdResponse(presModel)
//
//        assertEquals(1, worksheetList2.size)
//        assertEquals("[WORKSHEET1]", worksheetList2[0]["worksheet"])
//    }
    @Test
    fun getParameterControlInput() {
        val parameterControlInput = getParameterControlInput(info)

        assertEquals(2, parameterControlInput.size)
        assertEquals("[INPUT_NAME1]", parameterControlInput[0]["column"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("select1", "select2", "select3"),
            parameterControlInput[0]["values"]!!.jsonArray.map { it.jsonPrimitive.content }
        )
        assertEquals("[Parameters].[Parameter 1]", parameterControlInput[0]["parameterName"]!!.jsonPrimitive.content)
        assertEquals("[INPUT_NAME2]", parameterControlInput[1]["column"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("select4", "select5", "select6"),
            parameterControlInput[1]["values"]!!.jsonArray.map { it.jsonPrimitive.content }
        )
        assertEquals("[Parameters].[Parameter 1]", parameterControlInput[1]["parameterName"]!!.jsonPrimitive.content)
    }

    @Test
    fun getParameterControlVqlResponse() {
        val presModel = vqlCmdResponse["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!
            .jsonObject["applicationPresModel"]!!.jsonObject
        val parameterControl = getParameterControlVqlResponse(presModel)

        assertEquals(2, parameterControl.size)
        assertEquals("[INPUT_NAME1]", parameterControl[0]["column"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("select1", "select2", "select3"),
            parameterControl[0]["values"]!!.jsonArray.map { it.jsonPrimitive.content }
        )
        assertEquals("[Parameters].[Parameter 1]", parameterControl[0]["parameterName"]!!.jsonPrimitive.content)
        assertEquals("[INPUT_NAME2]", parameterControl[1]["column"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("select4", "select5", "select6"),
            parameterControl[1]["values"]!!.jsonArray.map { it.jsonPrimitive.content }
        )
        assertEquals("[Parameters].[Parameter 1]", parameterControl[1]["parameterName"]!!.jsonPrimitive.content)
    }

    @Test
    fun getIndicesInfoVqlResponse() {
        val presModel = vqlCmdResponse["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!
            .jsonObject["applicationPresModel"]!!.jsonObject
        val indicesInfo = getIndicesInfoVqlResponse(presModel, "[WORKSHEET1]")

        assertEquals(2, indicesInfo.size)
        assertEquals("[FIELD1]", indicesInfo[0]["fieldCaption"]!!.jsonPrimitive.content)
        assertEquals("[FIELD2]", indicesInfo[1]["fieldCaption"]!!.jsonPrimitive.content)

        // check noSelectFilter parameter
        val indicesInfoWithFilter = getIndicesInfoVqlResponse(presModel, "[WORKSHEET1]", noSelectFilter = false)
        assertEquals(1, indicesInfoWithFilter.size)

        // worksheet not found
        val indicesInfoNotFound = getIndicesInfoVqlResponse(presModel, "XXXXX")
        assertEquals(0, indicesInfoNotFound.size)

        // empty data
        val presModelEmpty = vqlCmdResponseEmptyValues["vqlCmdResponse"]!!.jsonObject["layoutStatus"]!!
            .jsonObject["applicationPresModel"]!!.jsonObject
        val indicesInfoEmpty = getIndicesInfoVqlResponse(presModelEmpty, "[WORKSHEET1]")
        assertEquals(0, indicesInfoEmpty.size)
    }
}