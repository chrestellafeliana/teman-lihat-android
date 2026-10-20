package com.example.temanlihat

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorDetectorTest {
    @Test fun namesPrimaryAndNeutralColorsInIndonesian() {
        val examples = listOf(
            DetectedColor(255, 0, 0) to "Merah", DetectedColor(0, 255, 0) to "Hijau",
            DetectedColor(0, 0, 255) to "Biru", DetectedColor(255, 255, 0) to "Kuning",
            DetectedColor(0, 0, 0) to "Hitam", DetectedColor(255, 255, 255) to "Putih",
            DetectedColor(128, 128, 128) to "Abu-abu", DetectedColor(255, 165, 0) to "Jingga",
            DetectedColor(139, 69, 19) to "Cokelat", DetectedColor(255, 192, 203) to "Merah muda"
        )
        examples.forEach { (color, name) -> assertEquals(name, color.name) }
    }

    @Test fun mixedAreaUsesDominantColorRatherThanInventingAverageColor() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        assertEquals("Merah", dominantColor(intArrayOf(red, red, red, blue)).name)
    }

    @Test fun transparentPixelsAreIgnored() {
        assertEquals("Biru", dominantColor(intArrayOf(0, 0, 0xFF0000FF.toInt())).name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun transparentImageIsRejected() { dominantColor(intArrayOf(0)) }

    @Test fun hexAndArgbMatchSample() {
        val color = DetectedColor(255, 128, 0)
        assertEquals("#FF8000", color.hex)
        assertEquals(0xFFFF8000.toInt(), color.argb)
    }
}
