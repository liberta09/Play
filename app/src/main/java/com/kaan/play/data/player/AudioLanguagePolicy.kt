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
     * hiç yoksa null (dokunma: dosyanın varsayılanı kalır). [streamDubbed] (kaynak "TR Dublaj" gibi işaretli) ve dublaj
     * önceliği açıkken etiketli Türkçe parça yoksa İngilizceye geçilmez: dil bilgisi olmayan ilk parça tutulur, o da yoksa null.
     */
    fun choose(tracks: List<AudioTrackInfo>, preferDub: Boolean, streamDubbed: Boolean = false): Int? {
        val supported = tracks.filter { it.isSupported && it.index >= 0 }
        if (supported.isEmpty()) return null
        val turkish = if (preferDub) supported.firstOrNull { TurkishAudioTrackRules.isTurkishTrack(it.language, it.label) } else null
        if (turkish == null && preferDub && streamDubbed) {
            // Kaynak dublajlı işaretli ama Türkçe parça etiketsiz olabilir: İngilizceye geçme, dil bilgisi olmayan parçayı tut.
            val unlabeled = supported.firstOrNull(::hasNoLanguage) ?: return null
            return if (unlabeled.isSelected) null else unlabeled.index
        }
        val target = turkish ?: run {
            if (!AudioTrackRules.hasAnyLanguageInfo(supported)) return null
            supported.firstOrNull(::isEnglish)
        } ?: return null
        return if (target.isSelected) null else target.index
    }

    /** Dil bilgisi yok: null, boş ya da "und". */
    fun hasNoLanguage(track: AudioTrackInfo): Boolean =
        track.language.isNullOrBlank() || track.language.trim().equals("und", ignoreCase = true)

    fun isEnglish(track: AudioTrackInfo): Boolean = SubtitleLanguages.normalize(track.language) == "en"
}
