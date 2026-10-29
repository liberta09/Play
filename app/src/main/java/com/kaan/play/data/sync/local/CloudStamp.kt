package com.kaan.play.data.sync.local

/**
 * Supabase `timestamptz` metnini (ör. `2026-10-28T09:22:43.123456+00:00`, `…Z`, boşluklu biçim) Unix mikrosaniyesine
 * çevirir. minSdk 24'te `java.time` olmadığı için elle; çözülemezse null (damgasız sayılır).
 */
object CloudStamp {

    private val PATTERN = Regex(
        """^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}(?::?\d{2})?)?$""",
    )

    fun parseMicros(text: String): Long? {
        val m = PATTERN.matchEntire(text.trim()) ?: return null
        val (y, mo, d, h, mi, s) = m.destructured
        val fraction = m.groupValues[7]
        val zone = m.groupValues[8]
        val month = mo.toInt()
        val day = d.toInt()
        if (month !in 1..12 || day !in 1..31 || h.toInt() > 23 || mi.toInt() > 59 || s.toInt() > 60) return null
        val days = daysFromCivil(y.toInt(), month, day)
        var seconds = days * 86_400L + h.toInt() * 3_600L + mi.toInt() * 60L + s.toInt()
        if (zone.isNotEmpty() && zone != "Z") {
            val sign = if (zone[0] == '-') -1 else 1
            val digits = zone.substring(1).replace(":", "")
            val offH = digits.take(2).toInt()
            val offM = digits.drop(2).ifEmpty { "0" }.toInt()
            seconds -= sign * (offH * 3_600L + offM * 60L)
        }
        val micros = fraction.padEnd(6, '0').take(6).ifEmpty { "0" }.toLong()
        return seconds * 1_000_000L + micros
    }

    /** Proleptik Gregoryen takvimde 1970-01-01'den bu yana gün (H. Hinnant). */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }
}
