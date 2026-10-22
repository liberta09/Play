package com.kaan.play.data.match

import kotlin.math.abs

/** İçerik türü (film / dizi). Android'den bağımsız; arayüzdeki `MediaType`ten ayrı tutulur ki kurallar saf Kotlin kalsın. */
enum class ContentKind { Movie, Tv }

/**
 * İstenen içeriğin TMDB'den bilinen kimliği: eşleştirme ve doğrulama bunun üstünden yapılır.
 * [titles]: öncelik sırasıyla başlıklar (arayüz dilindeki başlık, özgün başlık, alternatifler); yinelenenler (katlanmış biçimde) atılmıştır.
 * [year]: yayın yılı (dizide ilk yayın yılı). [runtimeMinutes]: film süresi (dizide bölüm süresi); bilinmiyorsa null.
 */
data class ContentIdentity(
    val kind: ContentKind,
    val titles: List<String>,
    val year: Int?,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val runtimeMinutes: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
) {
    /** Eklentiye sorulacak arama sorguları (en çok [max]; aynı sorgu katlanmış biçimde bir kez). */
    fun searchQueries(max: Int = MAX_QUERIES): List<String> =
        titles.distinctBy(TitleText::fold).filter { TitleText.fold(it).isNotEmpty() }.take(max)

    companion object {
        const val MAX_QUERIES = 3

        /**
         * TMDB alanlarından kimlik kurar. Başlık sırası: [title] (arayüz dili), [originalTitle], sonra [alternatives] içinden
         * en çok [extra] tane (yinelenen/boş olanlar atılır). Alternatifler yalnızca eşleştirmeye yarar; sorgu sayısını
         * [searchQueries] sınırlar.
         */
        fun of(
            kind: ContentKind,
            title: String,
            originalTitle: String?,
            alternatives: List<String>,
            year: Int?,
            tmdbId: Int? = null,
            imdbId: String? = null,
            runtimeMinutes: Int? = null,
            season: Int? = null,
            episode: Int? = null,
            extra: Int = 6,
        ): ContentIdentity {
            val ordered = buildList {
                add(title)
                originalTitle?.let(::add)
                addAll(alternatives.take(extra * 4))
            }.map(String::trim).filter { TitleText.fold(it).isNotEmpty() }
            val titles = ordered.distinctBy(TitleText::fold).take(2 + extra)
            return ContentIdentity(kind, titles, year, tmdbId, imdbId?.takeIf { it.startsWith("tt") }, runtimeMinutes?.takeIf { it > 0 }, season, episode)
        }
    }
}

/** İki başlığın karşılaştırma sonucu: [score] 0..1; [partial] bir başlık ötekini kapsıyor ama fazladan sözcük var (alt başlık olabilir). */
data class TitleComparison(val score: Double, val partial: Boolean = false)

object TitleMatch {
    /** Bu puanın altı eşleşme sayılmaz. */
    const val ACCEPT = 0.85

    /** Kısmi (kapsayan) eşleşme yalnızca yıl birebir tutuyorsa bu puanla kabul edilir. */
    const val PARTIAL_WITH_YEAR = 0.86

    fun compare(wanted: String, candidate: String): TitleComparison {
        val w = TitleText.tokens(wanted)
        val c = TitleText.tokens(candidate)
        if (w.isEmpty() || c.isEmpty()) return TitleComparison(0.0)
        if (w == c) return TitleComparison(1.0)

        // Sayılar (devam filmi / bölüm no) aynı olmalı: "İki Kule" ile "Kralın Dönüşü", "Dune" ile "Dune 2" eşleşmez.
        // Tek istisna: yalnızca bir tarafta "1" olması ("Yüzüklerin Efendisi 1" = ilk film).
        val wn = w.filter(TitleText::isNumber).toSet() - "1"
        val cn = c.filter(TitleText::isNumber).toSet() - "1"
        if (wn != cn) return TitleComparison(0.0)

        val ww = w.filterNot(TitleText::isNumber)
        val cw = c.filterNot(TitleText::isNumber)
        if (ww.isEmpty() || cw.isEmpty()) return TitleComparison(0.0)
        if (ww == cw) return TitleComparison(0.97)

        val matched = ww.count { a -> cw.any { b -> sameToken(a, b) } }
        val covered = cw.count { b -> ww.any { a -> sameToken(a, b) } }
        val jaccard = matched.toDouble() / (ww.size + cw.size - minOf(matched, covered))
        if (jaccard >= 0.9) return TitleComparison(jaccard)

        // Biri ötekini kapsıyor, fazladan sözcük var: alt başlık ("Yüzük Kardeşliği", "Reloaded") olabilir → yalnızca yılla kabul.
        val wInC = matched == ww.size
        val cInW = covered == cw.size
        if (wInC || cInW) {
            val shortSize = if (wInC) ww.size else cw.size
            val longSize = if (wInC) cw.size else ww.size
            if (shortSize.toDouble() / longSize >= 0.5) return TitleComparison(PARTIAL_WITH_YEAR, partial = true)
        }
        return TitleComparison(jaccard.coerceAtMost(0.5))
    }

    /** Aynı sözcük: birebir ya da (uzunluğu ≥5 olanlarda) tek harf farkı (çekim eki/yazım). */
    internal fun sameToken(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 5 || b.length < 5) return false
        return editDistanceAtMostOne(a, b)
    }

    private fun editDistanceAtMostOne(a: String, b: String): Boolean {
        if (abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++
                j++
                continue
            }
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> {
                    i++
                    j++
                }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
}

/** Arama sonucundan alınan, eşleştirmeye yarayan alanlar. [year] eklentinin verdiği yıl; yoksa başlıktan okunur. */
data class MatchCandidate(
    val name: String,
    val year: Int?,
    val url: String,
    val kind: ContentKind? = null,
    /** Başlığın diğer adları (ör. anime `otherName`). */
    val otherNames: List<String> = emptyList(),
)

data class ScoredCandidate(val candidate: MatchCandidate, val score: Double, val yearExact: Boolean)

sealed interface MatchOutcome {
    data class Match(val best: ScoredCandidate) : MatchOutcome

    /** [rejections]: ilk birkaç adayın neden elendiği ("ad (yıl): sebep"); günlük içindir. */
    data class NoMatch(val rejections: List<String>) : MatchOutcome
}

sealed interface Verdict {
    data object Ok : Verdict

    data class Reject(val reason: String) : Verdict
}

/**
 * CloudStream (ve başlıkla arama yapan her eklenti) için eşleştirme ve doğrulama kuralları. Android'den bağımsız.
 *
 * Kurallar: (1) yılı farklı aday ASLA eşleşmez (|fark| ≤ 1 olmalı; ikisi de biliniyorsa); (2) "içinde geçer" yalnızca fazladan
 * sözcük yoksa kabul edilir, fazladan sözcük varsa (alt başlık/devam numarası) yalnızca yıl birebir tutuyorsa; (3) birden çok aday varsa
 * ilki değil en yüksek puanlısı seçilir; (4) eşik altında eşleşme YOKTUR (başka film oynatmaktan iyidir).
 */
object CsMatchRules {
    private const val MAX_REJECTIONS_LOGGED = 3

    fun pick(candidates: List<MatchCandidate>, identity: ContentIdentity): MatchOutcome {
        val scored = ArrayList<ScoredCandidate>()
        val rejections = ArrayList<String>()
        for (candidate in candidates) {
            val (accepted, reason) = evaluate(candidate, identity)
            if (accepted != null) scored += accepted else rejections += "${candidate.name}${candidate.year?.let { " ($it)" }.orEmpty()}: $reason"
        }
        // Puan eşitse önce yılı birebir tutan, sonra türü uyan, sonra geliş sırası (sort kararlıdır).
        val best = scored.sortedWith(
            compareByDescending<ScoredCandidate> { it.score }
                .thenByDescending { it.yearExact }
                .thenByDescending { it.candidate.kind != null && it.candidate.kind == identity.kind },
        ).firstOrNull()
        return if (best != null) MatchOutcome.Match(best) else MatchOutcome.NoMatch(rejections.take(MAX_REJECTIONS_LOGGED))
    }

    /** (puanlı aday, null) ya da (null, ret sebebi). */
    private fun evaluate(candidate: MatchCandidate, identity: ContentIdentity): Pair<ScoredCandidate?, String> {
        val candidateYear = candidate.year ?: TitleText.yearIn(candidate.name)
        val wantedYear = identity.year
        if (candidateYear != null && wantedYear != null && abs(candidateYear - wantedYear) > 1) {
            return null to "yıl farklı ($candidateYear ≠ $wantedYear)"
        }
        val yearExact = candidateYear != null && wantedYear != null && candidateYear == wantedYear
        val names = (listOf(candidate.name) + candidate.otherNames).flatMap(TitleText::segments).distinct()
        var best = TitleComparison(0.0)
        for (wanted in identity.titles) {
            for (name in names) {
                val cmp = TitleMatch.compare(wanted, name)
                if (cmp.score > best.score && !(cmp.partial && !yearExact)) best = cmp
            }
        }
        if (best.score < TitleMatch.ACCEPT) {
            return null to if (best.score == 0.0) "başlık uyuşmuyor" else "başlık yeterince benzemiyor (%.2f)".format(java.util.Locale.ROOT, best.score)
        }
        val bonus = (if (yearExact) 0.02 else 0.0) + (if (candidate.kind != null && candidate.kind == identity.kind) 0.01 else 0.0)
        return ScoredCandidate(candidate, best.score + bonus, yearExact) to ""
    }

    /** `load()` sonrası eklentinin bildirdiği bilgiler (alanlar bilinmiyorsa null). */
    data class LoadedInfo(
        val name: String?,
        val year: Int?,
        val imdbId: String?,
        val tmdbId: String?,
        val durationMinutes: Int?,
    )

    /**
     * Yükleme sonrası doğrulama: kimlik (TMDB/IMDb) varsa kesin karar verir; yoksa ad + yıl (+ film süresi) ile. Adı hiç vermeyen ve
     * kimliği olmayan yanıt, arama aşamasındaki eşleşmeyle yetinilir (Ok). Ad geliyorsa ve istekle uyuşmuyorsa reddedilir.
     */
    fun verifyLoaded(info: LoadedInfo, identity: ContentIdentity): Verdict {
        val tmdb = info.tmdbId?.trim()?.toIntOrNull()
        if (tmdb != null && identity.tmdbId != null && identity.tmdbId > 0) {
            return if (tmdb == identity.tmdbId) Verdict.Ok else Verdict.Reject("TMDB kimliği farklı")
        }
        val imdb = info.imdbId?.trim()?.lowercase()?.takeIf { it.startsWith("tt") }
        if (imdb != null && identity.imdbId != null) {
            return if (imdb == identity.imdbId.lowercase()) Verdict.Ok else Verdict.Reject("IMDb kimliği farklı")
        }
        val wantedYear = identity.year
        if (info.year != null && wantedYear != null && abs(info.year - wantedYear) > 1) {
            return Verdict.Reject("yıl farklı (${info.year} ≠ $wantedYear)")
        }
        val name = info.name?.takeIf { it.isNotBlank() }
        if (name != null) {
            val yearExact = info.year != null && wantedYear != null && info.year == wantedYear
            val ok = identity.titles.any { wanted ->
                TitleText.segments(name).any { segment ->
                    val cmp = TitleMatch.compare(wanted, segment)
                    cmp.score >= TitleMatch.ACCEPT && (!cmp.partial || yearExact)
                }
            }
            if (!ok) return Verdict.Reject("ad uyuşmuyor")
        }
        val runtime = identity.runtimeMinutes
        val loaded = info.durationMinutes
        if (identity.kind == ContentKind.Movie && runtime != null && loaded != null && loaded > 0) {
            if (ContentDurationCheck.check(loaded * 60_000L, runtime, ContentKind.Movie) == ContentDurationCheck.Result.Mismatch) {
                return Verdict.Reject("süre farklı ($loaded dk ≠ $runtime dk)")
            }
        }
        return Verdict.Ok
    }
}

/** Dizide eklentinin bölüm listesinden istenen sezon/bölümü bulur; kuralları [EpisodePicker] tanımlar. */
data class EpisodeRef(val data: String, val name: String?, val season: Int?, val episode: Int?)

/**
 * Bölüm seçimi: **başka sezonun/bölümün verisini asla vermez**. Sezon bilgisi olan listede yalnızca (sezon, bölüm) birebir tutan seçilir;
 * sezonsuz (düz) listede ise yalnızca ilk sezon (ya da sezon belirtilmemiş istek) için bölüm numarasıyla/sırasıyla bulunur. Sezon 2+ için
 * sezonsuz düz listede hangi sezon olduğu bilinemez → null ("kaynak bulunamadı", yanlış bölümü oynatmaktan iyidir).
 */
object EpisodePicker {
    private val NAME_NUMBER = Regex("""(?:bölüm|bolum|episode|ep\.?)\s*(\d+)|\b(\d+)\s*\.\s*bölüm""", RegexOption.IGNORE_CASE)
    private val DATA_NUMBER = Regex("""(?:bölüm|bolum|ep)\D*?(\d+)""", RegexOption.IGNORE_CASE)

    fun pick(episodes: List<EpisodeRef>, season: Int?, episode: Int?): String? {
        if (episodes.isEmpty()) return null
        if (episode == null) return episodes.first().data

        // Sezon belirtilmemişse (tek parça içerik) bölüm numarası yeter.
        if (season == null) return episodes.firstOrNull { it.episode == episode }?.data ?: pickFlat(episodes, null, episode)

        val hasSeasons = episodes.any { it.season != null }
        // 1. Birebir (sezon, bölüm).
        episodes.firstOrNull { it.season == season && it.episode == episode }?.let { return it.data }

        if (hasSeasons) {
            // Sezon bilgisi var ama istenen sezon/bölüm yok: başka sezondan bölüm vermek yerine yalnızca sezonu eksik satırlar aranır.
            val withoutSeason = episodes.filter { it.season == null }
            return pickFlat(withoutSeason, season, episode)
        }
        return pickFlat(episodes, season, episode)
    }

    /** Sezonsuz satırlar: yalnızca ilk sezon (ya da sezon belirtilmemiş) için bölüm numarası / çıkarılan numara / sıra. */
    private fun pickFlat(episodes: List<EpisodeRef>, season: Int?, episode: Int): String? {
        if (episodes.isEmpty()) return null
        if (season != null && season > 1) return null
        episodes.firstOrNull { it.episode == episode }?.let { return it.data }
        episodes.firstOrNull { extractNumber(it) == episode }?.let { return it.data }
        val index = episode - 1
        if (index in episodes.indices && episodes.none { it.episode != null && it.episode > 0 }) return episodes[index].data
        return null
    }

    internal fun extractNumber(ep: EpisodeRef): Int? {
        if (ep.episode != null && ep.episode > 0) return ep.episode
        NAME_NUMBER.find(ep.name.orEmpty())?.let { m ->
            m.groupValues[1].ifEmpty { m.groupValues[2] }.toIntOrNull()?.let { return it }
        }
        return DATA_NUMBER.find(ep.data)?.groupValues?.get(1)?.toIntOrNull()
    }
}
