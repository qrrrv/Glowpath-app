package com.musicplayer.data.player

import com.musicplayer.data.Curve
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private const val HALF_PI = (Math.PI / 2.0).toFloat()

/**
 * Значение нарастания входящего трека (0..1) для прогресса перехода (0..1).
 */
fun fadeIn(progress: Float, curve: Curve): Float {
    val t = progress.coerceIn(0f, 1f)
    return when (curve) {
        Curve.LINEAR -> t
        Curve.EASE_IN -> t.pow(2f)
        Curve.EASE_OUT -> 1f - (1f - t).pow(2f)
        Curve.EASE_IN_OUT -> smoothstep(t)
        Curve.EQUAL_POWER -> sin(t * HALF_PI)
    }
}

/**
 * Значение затухания исходящего трека (1..0) для прогресса перехода (0..1).
 *
 * Для [Curve.EQUAL_POWER] это cos, а не (1 - sin): иначе в середине
 * кроссфейда появляется заметный провал громкости.
 */
fun fadeOut(progress: Float, curve: Curve): Float {
    val t = progress.coerceIn(0f, 1f)
    return when (curve) {
        Curve.EQUAL_POWER -> cos(t * HALF_PI)
        else -> 1f - fadeIn(t, curve)
    }
}

/** Старое имя — оставлено для совместимости. */
fun envelope(progress: Float, curve: Curve): Float = fadeIn(progress, curve)

private fun smoothstep(t: Float): Float = if (t < 0.5f) {
    2f * t * t
} else {
    1f - (-2f * t + 2f).pow(2f) / 2f
}
