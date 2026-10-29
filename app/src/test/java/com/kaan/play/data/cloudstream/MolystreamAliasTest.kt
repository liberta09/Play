package com.kaan.play.data.cloudstream

import com.kaan.play.data.cloudstream.extractors.DbxMolystream
import com.kaan.play.data.cloudstream.extractors.MolyTo
import com.kaan.play.data.cloudstream.extractors.Molystream
import org.junit.Assert.assertEquals
import org.junit.Test

class MolystreamAliasTest {
    @Test
    fun aliasesUseVidmolyExtractorForEachSupportedHost() {
        val aliases = listOf(Molystream(), DbxMolystream(), MolyTo())
        assertEquals(
            listOf(
                "https://molystream.org",
                "https://dbx.molystream.org",
                "https://moly.to",
            ),
            aliases.map { it.mainUrl },
        )
        assertEquals(listOf("Molystream", "Molystream", "Molystream"), aliases.map { it.name })
    }
}
