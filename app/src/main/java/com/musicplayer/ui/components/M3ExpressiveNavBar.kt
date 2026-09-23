package com.musicplayer.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.ui.theme.LocalAppFontFamily
import kotlin.math.abs

private data class ExpressiveNavDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
)

private val ExpressiveNavDestinations = listOf(
    ExpressiveNavDestination(
        route = "home",
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic,
        label = "Главная"
    ),
    ExpressiveNavDestination(
        route = "albums",
        selectedIcon = Icons.Filled.Album,
        unselectedIcon = Icons.Outlined.Album,
        label = "Коллекции"
    ),
    ExpressiveNavDestination(
        route = "online_search",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
        label = "Поиск"
    )
)

private const val UnselectedWeight = 1f
private const val SelectedWeight = 1.82f
private const val PressBoost = 0.16f
private const val NeighborCompress = 0.09f

/**
 * Material 3 Expressive bottom navigation:
 * connected icon buttons, almost full-width, selected destination expands
 * (neighbors shrink), press morphs width, pleasant haptic on tab change.
 */
@Composable
fun AppBottomNavBar(
    currentRoute: String,
    pagePosition: Float? = null,
    onNavigate: (String) -> Unit
) {
    val destinations = ExpressiveNavDestinations
    val selectedIndex = destinations
        .indexOfFirst { it.route == currentRoute }
        .coerceAtLeast(0)
    val livePosition = pagePosition?.coerceIn(0f, destinations.lastIndex.toFloat())
    val view = LocalView.current
    var pressedIndex by remember { mutableStateOf<Int?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(58.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        destinations.forEachIndexed { index, destination ->
            val rawSelection = if (livePosition != null) {
                (1f - abs(livePosition - index)).coerceIn(0f, 1f)
            } else {
                if (index == selectedIndex) 1f else 0f
            }
            val selection by animateFloatAsState(
                targetValue = rawSelection,
                animationSpec = if (livePosition != null) {
                    snap()
                } else {
                    spring(
                        dampingRatio = 0.72f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                },
                label = "navSelection$index"
            )
            val pressDelta by animateFloatAsState(
                targetValue = when (val pressed = pressedIndex) {
                    index -> PressBoost
                    null -> 0f
                    else -> if (abs(pressed - index) == 1) -NeighborCompress else 0f
                },
                animationSpec = spring(
                    dampingRatio = 0.68f,
                    stiffness = 700f
                ),
                label = "navPress$index"
            )
            val weight = (UnselectedWeight +
                (SelectedWeight - UnselectedWeight) * selection +
                pressDelta)
                .coerceAtLeast(0.72f)

            ExpressiveNavItem(
                destination = destination,
                selection = selection,
                isSelected = index == selectedIndex,
                index = index,
                lastIndex = destinations.lastIndex,
                onPressedChange = { pressed ->
                    pressedIndex = if (pressed) index else {
                        if (pressedIndex == index) null else pressedIndex
                    }
                },
                onClick = {
                    performExpressiveNavHaptic(
                        view = view,
                        strong = destination.route != currentRoute
                    )
                    onNavigate(destination.route)
                },
                modifier = Modifier.weight(weight)
            )
        }
    }
}

@Composable
private fun ExpressiveNavItem(
    destination: ExpressiveNavDestination,
    selection: Float,
    isSelected: Boolean,
    index: Int,
    lastIndex: Int,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val font = LocalAppFontFamily.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        onPressedChange(isPressed)
    }

    val scheme = MaterialTheme.colorScheme
    val containerColor = lerp(
        scheme.surfaceContainerHigh,
        scheme.primary,
        selection
    )
    val contentColor = lerp(
        scheme.onSurfaceVariant,
        scheme.onPrimary,
        selection
    )

    val innerRadius = lerpDp(12.dp, 22.dp, selection)
    val pressInner = if (isPressed) 8.dp else innerRadius
    val startRadius = if (index == 0) 29.dp else pressInner
    val endRadius = if (index == lastIndex) 29.dp else pressInner
    val shape = RoundedCornerShape(
        topStart = startRadius,
        bottomStart = startRadius,
        topEnd = endRadius,
        bottomEnd = endRadius
    )

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 380f
        ),
        label = "navIconScale$index"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxHeight()
            .semantics {
                role = Role.Tab
                selected = isSelected
            },
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (isSelected) 1.dp else 0.dp,
        shadowElevation = 0.dp,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (selection > 0.5f) {
                    destination.selectedIcon
                } else {
                    destination.unselectedIcon
                },
                contentDescription = if (selection > 0.5f) null else destination.label,
                tint = contentColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Spacer(modifier = Modifier.width(10.dp * selection))
            Text(
                text = destination.label,
                color = contentColor,
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (placeable.width * selection)
                            .toInt()
                            .coerceAtLeast(0)
                        layout(width, placeable.height) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .graphicsLayer {
                        alpha = selection
                    }
            )
        }
    }
}

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp {
    return start + (stop - start) * fraction
}

private fun performExpressiveNavHaptic(view: View, strong: Boolean) {
    view.isHapticFeedbackEnabled = true
    val constant = when {
        Build.VERSION.SDK_INT >= 34 && strong -> HapticFeedbackConstants.CONFIRM
        Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.SEGMENT_TICK
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && strong -> HapticFeedbackConstants.CONFIRM
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> HapticFeedbackConstants.KEYBOARD_TAP
        else -> HapticFeedbackConstants.VIRTUAL_KEY
    }
    view.performHapticFeedback(constant)
}
