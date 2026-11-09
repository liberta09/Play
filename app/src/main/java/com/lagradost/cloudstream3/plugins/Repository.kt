package com.lagradost.cloudstream3.plugins

data class Repository(
    val name: String,
    val url: String,
    val description: String? = null,
    val manifestVersion: Int = 1,
    val pluginLists: List<String> = emptyList(),
    val iconUrl: String? = null,
)
