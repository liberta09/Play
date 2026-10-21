package com.kaan.play.data.web

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Android'den bağımsız, yüksek başarımlı reklam ve izleyici alan adı eşleştiricisi (birim testli).
 *
 * Özellikler:
 * - Alt alan adlarını yukarı doğru tarar (örn. `reklam.x.com`, `x.com` engelliyse engellenir).
 * - Büyük/küçük harf, sondaki noktalar ve baştaki "www." farkını yok sayar.
 * - Hızlıdır: HashSet + alt alan adı yukarı doğru tarama O(k), k = etiket sayısı.
 * - Thread-safe & lock-free okuma: Arka planda liste yüklenirken mevcut [AdDomains.all] kullanılır;
 *   yükleme tamamlandığında liste atomik olarak güncellenir.
 */
class AdHostMatcher(
    initialDomains: Collection<String> = AdDomains.all
) {

    @Volatile
    private var blockedHosts: Set<String> = normalizeAll(initialDomains)

    @Volatile
    var isLoaded: Boolean = false
        private set

    val domainCount: Int get() = blockedHosts.size

    /**
     * [host], listedeki bir alan adının kendisi ya da alt alan adıysa true döner.
     * Baştaki "www." ve büyük/küçük harf duyarsızdır.
     */
    fun isBlocked(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        var h = normalize(host)
        if (h.isEmpty()) return false

        val current = blockedHosts
        while (true) {
            if (h in current) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    /**
     * [reader] üzerinden satır satır alan adlarını okur, [AdDomains.all] ile birleştirir
     * ve eşleştiriciyi atomik olarak günceller.
     */
    fun loadFromReader(reader: Reader) {
        val merged = HashSet<String>(blockedHosts.size + 60000)
        // Mevcut yerleşik AdDomains listesi her zaman korunur ve birleştirilir
        for (d in AdDomains.all) {
            val norm = normalize(d)
            if (norm.isNotEmpty()) {
                merged.add(norm)
            }
        }

        BufferedReader(reader).useLines { lines ->
            for (raw in lines) {
                val domain = extractDomain(raw)
                if (domain != null && domain.isNotEmpty()) {
                    val norm = normalize(domain)
                    if (norm.isNotEmpty()) {
                        merged.add(norm)
                    }
                }
            }
        }

        blockedHosts = merged
        isLoaded = true
    }

    fun loadFromStream(stream: InputStream) {
        loadFromReader(InputStreamReader(stream, StandardCharsets.UTF_8))
    }

    fun loadFromLines(lines: Sequence<String>) {
        val merged = HashSet<String>(blockedHosts.size + 60000)
        for (d in AdDomains.all) {
            val norm = normalize(d)
            if (norm.isNotEmpty()) {
                merged.add(norm)
            }
        }
        for (raw in lines) {
            val domain = extractDomain(raw)
            if (domain != null && domain.isNotEmpty()) {
                val norm = normalize(domain)
                if (norm.isNotEmpty()) {
                    merged.add(norm)
                }
            }
        }
        blockedHosts = merged
        isLoaded = true
    }

    companion object {
        /** Uygulama genelinde paylaşılan varsayılan eşleştirici. */
        val default: AdHostMatcher = AdHostMatcher()

        /**
         * Alan adını normalleştirir: boşlukları kırpar, küçük harfe çevirir,
         * sondaki noktaları ve baştaki "www." önekini kaldırır.
         */
        fun normalize(host: String): String {
            var h = host.trim().lowercase(Locale.ROOT).trimEnd('.')
            if (h.startsWith("www.")) {
                h = h.removePrefix("www.")
            }
            return h
        }

        private fun normalizeAll(domains: Collection<String>): Set<String> {
            val set = HashSet<String>(domains.size)
            for (d in domains) {
                val norm = normalize(d)
                if (norm.isNotEmpty()) {
                    set.add(norm)
                }
            }
            return set
        }

        /**
         * Satırdan alan adını çıkarır.
         * - ||alanadi^ biçimindeki kurallardan alan adını ayıklar.
         * - Düz alan adlarını kabul eder.
         * - İstisna kurallarını (@@), element gizleme kurallarını (##), yorumları (# veya !) yok sayar (null döner).
         */
        fun extractDomain(line: String): String? {
            var l = line.trim()
            if (l.isEmpty() || l.startsWith('#') || l.startsWith('!') || l.startsWith("@@") || l.contains("##") || l.contains("#?#") || l.contains("#@#")) {
                return null
            }
            if (l.startsWith("||")) {
                l = l.removePrefix("||")
                val caret = l.indexOf('^')
                if (caret >= 0) {
                    l = l.substring(0, caret)
                }
            }
            val norm = normalize(l)
            return if (isValidDomain(norm)) norm else null
        }

        private fun isValidDomain(d: String): Boolean {
            if (d.isEmpty() || !d.contains('.') || d.contains('/') || d.contains(':') || d.contains('*')) {
                return false
            }
            return true
        }
    }
}
