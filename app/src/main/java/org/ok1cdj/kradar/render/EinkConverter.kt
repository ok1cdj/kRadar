package org.ok1cdj.kradar.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Converts a RainViewer PNG tile into a quantized greyscale overlay for e-ink.
 *
 * Empirically (sampling real scheme-2 tiles), RainViewer's widget tiles encode
 * precipitation intensity in the **alpha channel**: light rain ≈ alpha 20, heavy
 * rain ≈ alpha 190–255, rising monotonically; the RGB palette itself is not a
 * reliable intensity signal (it ramps tan→yellow with an occasional cyan band).
 * So we drive intensity from alpha and ignore colour entirely — which also makes
 * this independent of whatever colour scheme the tile uses.
 *
 * Mapping: light rain → light grey, heavy rain → solid black. Intensity is
 * quantized to [NUM_LEVELS] discrete opacities of black over the map, so the base
 * map shows through light rain and is fully covered under heavy rain. Discrete
 * steps (not a smooth gradient) keep e-ink redraws minimal.
 *
 * The constants below are the tuning knobs — adjust after seeing tiles on device.
 */
object EinkConverter {
    /** Number of distinct rain intensity shades. */
    const val NUM_LEVELS = 5

    /** Input alpha below this is "no rain" (fully transparent). Lightest real rain ≈ 20. */
    const val MASK_THRESHOLD = 12

    /** Input alpha treated as full intensity; the tan ramp tops out around here. */
    const val INPUT_ALPHA_MAX = 200

    /** Output opacity of the lightest / heaviest rain shade (0..255) of black. */
    private const val ALPHA_MIN = 45   // light rain -> light grey
    private const val ALPHA_MAX = 255  // heavy rain -> solid black

    // Precomputed black-with-alpha value for each level 0..NUM_LEVELS-1.
    private val levelColors: IntArray = IntArray(NUM_LEVELS) { i ->
        val a = if (NUM_LEVELS == 1) ALPHA_MAX
        else ALPHA_MIN + (ALPHA_MAX - ALPHA_MIN) * i / (NUM_LEVELS - 1)
        a shl 24 // black (RGB 0), alpha a
    }

    private const val SPAN = (INPUT_ALPHA_MAX - MASK_THRESHOLD).toFloat()

    /** Decode [png] and return a quantized greyscale overlay bitmap (ARGB_8888). */
    fun toEink(png: ByteArray): Bitmap? {
        val src = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        src.recycle()

        for (i in pixels.indices) {
            val alpha = (pixels[i] ushr 24) and 0xFF
            if (alpha < MASK_THRESHOLD) {
                pixels[i] = 0 // transparent: no rain
                continue
            }
            // Intensity straight from alpha: more opaque = heavier rain.
            val intensity = ((alpha - MASK_THRESHOLD) / SPAN).coerceIn(0f, 1f)
            var level = (intensity * NUM_LEVELS).toInt()
            if (level >= NUM_LEVELS) level = NUM_LEVELS - 1
            pixels[i] = levelColors[level]
        }

        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
