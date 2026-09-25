package com.musicplayer.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

/**
 * Порт анимации удаления из Booming Music.
 *
 * В Booming очередь на Compose использует `Modifier.animateItem()` из
 * `sh.calvin.reorderable.ReorderableItem`: строка гаснет, остальные
 * пружиной поднимаются на её место. Плейлисты на RecyclerView делают то же
 * через `RefactoredDefaultItemAnimator` (fade 120 мс, move 250 мс).
 *
 * Стиль 3 в настройках Glowpath («Booming») удаляет трек сразу из списка —
 * дальше отрабатывает этот модификатор. Стили 0–2 (пыль / snap) оставляют
 * частичный эффект, а этот модификатор всё равно сдвигает строки снизу.
 */
object BoomingDeleteAnim {
    const val STYLE = 3

    const val FADE_IN_MS = 220
    const val FADE_OUT_MS = 120
    const val MOVE_MS = 250
}

fun LazyItemScope.boomingDeleteItemModifier(): Modifier {
    return Modifier.animateItem(
        fadeInSpec = tween(BoomingDeleteAnim.FADE_IN_MS, easing = LinearOutSlowInEasing),
        fadeOutSpec = tween(BoomingDeleteAnim.FADE_OUT_MS),
        placementSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = IntOffset(1, 1)
        )
    )
}

fun isBoomingDeleteStyle(style: Int): Boolean = style == BoomingDeleteAnim.STYLE
