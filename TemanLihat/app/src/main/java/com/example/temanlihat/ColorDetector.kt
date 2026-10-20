package com.example.temanlihat

import kotlin.math.max
import kotlin.math.min

internal data class DetectedColor(val red: Int, val green: Int, val blue: Int) {
    val argb: Int get() = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    val hex: String get() = "#%02X%02X%02X".format(red, green, blue)
    val name: String get() {
        val high = max(red, max(green, blue)).toFloat()
        val low = min(red, min(green, blue)).toFloat()
        val delta = high - low
        val saturation = if (high == 0f) 0f else delta / high
        if (high < 45) return "Hitam"
        if (saturation < 0.12f) return if (high > 220) "Putih" else "Abu-abu"
        var hue = when (high.toInt()) {
            red -> 60f * ((green - blue) / delta % 6)
            green -> 60f * ((blue - red) / delta + 2)
            else -> 60f * ((red - green) / delta + 4)
        }
        if (hue < 0) hue += 360
        return when {
            hue < 15 || hue >= 345 -> if (high > 180 && saturation < 0.65f) "Merah muda" else "Merah"
            hue < 45 -> if (high < 180) "Cokelat" else "Jingga"
            hue < 70 -> "Kuning"
            hue < 165 -> "Hijau"
            hue < 195 -> "Sian"
            hue < 255 -> "Biru"
            hue < 290 -> "Ungu"
            else -> if (high > 180 && saturation < 0.65f) "Merah muda" else "Magenta"
        }
    }
}

/** Most frequent quantized color, so two different colors are not averaged into a third. */
internal fun dominantColor(pixels: IntArray): DetectedColor {
    val visible = pixels.filter { (it ushr 24) >= 128 }
    require(visible.isNotEmpty()) { "Area gambar transparan" }
    val groups = visible.groupBy {
        (((it shr 16) and 255) / 32 shl 6) or
            (((it shr 8) and 255) / 32 shl 3) or ((it and 255) / 32)
    }
    val dominant = groups.values.maxBy { it.size }
    return DetectedColor(
        dominant.sumOf { (it shr 16) and 255 } / dominant.size,
        dominant.sumOf { (it shr 8) and 255 } / dominant.size,
        dominant.sumOf { it and 255 } / dominant.size
    )
}
