package com.musicplayer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.musicplayer.data.SortOrder
import kotlinx.coroutines.launch

internal data class LibrarySortOption(
    val order: SortOrder,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

internal val librarySortOptions = listOf(
    LibrarySortOption(SortOrder.TITLE, "По названию", "Названия треков по алфавиту", Icons.Rounded.SortByAlpha),
    LibrarySortOption(SortOrder.ARTIST, "По исполнителю", "Имена исполнителей по алфавиту", Icons.Rounded.PersonOutline),
    LibrarySortOption(SortOrder.ALBUM, "По альбому", "Названия альбомов по алфавиту", Icons.Rounded.Album),
    LibrarySortOption(SortOrder.DURATION, "По длительности", "От коротких треков к длинным", Icons.Rounded.Schedule),
    LibrarySortOption(SortOrder.DATE_ADDED, "По дате добавления", "Сначала недавно добавленные", Icons.Rounded.NewReleases),
)

@Composable
internal fun SettingsSortItem(sortOrder: SortOrder, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val option = librarySortOptions.first { it.order == sortOrder }
    // Тот же вид, что у остальных пунктов настроек (Android 16 / Material 3 Expressive)
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = colors.surfaceContainer),
        headlineContent = {
            Text("Сортировка", color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(option.title, color = colors.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
        },
        leadingContent = { SettingsLeadingIcon(icon = Icons.Rounded.Sort, tint = colors.primary) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySortSheet(sortOrder: SortOrder, onSortChange: (SortOrder) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    fun closeSheet() {
        if (closing) return
        closing = true
        scope.launch {
            try {
                sheetState.hide()
                if (!sheetState.isVisible) onDismiss()
            } finally {
                closing = false
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        LibrarySortSheetContent(
            sortOrder = sortOrder,
            enabled = !closing,
            onSelect = {
                onSortChange(it)
                closeSheet()
            },
            onClose = ::closeSheet,
        )
    }
}

@Composable
internal fun LibrarySortSheetContent(
    sortOrder: SortOrder,
    onSelect: (SortOrder) -> Unit,
    onClose: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Сортировка", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text("Порядок треков в медиатеке", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            IconButton(onClick = onClose, enabled = enabled) { Icon(Icons.Rounded.Close, "Закрыть сортировку") }
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false).selectableGroup().testTag("library_sort_options"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(librarySortOptions, key = { it.order.name }) { option ->
                val selected = sortOrder == option.order
                val container by animateColorAsState(
                    if (selected) colors.secondaryContainer else colors.surfaceContainer,
                    label = "sort_option_color",
                )
                val foreground = if (selected) colors.onSecondaryContainer else colors.onSurface
                Surface(color = container, contentColor = foreground, shape = RoundedCornerShape(20.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(option.order) })
                            .testTag("library_sort_${option.order.name}")
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(option.icon, null, Modifier.size(24.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(option.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                            )
                        }
                        RadioButton(selected = selected, onClick = null, enabled = enabled)
                    }
                }
            }
        }
    }
}
