package com.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.runtime.derivedStateOf

/**
 * Текст с автопрокруткой — включается только если текст не влезает в ширину.
 * По краям плавный градиент. Прокрутка стартует через 1.5 сек.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoScrollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    textAlign: TextAlign? = null,
    gradientEdgeColor: Color,
    gradientWidth: Dp = 24.dp
) {
    SubcomposeLayout(modifier = modifier.clipToBounds()) { constraints ->
        val textPlaceable = subcompose("measureText") {
            Text(text = text, style = style, maxLines = 1)
        }[0].measure(constraints.copy(maxWidth = Int.MAX_VALUE))

        val isOverflowing = textPlaceable.width > constraints.maxWidth

        val content = @Composable {
            if (isOverflowing) {
                val initialDelayMillis = 1500
                val fadeAnimDuration = 500

                var isScrolling by remember { mutableStateOf(false) }
                LaunchedEffect(text) {
                    isScrolling = false
                    delay(initialDelayMillis.toLong())
                    isScrolling = true
                }

                // Левый градиент плавно пропадает когда начинается прокрутка
                val leftGradientColor by animateColorAsState(
                    targetValue = if (isScrolling) Color.Transparent else gradientEdgeColor,
                    animationSpec = tween(durationMillis = fadeAnimDuration),
                    label = "leftGradient"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val gw = gradientWidth.toPx()
                            // Левый край (пропадает при прокрутке)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(leftGradientColor, gradientEdgeColor),
                                    startX = 0f,
                                    endX = gw
                                ),
                                blendMode = BlendMode.DstIn
                            )
                            // Правый край (всегда виден при переполнении)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(gradientEdgeColor, Color.Transparent),
                                    startX = size.width - gw,
                                    endX = size.width
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    Text(
                        text = text,
                        style = style,
                        textAlign = textAlign,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            spacing = MarqueeSpacing(gradientWidth + 6.dp),
                            velocity = 25.dp,
                            initialDelayMillis = initialDelayMillis
                        )
                    )
                }
            } else {
                Text(
                    text = text,
                    style = style,
                    textAlign = textAlign,
                    maxLines = 1,
                )
            }
        }

        val contentPlaceable = subcompose("content", content)[0].measure(constraints)
        val targetWidth =
            constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: contentPlaceable.width

        layout(targetWidth, contentPlaceable.height) {
            contentPlaceable.place(0, 0)
        }
    }
}

/**
 * AutoScrollingTextOnDemand — прокрутка запускается только когда
 * [expansionFractionProvider] возвращает 1f (например, экран полностью открыт).
 * Если текст помещается — ведёт себя как обычный Text.
 *
 * @param text отображаемый текст
 * @param style стиль текста
 * @param gradientEdgeColor цвет градиента по краям
 * @param expansionFractionProvider лямбда, возвращающая 0..1 (1 = полностью раскрыт)
 */
@Composable
fun AutoScrollingTextOnDemand(
    text: String,
    style: TextStyle,
    gradientEdgeColor: Color,
    expansionFractionProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    var overflow by remember { mutableStateOf(false) }
    val canStart by remember {
        derivedStateOf { expansionFractionProvider() > 0.99f && overflow }
    }

    if (!canStart) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { res -> overflow = res.hasVisualOverflow },
            modifier = modifier
        )
    } else {
        AutoScrollingText(
            text = text,
            style = style,
            textAlign = TextAlign.Start,
            gradientEdgeColor = gradientEdgeColor,
            modifier = modifier
        )
    }
}
