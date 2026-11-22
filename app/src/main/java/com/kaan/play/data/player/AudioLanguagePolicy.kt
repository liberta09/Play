package com.kaan.play.data.player

import com.kaan.play.data.subtitle.SubtitleLanguages

/**
 * Ses dili sırası (Kullanıcı kararı 2026-11-23; Android'den bağımsız, birim testli).
 *
 * "Türkçe dublajı öncele" açıkken: Türkçe → İngilizce → dosyanın varsayılanı; kapalıyken: İngilizce → dosyanın varsayılanı.
 * Yalnızca desteklenen (çalınabilen) parçalar adaydır. Türkçe [TurkishAudioTrackRules.isTurkishTrack] ile, İngilizce
 * [SubtitleLanguages.normalize] == "en" ile (en, eng, English) tanınır. Hiçbir parçada dil bilgisi yoksa karar verilmez
 * (öğrenilmiş parça akışı [AudioTrackLearning] devreye girer). Kullanıcının elle seçimi bu kuralın dışındadır (çağıran bakar).
 */
object AudioLanguagePolicy {

    /** Oynatıcının ilk parça seçimi için tercih edilen diller (öncelik sırasıyla). */
    fun preferredLanguages(preferDub: Boolean): List<String> = if (preferDub) listOf("tr", "en") else listOf("en")

    /**
     * Seçilmesi gereken parçanın [AudioTrackInfo.index] değeri; zaten seçiliyse, uygun dilde parça yoksa ya da dil bilgisi
     * hiç yoksa null (dokunma: dosyanın varsayılanı kalır).
     */
    fun choose(tracks: List<AudioTrackInfo>, preferDub: Boolean): Int? {
        val supported = tracks.filter { it.isSupported && it.index >= 0 }
        if (supported.isEmpty()) return null
        val turkish = if (preferDub) supported.firstOrNull { TurkishAudioTrackRules.isTurkishTrack(it.language, it.label) } else null
        val target = turkish ?: run {
            if (!AudioTrackRules.hasAnyLanguageInfo(supported)) return null
            supported.firstOrNull(::isEnglish)
        } ?: return null
        return if (target.isSelected) null else target.index
    }

    fun isEnglish(track: AudioTrackInfo): Boolean = SubtitleLanguages.normalize(track.language) == "en"
}
