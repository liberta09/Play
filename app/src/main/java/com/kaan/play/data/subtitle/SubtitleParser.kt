package com.kaan.play.data.subtitle

/**
 * Tek altyazı satırı. [text] düz metindir (satır sonları `\n`): renk, konum, eğik/kalın gibi her biçim atılır, çünkü oynatıcı
 * altyazıyı kullanıcının tek altyazı görünümüyle (Ayarlar > Oynatıcı) çizer (Media3 de gömülü biçimleri yok sayıyordu). Zamanlar milisaniye, içeriğin kendi zaman çizgisindedir (kaydırma yok).
 */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

enum class SubtitleFormat { SubRip, WebVtt, Ssa }

/**
 * SRT, WebVTT ve ASS/SSA metnini [SubtitleCue] listesine çevirir. Biçim dosya adından değil içerikten anlaşılır (addon
 * adresleri çoğu zaman uzantısızdır). Bozuk kayıt yalnızca kendini düşürür; hiç satır çıkmazsa boş liste döner.
 *
 * Ayrıştırıcıyı Media3'e bırakmadık: kaydırmayı (senkron) anında ve oynatmayı kesmeden uygulamak için satırları kendimiz
 * zamanlıyoruz, Media3'ün ayrıştırıcıları da Android sınıflarına bağlı olduğu için yerel birim testlerde çalışmaz.
 */
object SubtitleParser {

    const val MAX_CUES = 20_000

    fun sniff(text: String): SubtitleFormat {
        val head = text.trimStart('﻿', ' ', '\t', '\r', '\n')
        return when {
            head.startsWith("WEBVTT") -> SubtitleFormat.WebVtt
            head.take(4096).let { it.contains("[Script Info]", ignoreCase = true) || it.contains("[Events]", ignoreCase = true) } ->
                SubtitleFormat.Ssa
            else -> SubtitleFormat.SubRip
        }
    }

    fun parse(text: String): List<SubtitleCue> {
        val cues = when (sniff(text)) {
            SubtitleFormat.Ssa -> parseSsa(text)
            // SRT ve WebVTT aynı kalıptır (zaman satırı + metin); WebVTT'nin üst bilgi/NOTE/STYLE blokları zaman satırından önce kalır.
            SubtitleFormat.WebVtt, SubtitleFormat.SubRip -> parseCueBlocks(text)
        }
        return cues.filter { it.endMs > it.startMs && it.text.isNotBlank() }.sortedBy { it.startMs }.take(MAX_CUES)
    }

    // ---- SRT ve WebVTT: "zaman --> zaman" satırı + metin satırları ----

    // SRT: 00:01:02,345 ; VTT: 01:02.345 ya da 00:01:02.345. Ayırıcı , ya da .; saat isteğe bağlı.
    private val TIMING = Regex(
        """(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})\s*-->\s*(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""",
    )

    private fun parseCueBlocks(text: String): List<SubtitleCue> {
        val out = ArrayList<SubtitleCue>()
        var start = 0L
        var end = 0L
        var open = false
        val body = StringBuilder()

        // [dropIndex]: kayıt boş satırsız bir sonrakine bitişikse, son satır sonraki kaydın sıra numarasıdır; metne karışmasın.
        fun flush(dropIndex: Boolean) {
            if (open) {
                val lines = body.toString().lines()
                val kept = if (dropIndex && lines.size > 1 && lines.last().isNotBlank() && lines.last().trim().all { it.isDigit() }) {
                    lines.dropLast(1)
                } else {
                    lines
                }
                out += SubtitleCue(start, end, cleanMarkup(kept.joinToString("\n")))
            }
            open = false
            body.setLength(0)
        }

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trimEnd('\r')
            val timing = TIMING.find(line)
            when {
                timing != null -> {
                    flush(dropIndex = true)
                    val g = timing.groupValues
                    start = millis(g[1], g[2], g[3], g[4])
                    end = millis(g[5], g[6], g[7], g[8])
                    open = true
                }
                !open -> Unit // üst bilgi, numara, NOTE/STYLE blokları
                line.isBlank() -> flush(dropIndex = false)
                else -> {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(line)
                }
            }
        }
        flush(dropIndex = false)
        return out
    }

    private fun millis(h: String, m: String, s: String, frac: String): Long {
        val hours = h.toLongOrNull() ?: 0L
        // ".5" yarım saniyedir, ".005" değil: kesir sağdan sıfırla üç haneye tamamlanır.
        val ms = frac.padEnd(3, '0').take(3).toLong()
        return ((hours * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + ms
    }

    // ---- ASS / SSA ----

    private val DEFAULT_SSA_FORMAT = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
    private val SSA_TIME = Regex("""(\d+):(\d{1,2}):(\d{2})[.:](\d{1,3})""")

    private fun parseSsa(text: String): List<SubtitleCue> {
        var inEvents = false
        var format = DEFAULT_SSA_FORMAT
        val out = ArrayList<SubtitleCue>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim().trimStart('﻿')
            when {
                line.startsWith("[") -> inEvents = line.equals("[Events]", ignoreCase = true)
                !inEvents -> Unit
                line.startsWith("Format:", ignoreCase = true) ->
                    format = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                line.startsWith("Dialogue:", ignoreCase = true) -> {
                    val fields = line.substringAfter(':').trimStart().split(',', limit = format.size)
                    val start = ssaTime(fields.getOrNull(format.indexOf("start")))
                    val end = ssaTime(fields.getOrNull(format.indexOf("end")))
                    val raw = fields.getOrNull(format.indexOf("text"))
                    if (start != null && end != null && raw != null) {
                        ssaText(raw)?.let { out += SubtitleCue(start, end, it) }
                    }
                }
            }
        }
        return out
    }

    private fun ssaTime(field: String?): Long? {
        val m = SSA_TIME.find(field?.trim() ?: return null) ?: return null
        val (h, min, s, frac) = m.destructured
        // ASS saniyenin yüzde biri (iki hane) kullanır; millis() kesri sağdan tamamlar: "5" -> 500 ms, "05" -> 50 ms.
        return millis(h, min, s, frac)
    }

    private val SSA_BLOCK = Regex("""\{[^}]*\}""")
    private val SSA_DRAWING = Regex("""\\p[1-9]""")

    /** `{...}` geçersiz kılma blokları (`{\an8}`, `{\i1}`, renk, konum) atılır, `\N` -> satır sonu. Çizim komutları (`\p1`) olan satır atılır. */
    private fun ssaText(raw: String): String? {
        val sb = StringBuilder()
        var last = 0
        for (block in SSA_BLOCK.findAll(raw)) {
            sb.append(raw, last, block.range.first)
            if (SSA_DRAWING.containsMatchIn(block.value)) return null
            last = block.range.last + 1
        }
        sb.append(raw, last, raw.length)
        val text = sb.toString().replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
        return cleanMarkup(text).takeIf { it.isNotBlank() }
    }

    // ---- ortak: işaret temizliği ----

    private val ANY_TAG = Regex("""<[^>]*>""")
    private val SSA_LEFTOVER = Regex("""\{\\[^}]*\}""")
    private val BREAK = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

    /** `<br>` satır sonu olur; her etiket (`<i>`, `<font>`, `<c.x>`, `<v ad>`, zaman damgası) ve `{\an8}` kalıntısı atılır, HTML karakterleri çözülür. */
    internal fun cleanMarkup(raw: String): String {
        val noBraces = SSA_LEFTOVER.replace(BREAK.replace(raw, "\n"), "")
        return ANY_TAG.replace(noBraces, "")
            .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replace("&lrm;", "").replace("&rlm;", "")
            .lines().joinToString("\n") { it.trim() }.trim()
    }
}
