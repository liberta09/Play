package com.kaan.play.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class LocalProfileSummary(
    val id: String,
    val name: String,
    val avatar: String = "aurora",
    val isChild: Boolean = false,
    val pinHash: String? = null,
    val isAdmin: Boolean = false,
    val isHidden: Boolean = false,
    val syncCode: String? = null,
) {
    val hasPin: Boolean get() = !pinHash.isNullOrBlank()
}
