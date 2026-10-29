package com.kaan.play.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncBaseline(
    val favorites: Map<String, Long> = emptyMap(),
    val history: Map<String, Long> = emptyMap(),
)
