package io.github.opletter.tableau.data

data class Sheet(
    val sheet: String,
    val isDashboard: Boolean,
    val isVisible: Boolean,
    val namesOfSubsheets: List<String>, // TODO: confirm that this is inf fact list of strings
    val windowId: String,
)