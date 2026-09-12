package com.musicplayer.data.player

import com.musicplayer.data.Curve
import kotlin.math.pow

/**
 * Вычисляет значение громкости (0..1) для заданного прогресса (0..1)
 * по заданной кривой перехода.
 *
 * Адаптировано из DualPlayerEngine (PixelPlay) под существующие модели Curve.
 */
fun envelope(progress: Float, curve: Curve): Float {
    val t = progress.coerceIn(0f, 1f)
    return when (curve) {
        Curve.LINEAR      -> t
        Curve.EASE_IN     -> t.pow(2f)
        Curve.EASE_OUT    -> 1f - (1f - t).pow(2f)
        Curve.EASE_IN_OUT -> if (t < 0.5f) {
            2f * t * t
        } else {
            1f - (-2f * t + 2f).pow(2f) / 2f
        }
    }
}
