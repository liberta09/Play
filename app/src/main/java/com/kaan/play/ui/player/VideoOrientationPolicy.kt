package com.kaan.play.ui.player

/** Rendered geometry only; provider names, URLs and page orientation are not evidence. */
object VideoOrientationPolicy {
    fun isPortrait(width: Int, height: Int, pixelRatio: Float = 1f, rotationDegrees: Int = 0): Boolean? {
        if (width <= 0 || height <= 0 || !pixelRatio.isFinite() || pixelRatio <= 0f) return null
        val rotation = ((rotationDegrees % 360) + 360) % 360
        if (rotation !in listOf(0, 90, 180, 270)) return null
        val displayWidth = width.toDouble() * pixelRatio
        val displayHeight = height.toDouble()
        return if (rotation == 90 || rotation == 270) displayWidth > displayHeight else displayHeight > displayWidth
    }

    /** Null is a legacy session: its explicit portrait flag was a manual choice. False means new automatic mode. */
    fun wasManuallyChosen(manualFlag: Boolean?, portrait: Boolean): Boolean = manualFlag ?: portrait

    fun resolve(touchDevice: Boolean, autoEnabled: Boolean, manuallyChosen: Boolean, currentPortrait: Boolean,
                width: Int, height: Int, pixelRatio: Float = 1f, rotationDegrees: Int = 0): Boolean = when {
        !touchDevice -> false
        manuallyChosen -> currentPortrait
        !autoEnabled -> false
        else -> isPortrait(width, height, pixelRatio, rotationDegrees) ?: currentPortrait
    }
}
