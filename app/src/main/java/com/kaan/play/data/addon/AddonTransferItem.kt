package com.kaan.play.data.addon

import kotlinx.serialization.Serializable

@Serializable
data class AddonTransferItem(val sourceUrl: String, val enabled: Boolean)
