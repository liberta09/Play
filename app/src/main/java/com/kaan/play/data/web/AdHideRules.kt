package com.kaan.play.data.web

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Android'den bağımsız, yüksek başarımlı reklam element gizleme kuralları ayrıştırıcısı ve eşleştiricisi.
 *
 * Kuralları [adhide.txt] dosyasından okur:
 * - Genel kurallar (`##seçici`): Tüm alan adlarına uygulanır.
 * - Alan adına özgü kurallar (`alanadi.com##seçici` veya `a.com,b.com##seçici`): Yalnızca eşleşen alan adı ve
 *   onun alt alan adlarına uygulanır (üst alan adı kuralları devralınır).
 * - Joker karakterli kurallar (`hdfilmcehennemi.*##seçici`): Eşleşen alan adlarına uygulanır.
 *
 * Thread-safe ve lock-free okuma desteği sağlar.
 */
class AdHideRules {

    @Volatile
    private var genericRules: List<String> = emptyList()

    @Volatile
    private var exactDomainRules: Map<String, List<String>> = emptyMap()

    @Volatile
    private var wildcardDomainRules: List<Pair<WildcardMatcher, List<String>>> = emptyList()

    @Volatile
    var isLoaded: Boolean = false
        private set

    val genericRuleCount: Int get() = genericRules.size

    val domainRuleCount: Int
        get() = exactDomainRules.values.sumOf { it.size } + wildcardDomainRules.sumOf { it.second.size }

    /**
     * Verilen alan adı için geçerli tüm seçicileri döndürür:
     * Genel kurallar + o alan adı ve üst alan adlarına ait kurallar + joker eşleşmeleri.
     * Liste henüz yüklenmediyse boş liste döner.
     */
    fun getSelectorsForHost(host: String?): List<String> {
        if (!isLoaded) return emptyList()

        val result = LinkedHashSet<String>(genericRules.size + 64)
        result.addAll(genericRules)

        if (!host.isNullOrBlank()) {
            val normHost = normalize(host)
            if (normHost.isNotEmpty()) {
                // 1. Tam alan adı ve üst alan adları (sub.example.com -> sub.example.com, example.com)
                var cur = normHost
                while (cur.isNotEmpty()) {
                    exactDomainRules[cur]?.let { result.addAll(it) }
                    val dot = cur.indexOf('.')
                    if (dot < 0) break
                    cur = cur.substring(dot + 1)
                }

                // 2. Joker kuralları (örn. hdfilmcehennemi.*)
                for ((matcher, selectors) in wildcardDomainRules) {
                    if (matcher.matches(normHost)) {
                        result.addAll(selectors)
                    }
                }
            }
        }

        return result.toList()
    }

    /**
     * Yalnızca verilen alan adına (ve üst alan adlarına) özgü seçicileri döndürür (genel kurallar hariç).
     * Testlerde ve çerçeveye özel doğrulamalarda kullanılır.
     */
    fun getDomainSelectorsForHost(host: String?): List<String> {
        if (!isLoaded || host.isNullOrBlank()) return emptyList()
        val normHost = normalize(host)
        if (normHost.isEmpty()) return emptyList()

        val result = LinkedHashSet<String>()

        // 1. Tam alan adı ve üst alan adları
        var cur = normHost
        while (cur.isNotEmpty()) {
            exactDomainRules[cur]?.let { result.addAll(it) }
            val dot = cur.indexOf('.')
            if (dot < 0) break
            cur = cur.substring(dot + 1)
        }

        // 2. Joker kuralları
        for ((matcher, selectors) in wildcardDomainRules) {
            if (matcher.matches(normHost)) {
                result.addAll(selectors)
            }
        }

        return result.toList()
    }

    /**
     * Verilen alan adı için tek bir CSS bloğu üretir:
     * `seçiciler { display: none !important; }`
     * Hiç seçici yoksa null döner.
     */
    fun buildCssForHost(host: String?): String? {
        val selectors = getSelectorsForHost(host)
        if (selectors.isEmpty()) return null
        return selectors.joinToString(",\n") + " { display: none !important; }"
    }

    /**
     * Satır satır kuralları okur ve eşleştiriciyi günceller.
     */
    fun loadFromLines(lines: Sequence<String>) {
        val generic = LinkedHashSet<String>()
        val exactMap = HashMap<String, MutableList<String>>()
        val wildcardMap = HashMap<String, MutableList<String>>()

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('!') || line.startsWith('[')) {
                continue
            }
            // Yorum satırlarını atla; '##' veya alanadi## kurallarını koru
            if (line == "#" || line.startsWith("# ") || line.startsWith("#\t")) {
                continue
            }
            // İstisna (#@#) ve betik/öznitelik (#?#, #$#, #%#) kuralları hariç
            if (line.contains("#@#") || line.contains("#?#") || line.contains("#$#") || line.contains("#%#")) {
                continue
            }
            val hashIdx = line.indexOf("##")
            if (hashIdx < 0) {
                continue
            }

            val domPart = line.substring(0, hashIdx).trim()
            val selector = line.substring(hashIdx + 2).trim()
            if (selector.isEmpty() || selector.startsWith("+js(") || selector.startsWith(":-abp-")) {
                continue
            }

            if (domPart.isEmpty()) {
                generic.add(selector)
            } else {
                for (rawDomain in domPart.split(',')) {
                    val d = rawDomain.trim()
                    if (d.isEmpty() || d.startsWith('~')) {
                        continue
                    }
                    val norm = normalize(d)
                    if (norm.isEmpty()) continue

                    if (norm.contains('*')) {
                        wildcardMap.getOrPut(norm) { mutableListOf() }.add(selector)
                    } else {
                        exactMap.getOrPut(norm) { mutableListOf() }.add(selector)
                    }
                }
            }
        }

        val wildcardList = wildcardMap.map { (pattern, selectors) ->
            WildcardMatcher(pattern) to selectors.toList()
        }

        genericRules = generic.toList()
        exactDomainRules = exactMap.mapValues { it.value.toList() }
        wildcardDomainRules = wildcardList
        isLoaded = true
    }

    fun loadFromReader(reader: Reader) {
        BufferedReader(reader).useLines { lines ->
            loadFromLines(lines)
        }
    }

    fun loadFromStream(stream: InputStream) {
        loadFromReader(InputStreamReader(stream, StandardCharsets.UTF_8))
    }

    class WildcardMatcher(val pattern: String) {
        private val regex: Regex = run {
            val sb = StringBuilder("^(?:[a-z0-9.-]+\\.)?")
            val parts = pattern.split('*')
            for (i in parts.indices) {
                if (i > 0) {
                    sb.append("[a-z0-9.-]*")
                }
                val part = parts[i]
                if (part.isNotEmpty()) {
                    sb.append(Regex.escape(part))
                }
            }
            sb.append("$")
            Regex(sb.toString())
        }

        fun matches(host: String): Boolean = regex.matches(host)
    }

    companion object {
        /** Uygulama genelinde paylaşılan varsayılan kural deposu. */
        val default: AdHideRules = AdHideRules()

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
    }
}
