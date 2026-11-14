package com.kaan.play.ui.player
import org.junit.Assert.*
import org.junit.Test
class ProviderSessionHandoffTest {
    @Test fun preservesRuntimeIdentityAndRejectsModifiedPayload() {
        val runtime = ProviderSessionHandoff<Any>()
        val provider = Any()
        val key = runtime.put("episode-one", provider)
        assertSame(provider, runtime.get(key, "episode-one"))
        assertSame(provider, runtime.get(key, "episode-one")) // activity recreation
        assertNull(runtime.get(key, "episode-two"))
    }
    @Test fun boundedAndExpiredSessionsRequireFreshResolution() {
        var now = 0L
        val runtime = ProviderSessionHandoff<Any> { now }
        val first = runtime.put("first", Any())
        repeat(8) { runtime.put("next-$it", Any()) }
        assertNull(runtime.get(first, "first"))
        val latest = runtime.put("latest", Any())
        now = 600_000L
        assertNull(runtime.get(latest, "latest"))
    }
}
