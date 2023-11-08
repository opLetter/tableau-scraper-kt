package io.github.opletter.tableau.data

data class StoryPointHolder(
    val storyBoard: String,
    val storyPoints: List<List<StoryPointEntry>>,
)

data class StoryPointEntry(
    val storyPointId: Int,
    val storyPointCaption: String,
)