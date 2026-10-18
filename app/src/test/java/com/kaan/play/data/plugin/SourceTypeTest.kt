package com.kaan.play.data.plugin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceTypeTest {

    @Test
    fun `each selection accepts only its corresponding manifest kind`() {
        assertTrue(SourceType.CLOUDSTREAM.accepts(ManifestKind.External))
        assertTrue(SourceType.NUVIO_ADDON.accepts(ManifestKind.Addon))
        assertTrue(SourceType.NUVIO_PLUGIN.accepts(ManifestKind.PluginRepo))

        assertFalse(SourceType.CLOUDSTREAM.accepts(ManifestKind.Addon))
        assertFalse(SourceType.CLOUDSTREAM.accepts(ManifestKind.PluginRepo))
        assertFalse(SourceType.NUVIO_ADDON.accepts(ManifestKind.External))
        assertFalse(SourceType.NUVIO_ADDON.accepts(ManifestKind.PluginRepo))
        assertFalse(SourceType.NUVIO_PLUGIN.accepts(ManifestKind.External))
        assertFalse(SourceType.NUVIO_PLUGIN.accepts(ManifestKind.Addon))
    }
}
