package com.kaan.play.data.match

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.StreamRequest
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * Akış etiketinde (ör. eklentinin `title`/`name`/`filename` metni) açıkça çelişen bölüm veya film yılı
 * varsa kaynağı temkinli biçimde eler; işaret eksik veya belirsizse **ASLA elemez**.
 * Başlık benzerliği denetimi YAPMAZ (yalnızca açık sezon/bölüm veya film yılı çelişkisi).
 */
object EpisodeSourceLabelRules {

    data class Decision(val accept: Boolean, val reason: String? = null)

    // S01E03, s1e3, S01.E03, S01-E03 ve opsiyonel bitiş aralığı (-E05 / -05 / -S01E05)
    private val SE_REGEX = Regex(
        """(?i)(?:^|[^\p{L}\p{N}])S(\d{1,2})[\s._-]*E(\d{1,3})(?:\s*-\s*(?:S(\d{1,2})[\s._-]*)?E?(\d{1,3}))?(?=$|[^\p{L}\p{N}])""",
    )

    // 1x03, 01x3 (çözünürlükler ve görüntü oranları kodda ayıklanır)
    private val NX_REGEX = Regex("""(?i)(?:^|[^\p{L}\p{N}])(\d{1,2})\s*x\s*(\d{1,3})(?=$|[^\p{L}\p{N}])""")

    // "2. Sezon 3. Bölüm" / "Season 2 Episode 3" / "2. Sezon 3 Bölüm"
    private val TR_SE_EP_REGEX = Regex(
        """(?i)(?:^|[^\p{L}\p{N}])(\d{1,2})\s*\.?\s*(?:Sezon|Season)\D{0,12}?(\d{1,3})\s*\.?\s*(?:Bölüm|Bolum|Episode|Ep\.?)(?=$|[^\p{L}\p{N}])""",
    )
    private val SEASON_WORD_NUM_REGEX = Regex(
        """(?i)(?:^|[^\p{L}\p{N}])(?:Sezon|Season)\s*(\d{1,2})\D{0,12}?(?:Bölüm|Bolum|Episode|Ep\.?)\s*(\d{1,3})(?=$|[^\p{L}\p{N}])""",
    )

    // Yalnızca açık bölüm işareti (ör. "3. Bölüm", "Bölüm 3", "Episode 3")
    private val EP_ORDINAL_TR_REGEX = Regex(
        """(?i)(?:^|[^\p{L}\p{N}])(\d{1,3})\s*\.\s*(?:Bölüm|Bolum)(?=$|[^\p{L}\p{N}])""",
    )
    private val EP_WORD_NUM_REGEX = Regex(
        """(?i)(?:^|[^\p{L}\p{N}])(?:Bölüm|Bolum|Episode)\s*(\d{1,3})(?=$|[^\p{L}\p{N}])""",
    )

    // S01E01-E10 gibi toplu sezon paketinde E01/E10 değil de salt "Season 1", "S01 Complete", "Tüm Bölümler" varsa ASLA eleme!
    private val PACK_HINT_REGEX = Regex(
        """(?i)\b(?:complete|pack|full\s*season|tüm\s*bölümler|tum\s*bolumler|bütün\s*bölümler|butun\s*bolumler)\b""",
    )

    // Film yılı adayları için 4 haneli sayı (önünde # veya bitişik harf/rakam olmayanlar)
    private val FOUR_DIGIT_YEAR_REGEX = Regex("""(?<![#\p{L}\p{N}])(\d{4})(?!\d)""")
    private val DIM_AFTER_REGEX = Regex("""^\s*[xX×*]\s*\d+""")
    private val DIM_BEFORE_REGEX = Regex("""\d+\s*[xX×*]\s*$""")
    private val UNIT_AFTER_REGEX = Regex(
        """^\s*(?:[MGTK]i?B|[KMGT]?bps|fps|bit|Hz|[pi])(?![a-zA-Z0-9])""",
        RegexOption.IGNORE_CASE,
    )

    fun extractCandidateYears(
        text: String,
        currentYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    ): List<Int> {
        val maxYear = currentYear + 1
        val candidates = mutableListOf<Int>()
        for (m in FOUR_DIGIT_YEAR_REGEX.findAll(text)) {
            val num = m.groupValues[1].toIntOrNull() ?: continue
            if (num !in 1900..maxYear) continue

            val before = text.substring(0, m.range.first)
            if (DIM_BEFORE_REGEX.containsMatchIn(before)) continue

            val after = text.substring(m.range.last + 1)
            if (DIM_AFTER_REGEX.containsMatchIn(after)) continue
            if (UNIT_AFTER_REGEX.containsMatchIn(after)) continue
            if (after.isNotEmpty() && after[0].isLetterOrDigit()) continue

            candidates += num
        }
        return candidates
    }

    private val NON_EPISODE_X_EPISODES = setOf(264, 265, 266, 360, 480, 576, 720, 1080, 1440, 2160)

    fun evaluate(label: String?, request: StreamRequest): Decision {
        val text = label?.trim().orEmpty()
        if (text.isEmpty()) return Decision(accept = true)

        val addonType = request.addonType?.lowercase(Locale.ROOT)
        if (addonType == "tv" || addonType == "channel") return Decision(accept = true)

        return when (request.type) {
            MediaType.Tv -> evaluateSeries(text, request.season, request.episode)
            MediaType.Movie -> evaluateMovie(text, request.year, request.title)
        }
    }

    private fun evaluateSeries(text: String, wantedSeason: Int?, wantedEpisode: Int?): Decision {
        if (wantedSeason == null || wantedEpisode == null || wantedSeason <= 0 || wantedEpisode <= 0) {
            return Decision(accept = true)
        }

        val explicitSeasonEpisode = mutableListOf<Pair<Int, Int>>()

        for (m in SE_REGEX.findAll(text)) {
            val s = m.groupValues[1].toIntOrNull() ?: continue
            val eStart = m.groupValues[2].toIntOrNull() ?: continue
            val endSeason = m.groupValues[3].toIntOrNull() ?: s
            val eEnd = m.groupValues[4].toIntOrNull()
            if (s <= 0 || eStart <= 0) continue

            if (eEnd != null && eEnd >= eStart && endSeason == s) {
                if (s == wantedSeason && wantedEpisode in eStart..eEnd) {
                    return Decision(accept = true)
                }
                explicitSeasonEpisode += s to eStart
            } else {
                explicitSeasonEpisode += s to eStart
            }
        }

        for (m in NX_REGEX.findAll(text)) {
            val s = m.groupValues[1].toIntOrNull() ?: continue
            val e = m.groupValues[2].toIntOrNull() ?: continue
            if (s <= 0 || e <= 0) continue
            if (e in NON_EPISODE_X_EPISODES) continue
            if ((s == 16 && e == 9) || (s == 4 && e == 3)) continue
            explicitSeasonEpisode += s to e
        }

        for (regex in listOf(TR_SE_EP_REGEX, SEASON_WORD_NUM_REGEX)) {
            for (m in regex.findAll(text)) {
                val s = m.groupValues[1].toIntOrNull() ?: continue
                val e = m.groupValues[2].toIntOrNull() ?: continue
                if (s > 0 && e > 0) explicitSeasonEpisode += s to e
            }
        }

        if (explicitSeasonEpisode.isNotEmpty()) {
            val anyMatches = explicitSeasonEpisode.any { (s, e) -> s == wantedSeason && e == wantedEpisode }
            if (anyMatches) return Decision(accept = true)
            if (PACK_HINT_REGEX.containsMatchIn(text) && explicitSeasonEpisode.any { (s, _) -> s == wantedSeason }) {
                return Decision(accept = true)
            }
            val foundStr = explicitSeasonEpisode.first().let { (s, e) -> "S${s}E${e}" }
            return Decision(
                accept = false,
                reason = "bölüm etiketi uyuşmuyor ($foundStr ≠ S${wantedSeason}E${wantedEpisode})",
            )
        }

        // Sezon belirtmeyen açık bölüm numarası ("3. Bölüm" / "Episode 3"): yalnızca 1. sezonda güvenle ele
        // (sonraki sezonlarda mutlak bölüm numarası olabilir — temkinli davran).
        if (wantedSeason == 1 && !PACK_HINT_REGEX.containsMatchIn(text)) {
            val epOnly = buildList {
                EP_ORDINAL_TR_REGEX.findAll(text).forEach { m ->
                    m.groupValues[1].toIntOrNull()?.takeIf { it in 1..999 }?.let(::add)
                }
                EP_WORD_NUM_REGEX.findAll(text).forEach { m ->
                    m.groupValues[1].toIntOrNull()?.takeIf { it in 1..999 }?.let(::add)
                }
            }
            if (epOnly.isNotEmpty()) {
                if (wantedEpisode in epOnly) return Decision(accept = true)
                return Decision(
                    accept = false,
                    reason = "bölüm numarası uyuşmuyor (${epOnly.first()}. bölüm ≠ $wantedEpisode)",
                )
            }
        }

        return Decision(accept = true)
    }

    private fun evaluateMovie(text: String, wantedYear: Int?, requestedTitle: String?): Decision {
        if (wantedYear == null || wantedYear !in 1900..2039) return Decision(accept = true)

        val titleYears = requestedTitle
            ?.let { t -> extractCandidateYears(t).toSet() }
            .orEmpty()

        val labelYears = extractCandidateYears(text)
            .filter { it !in titleYears }

        if (labelYears.isEmpty()) return Decision(accept = true)
        if (labelYears.any { abs(it - wantedYear) <= 1 }) return Decision(accept = true)

        return Decision(
            accept = false,
            reason = "film yılı uyuşmuyor (${labelYears.first()} ≠ $wantedYear)",
        )
    }
}
