package com.kaan.play.ui.home.slider

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kaan.play.ui.home.MatchboxGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SliderMathTest {
    private val eps = 1e-4f

    @Test fun cyclicOffsetAndIndex() {
        assertEquals(0f, SliderMath.offset(3, 3f, 10), eps)
        assertEquals(-1f, SliderMath.offset(9, 0f, 10), eps)
        assertEquals(1f, SliderMath.offset(0, 9f, 10), eps)
        assertEquals(9, SliderMath.index(-1f, 10))
        assertEquals(0, SliderMath.index(10.2f, 10))
    }

    @Test fun swipeThresholdAndFling() {
        assertEquals(0f, SliderMath.settleTarget(0f, 0.2f, 0f), eps)
        assertEquals(1f, SliderMath.settleTarget(0f, 0.34f, 0f), eps)
        assertEquals(-1f, SliderMath.settleTarget(0f, -0.4f, 0f), eps)
        assertEquals(1f, SliderMath.settleTarget(0f, 0.05f, 1.5f), eps)
        assertEquals(3f, SliderMath.settleTarget(0f, 2.7f, 0f), eps)
    }

    @Test fun tapSidesAndCounter() {
        assertEquals(1, SliderMath.tapStep(120f, 100f))
        assertEquals(-1, SliderMath.tapStep(-120f, 100f))
        assertEquals(0, SliderMath.tapStep(40f, 100f))
        assertEquals("02" to " / 06", SliderMath.counter(1, 6))
        assertEquals(4_500L, SliderMath.AUTOPLAY_MS)
    }

    @Test fun sliderTableFollowsTheTheme() {
        // Açık tema (Play 1.2.6 açık yüzeyleri): krem ve açık.
        val (lf, ln) = SliderColors.table(Color(0xFFFFFFFF), Color(0xFFE2EAF2))
        assertTrue(lf.luminance() > 0.6f && ln.luminance() > 0.7f)
        assertTrue("ılık krem", lf.red > lf.blue)
        // Koyu tema: koyu kalır.
        val (df, dn) = SliderColors.table(Color(0xFF0A0D14), Color(0xFF101420))
        assertTrue(df.luminance() < 0.05f && dn.luminance() < 0.05f)
        // Okyanus mavisi: koyu kalır, mavi tonu korunur.
        val (of, _) = SliderColors.table(Color(0xFF081B33), Color(0xFF0B2340))
        assertTrue(of.luminance() < 0.05f && of.blue > of.red)
    }

    @Test fun openMatchboxSleevesAndText() {
        assertEquals(6, OpenMatchboxGeometry.SLEEVES.size)
        assertEquals(OpenMatchboxGeometry.sleeveColor(0), OpenMatchboxGeometry.sleeveColor(6))
        assertEquals("NO. 02", OpenMatchboxGeometry.number(1))
        assertEquals(28f, OpenMatchboxGeometry.titleSize("Halo"), eps)
        assertTrue(OpenMatchboxGeometry.titleSize("Sürgünün Süpermarketi") < 28f)
        assertEquals(15f, OpenMatchboxGeometry.titleSize("Aaaaaaaaaaaaaaaaaaaaaaaa"), eps)
        assertEquals(0f, OpenMatchboxGeometry.sideWidth(0f), eps)
        assertEquals(OpenMatchboxGeometry.SIDE, OpenMatchboxGeometry.sideWidth(-2f), eps)
        // Sahne: kılıf + çekmece önü + açık çekmece sığar.
        assertTrue(OpenMatchboxGeometry.SCENE_H > MatchboxGeometry.TOP + MatchboxGeometry.SLEEVE_H + MatchboxGeometry.OPEN)
    }
}
