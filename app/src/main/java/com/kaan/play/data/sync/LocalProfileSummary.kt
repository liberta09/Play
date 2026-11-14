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
    /** PIN'in son konduğu/kaldırıldığı an (ms). Wi-Fi eşitlemesinde daha yeni PIN kazanır; 0 = bilinmiyor (eski kayıt). */
    val pinUpdatedAt: Long = 0L,
) {
    val hasPin: Boolean get() = !pinHash.isNullOrBlank()
}
