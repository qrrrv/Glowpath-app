package com.musicplayer.data

/**
 * Режим перехода между треками.
 */
enum class TransitionMode {
    NONE,    // Без перехода
    OVERLAP  // Кроссфейд (перекрытие)
}

/**
 * Кривая изменения громкости при переходе.
 */
enum class Curve {
    LINEAR,      // Линейная
    EASE_IN,     // Нарастающая
    EASE_OUT,    // Затухающая
    EASE_IN_OUT  // Нарастающая-затухающая
}

/**
 * Настройки перехода между треками.
 * @param mode режим перехода
 * @param durationMs длительность перехода в мс (0..12000)
 * @param curveIn кривая нарастания нового трека
 * @param curveOut кривая затухания текущего трека
 */
data class TransitionSettings(
    val mode: TransitionMode = TransitionMode.NONE,
    val durationMs: Int = 2000,
    val curveIn: Curve = Curve.EASE_IN,
    val curveOut: Curve = Curve.EASE_OUT
)
