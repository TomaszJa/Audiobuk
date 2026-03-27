package com.example.audiobuk.ui.components

enum class HelpTarget {
    PRECISION_SEEK, CHAPTER_ZOOM, SPEED, TIMER, BROWSE, CONTROLS,
    LIB_FILTER, LIB_SORT, LIB_VIEW, LIB_ROOT, LIB_SEARCH, LIB_PLAYBACK_BAR
}

data class HelpStep(
    val target: HelpTarget,
    val text: String
)
