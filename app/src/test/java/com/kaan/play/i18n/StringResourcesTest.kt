package com.kaan.play.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** values/ (İngilizce) ve values-tr/ aynı anahtarlara sahip olmalı: çeviri unutulmasın. */
class StringResourcesTest {

    private val resDir = File("src/main/res")

    private fun translatableKeys(file: File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) as org.w3c.dom.Element }
            .filter { it.getAttribute("translatable") != "false" }
            .map { it.getAttribute("name") }
            .toSet()
    }

    @Test
    fun turkishHasSameKeysAsDefault() {
        val en = translatableKeys(File(resDir, "values/strings.xml"))
        val tr = translatableKeys(File(resDir, "values-tr/strings.xml"))
        assertTrue(en.isNotEmpty())
        assertEquals("Eksik/fazla çeviri anahtarı", en, tr)
    }

    /** Ek string dosyaları (strings_*.xml) ve TV çeşidinin kaynakları (src/tv/res) da iki dilde aynı anahtarlara sahip olmalı. */
    @Test
    fun everyStringFileAndTvFlavorHaveTurkish() {
        for (dir in listOf(File("src/main/res"), File("src/tv/res"))) {
            if (!dir.exists()) continue
            fun keysOf(values: String) = File(dir, values).listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }
                .orEmpty().flatMap { translatableKeys(it) + pluralKeys(it) }.toSet()
            val en = keysOf("values")
            val tr = keysOf("values-tr")
            assertTrue(en.isNotEmpty())
            assertEquals("Eksik/fazla çeviri anahtarı (${dir.path})", en, tr)
        }
    }

    private fun pluralKeys(file: File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("plurals")
        return (0 until nodes.length).map { "plurals:" + (nodes.item(it) as org.w3c.dom.Element).getAttribute("name") }.toSet()
    }
}
