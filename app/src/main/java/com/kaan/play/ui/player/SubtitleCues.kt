package com.kaan.play.ui.player

import androidx.media3.common.text.Cue
import com.kaan.play.data.subtitle.SubtitleCue

/** [SubtitleCue] -> Media3 [Cue] (`SubtitleView`'a verilir). Android sınıflarına bağlı olduğu için denetleyiciden ayrı durur. */
object SubtitleCues {

    /** Aynı anda görünen satırlar tek altyazıda alt alta birleşir (Media3 konumsuz satırları üst üste çizerdi). */
    fun toCues(active: List<SubtitleCue>): List<Cue> {
        if (active.isEmpty()) return emptyList()
        return listOf(Cue.Builder().setText(active.joinToString("\n") { it.text }).build())
    }
}
