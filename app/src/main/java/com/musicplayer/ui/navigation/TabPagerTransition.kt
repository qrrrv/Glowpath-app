package com.musicplayer.ui.navigation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import kotlin.math.absoluteValue

/**
 * Переход между вкладками как в AniSync (Material 3 Shared Axis X).
 *
 * В AniSync главные вкладки едут не на всю ширину экрана, а только на 20%,
 * и при этом растворяются (fade). Из-за этого страницы как будто
 * перелистываются друг через друга, а не просто сдвигаются целиком.
 *
 * Glowpath держит вкладки в HorizontalPager (свайп остаётся), поэтому
 * дефолтный 100% слайд пейджера отменяется через graphicsLayer и
 * заменяется тем же 20% + fade.
 *
 * Куда класть:
 *   app/src/main/java/com/musicplayer/ui/navigation/TabPagerTransition.kt
 */
object TabPagerTransition {
    /** Как `sharedAxisOffsetFraction` в AniSync NavHost. */
    const val SHARED_AXIS_FRACTION = 0.20f

    /**
     * Снап при тапе по нижней навигации.
     * AniSync использует motionScheme.fastSpatialSpec (expressive spring).
     */
    val snapSpec: AnimationSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = 800f
    )
}

/**
 * Навесь на каждую страницу HorizontalPager:
 *
 * ```
 * HorizontalPager(state = pagerState, ...) { page ->
 *     Box(Modifier.fillMaxSize().aniSyncSharedAxisPage(page, pagerState)) {
 *         // контент вкладки
 *     }
 * }
 * ```
 */
fun Modifier.aniSyncSharedAxisPage(
    page: Int,
    pagerState: PagerState,
    offsetFraction: Float = TabPagerTransition.SHARED_AXIS_FRACTION,
): Modifier {
    val pageOffset =
        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
    return this
        .zIndex(1f - pageOffset.absoluteValue)
        .graphicsLayer {
            // Педжер уже сдвинул страницу на 100% ширины.
            // Возвращаем почти всё назад и оставляем только 20% Shared Axis X.
            translationX = pageOffset * size.width * (1f - offsetFraction)
            alpha = (1f - pageOffset.absoluteValue).coerceIn(0f, 1f)
        }
}
