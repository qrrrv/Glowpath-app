package com.musicplayer.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Кастомный скроллбар с анимацией расширения при касании.
 * Поддерживает LazyListState и LazyGridState.
 *
 * Пример использования:
 *   Box {
 *       LazyColumn(state = listState) { ... }
 *       ExpressiveScrollBar(listState = listState, modifier = Modifier.align(Alignment.CenterEnd))
 *   }
 */
@Composable
fun ExpressiveScrollBar(
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    gridState: LazyGridState? = null,
    minHeight: Dp = 48.dp,
    thickness: Dp = 8.dp,
    indicatorExpandedWidth: Dp = 24.dp,
    paddingEnd: Dp = 4.dp,
    trackGap: Dp = 8.dp
) {
    val coroutineScope = rememberCoroutineScope()

    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    var pendingScrollIndex by remember { mutableIntStateOf(-1) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.secondaryContainer
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    val isInteracting = isPressed || isDragging

    val animatedWidth by animateDpAsState(
        targetValue = if (isInteracting) indicatorExpandedWidth else thickness,
        animationSpec = tween(durationMillis = 200),
        label = "WidthAnimation"
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "IconAlpha"
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxHeight().width(indicatorExpandedWidth + paddingEnd)
    ) {
        val density = LocalDensity.current
        val constraintsMaxWidth = maxWidth
        val constraintsMaxHeight = maxHeight

        val canScrollForward by remember {
            derivedStateOf { listState?.canScrollForward ?: gridState?.canScrollForward ?: false }
        }
        val canScrollBackward by remember {
            derivedStateOf { listState?.canScrollBackward ?: gridState?.canScrollBackward ?: false }
        }

        if (!canScrollForward && !canScrollBackward) return@BoxWithConstraints

        fun getScrollStats(): Triple<Float, Int, Float> {
            val total: Int; val first: Int; val visible: Int
            if (listState != null) {
                val li = listState.layoutInfo
                total = li.totalItemsCount; first = listState.firstVisibleItemIndex; visible = li.visibleItemsInfo.size
            } else if (gridState != null) {
                val li = gridState.layoutInfo
                total = li.totalItemsCount; first = gridState.firstVisibleItemIndex; visible = li.visibleItemsInfo.size
            } else return Triple(0f, 0, 1f)

            if (total == 0) return Triple(0f, 0, 1f)
            val maxIdx = (total - visible).coerceAtLeast(1)
            val fwd = listState?.canScrollForward ?: gridState?.canScrollForward ?: false
            val bwd = listState?.canScrollBackward ?: gridState?.canScrollBackward ?: false
            val progress = when { !fwd -> 1f; !bwd -> 0f; else -> (first.toFloat() / maxIdx.toFloat()).coerceIn(0f, 0.99f) }
            val availH = with(density) { constraintsMaxHeight.toPx() }
            val handleH = with(density) { minHeight.toPx() }
            return Triple(progress, total, (availH - handleH).coerceAtLeast(1f))
        }

        fun updateProgressFromTouch(touchY: Float, grabOffset: Float) {
            val (_, total, scrollableH) = getScrollStats()
            val newProgress = ((touchY - grabOffset) / scrollableH).coerceIn(0f, 1f)
            dragProgress = newProgress
            pendingScrollIndex = (newProgress * total).toInt()
        }

        LaunchedEffect(Unit) {
            snapshotFlow { pendingScrollIndex }.collect { index ->
                if (index >= 0) {
                    listState?.scrollToItem(index)
                    gridState?.scrollToItem(index)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        isPressed = true
                        try { awaitRelease() } finally { isPressed = false }
                    })
                }
                .pointerInput(Unit) {
                    var grabOffset = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val (realP, _, scrollH) = getScrollStats()
                            val handleH = with(density) { minHeight.toPx() }
                            val dispP = if (dragProgress >= 0f) dragProgress else realP
                            val handleY = dispP * scrollH
                            grabOffset = if (offset.y in handleY..(handleY + handleH)) offset.y - handleY else handleH / 2f
                            updateProgressFromTouch(offset.y, grabOffset)
                        },
                        onDragEnd = { isDragging = false; dragProgress = -1f },
                        onDragCancel = { isDragging = false; dragProgress = -1f },
                        onDrag = { change, _ -> change.consume(); updateProgressFromTouch(change.position.y, grabOffset) }
                    )
                }
        ) {
            val rightAnchorX = with(density) { (constraintsMaxWidth - paddingEnd).toPx() }
            val trackX = rightAnchorX - with(density) { thickness.toPx() / 2 }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val (realP, _, scrollH) = getScrollStats()
                val dispP = if (isDragging && dragProgress >= 0f) dragProgress else realP
                val handleY = dispP * scrollH
                val handleH = minHeight.toPx()
                val indicW = animatedWidth.toPx()
                val gapPx = trackGap.toPx()
                val trackStroke = thickness.toPx()
                val indicX = rightAnchorX - indicW

                if (handleY > gapPx) {
                    drawLine(color = surfaceVariantColor, start = Offset(trackX, 0f),
                        end = Offset(trackX, handleY - gapPx), strokeWidth = trackStroke, cap = StrokeCap.Round)
                }
                if (handleY + handleH + gapPx < size.height) {
                    drawLine(color = surfaceVariantColor, start = Offset(trackX, handleY + handleH + gapPx),
                        end = Offset(trackX, size.height), strokeWidth = trackStroke, cap = StrokeCap.Round)
                }
                drawRoundRect(color = primaryColor, topLeft = Offset(indicX, handleY),
                    size = Size(indicW, handleH), cornerRadius = CornerRadius(indicW / 2f, indicW / 2f))
            }

            if (iconAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .offset {
                            val (realP, _, scrollH) = getScrollStats()
                            val dispP = if (isDragging && dragProgress >= 0f) dragProgress else realP
                            val handleY = dispP * scrollH
                            val handleH = with(density) { minHeight.toPx() }
                            val iconSize = with(density) { 24.dp.toPx() }
                            val pePx = with(density) { paddingEnd.toPx() }
                            val animWPx = with(density) { animatedWidth.toPx() }
                            val maxWPx = with(density) { constraintsMaxWidth.toPx() }
                            IntOffset(
                                (maxWPx - pePx - animWPx / 2 - iconSize / 2).toInt(),
                                (handleY + handleH / 2 - iconSize / 2).toInt()
                            )
                        }
                        .size(24.dp)
                        .graphicsLayer { alpha = iconAlpha; scaleX = iconAlpha; scaleY = iconAlpha }
                ) {
                    Icon(imageVector = Icons.Rounded.UnfoldMore, contentDescription = null,
                        tint = onPrimaryColor, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
