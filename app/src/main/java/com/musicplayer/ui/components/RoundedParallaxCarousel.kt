package com.musicplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Стиль карусели.
 */
object CarouselStyle {
    const val NO_PEEK  = "no_peek"   // Один элемент на весь экран
    const val ONE_PEEK = "one_peek"  // Виден краешек следующего элемента
    const val TWO_PEEK = "two_peek"  // По бокам видны два элемента
}

/**
 * Горизонтальная карусель с параллакс-эффектом и скруглёнными углами.
 *
 * Особенности:
 * - Активный элемент отображается полноразмерным, соседние — уменьшенными (параллакс).
 * - Плавная анимация scale и alpha при прокрутке.
 * - Скруглённые углы у каждого элемента.
 *
 * @param itemCount количество элементов
 * @param style стиль карусели из [CarouselStyle]
 * @param itemCornerRadius радиус скругления углов элементов
 * @param itemSpacing расстояние между элементами
 * @param contentPadding внутренние отступы
 * @param content контент каждого элемента (индекс, isActive — является ли элемент центральным)
 */
@Composable
fun RoundedParallaxCarousel(
    itemCount: Int,
    modifier: Modifier = Modifier,
    style: String = CarouselStyle.ONE_PEEK,
    itemCornerRadius: Dp = 16.dp,
    itemSpacing: Dp = 8.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable (index: Int, isActive: Boolean) -> Unit
) {
    if (itemCount == 0) return

    val pagerState = rememberPagerState(pageCount = { itemCount })

    val pageSize: Float = when (style) {
        CarouselStyle.NO_PEEK  -> 1.0f
        CarouselStyle.ONE_PEEK -> 0.82f
        CarouselStyle.TWO_PEEK -> 0.62f
        else                   -> 0.82f
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        contentPadding = when (style) {
            CarouselStyle.NO_PEEK  -> PaddingValues(horizontal = 0.dp)
            CarouselStyle.ONE_PEEK -> PaddingValues(horizontal = 32.dp)
            CarouselStyle.TWO_PEEK -> PaddingValues(horizontal = 56.dp)
            else                   -> PaddingValues(horizontal = 32.dp)
        },
        pageSpacing = itemSpacing,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(1)
        ),
        beyondViewportPageCount = when (style) {
            CarouselStyle.TWO_PEEK -> 2
            else -> 1
        }
    ) { page ->
        val pageOffset = (pagerState.currentPage - page) +
                pagerState.currentPageOffsetFraction

        // Параллакс: центральный элемент = 1f, соседние уменьшаются
        val scale by animateFloatAsState(
            targetValue = if (abs(pageOffset) < 0.001f) 1f
            else 1f - (0.12f * abs(pageOffset).coerceIn(0f, 1f)),
            animationSpec = tween(250),
            label = "carouselScale_$page"
        )

        val alpha by animateFloatAsState(
            targetValue = if (abs(pageOffset) < 0.001f) 1f
            else 1f - (0.3f * abs(pageOffset).coerceIn(0f, 1f)),
            animationSpec = tween(250),
            label = "carouselAlpha_$page"
        )

        val isActive = pagerState.currentPage == page

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clip(RoundedCornerShape(itemCornerRadius)),
            contentAlignment = Alignment.Center
        ) {
            content(page, isActive)
        }
    }
}
