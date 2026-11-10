package com.kaan.play.data.player

import com.kaan.play.data.subtitle.SubtitleLanguages
import java.util.Locale

/** "Sonraki ses izi" düğmesinin ve ekranda gösterilen sade ses izi adının saf Kotlin kuralları. */
object AudioTrackRules {

    private val TECHNICAL_ONLY_LABELS = setOf(
        "aac", "ac3", "eac3", "e-ac3", "dts", "truehd", "opus", "mp3", "vorbis", "flac",
        "mono", "stereo", "5.1", "7.1", "dolby", "dolby digital", "pcm",
    )

    /** Desteklenen birden çok ses parçası varsa sıradaki parçayı (sonda başa dönerek) döndürür; yoksa null. */
    fun nextTrack(tracks: List<AudioTrackInfo>): AudioTrackInfo? {
        val supported = tracks.filter { it.isSupported }
        if (supported.size <= 1) return null
        val selectedPos = supported.indexOfFirst { it.isSelected }
        val nextPos = if (selectedPos < 0) 0 else (selectedPos + 1) % supported.size
        return supported[nextPos]
    }

    /** En az bir desteklenen parçada tanınabilir dil kodu varsa true; hiçbiri tanınmıyorsa false. */
    fun hasAnyLanguageInfo(tracks: List<AudioTrackInfo>): Boolean =
        tracks.filter { it.isSupported }.any { SubtitleLanguages.normalize(it.language) != null }

    /** Teknik ayrıntı (codec, kanal, mime) göstermeden sade kullanıcı adı üretir. */
    fun displayNameForUi(
        track: AudioTrackInfo,
        displayIndexOneBased: Int,
        fallbackLabel: (Int) -> String,
        languageDisplayName: (String) -> String,
    ): String {
        val normalized = track.language?.let(SubtitleLanguages::normalize)
        if (normalized != null) return languageDisplayName(normalized)
        val cleanLabel = track.label?.trim()?.takeIf { lbl ->
            lbl.isNotEmpty() &&
                !lbl.equals(track.language, ignoreCase = true) &&
                lbl.lowercase(Locale.ROOT) !in TECHNICAL_ONLY_LABELS &&
                !lbl.startsWith("audio/", ignoreCase = true)
        }
        return cleanLabel ?: fallbackLabel(displayIndexOneBased)
    }
}
