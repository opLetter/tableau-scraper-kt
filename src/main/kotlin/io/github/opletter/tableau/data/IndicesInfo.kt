package io.github.opletter.tableau.data

internal interface LimitedIndicesInfo {
    val fieldCaption: String
    val valueIndices: List<Int>
    val aliasIndices: List<Int>
    val dataType: String
    val fn: String
}

internal fun LimitedIndicesInfo(
    fieldCaption: String,
    valueIndices: List<Int>,
    aliasIndices: List<Int>,
    dataType: String,
    fn: String,
): LimitedIndicesInfo = object : LimitedIndicesInfo {
    override val fieldCaption = fieldCaption
    override val valueIndices = valueIndices
    override val aliasIndices = aliasIndices
    override val dataType = dataType
    override val fn = fn
}


internal data class IndicesInfo(
    override val fieldCaption: String,
    val tupleIds: List<Int>,
    override val valueIndices: List<Int>,
    override val aliasIndices: List<Int>,
    override val dataType: String,
    // these were included in the original code, but don't seem to be used anywhere
//    val paneIndices: Int,
//    val columnIndices: Int,
    override val fn: String,
) : LimitedIndicesInfo