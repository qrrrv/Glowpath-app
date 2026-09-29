/*
 * Rhythm-inspired About screen.
 * Original Rhythm project: https://github.com/cromaguy/Rhythm
 * Licensed under GPL-3.0-or-later. This adaptation keeps the original
 * author credits and visual structure as requested.
 */
package com.musicplayer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.musicplayer.R
import com.musicplayer.ui.theme.LocalAppFontFamily

private data class AboutDetail(val icon: ImageVector, val label: String, val value: String)
private data class CommunityMember(val name: String, val role: String, val github: String)
private data class AboutAction(val icon: ImageVector, val title: String, val description: String, val url: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val font = LocalAppFontFamily.current
    val listState = rememberLazyListState()
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    val packageInfo = rememberPackageInfo(context)
    val version = packageInfo.first
    val build = packageInfo.second
    val visible by remember { androidx.compose.runtime.mutableStateOf(true) }

    val detailCards = listOf(
        AboutDetail(Icons.Rounded.Info, "Версия", version),
        AboutDetail(Icons.Rounded.Build, "Сборка", build),
        AboutDetail(Icons.Rounded.DeveloperMode, "Target SDK", "36"),
        AboutDetail(Icons.Rounded.Memory, "Архитектура", Build.SUPPORTED_ABIS.take(2).joinToString(", ").ifBlank { "ARM64 & ARM32" })
    )

    val maintainers = listOf(
        CommunityMember("Izzy", "Manages updates on IzzyOnDroid", "IzzySoft"),
        CommunityMember("linsui", "Manages updates on F-Droid", "linsui"),
        CommunityMember("Licaon_Kter", "Manages updates on F-Droid", "licaon-kter")
    )
    val collaborators = listOf(
        CommunityMember("theovilardo", "Guide & PixelPlayer's Lead Dev", "theovilardo"),
        CommunityMember("Nick", "Guide & Gramophone's Maintainer", "nift4"),
        CommunityMember("vivi", "Guide & Vivi Music's Lead Dev", "vivizzz007"),
        CommunityMember("Alex", "Lyrically API's Lead Dev", "Paxsenix0")
    )
    val designTesting = listOf(CommunityMember("itzKane", "UI Concept Designer", "soykane"))
    val actions = listOf(
        AboutAction(Icons.Rounded.Download, "Check for Updates", "Check for the latest version", "https://github.com/qrrrv/Glowpath-app/releases"),
        AboutAction(Icons.Rounded.BugReport, "Report Bug or Suggest Feature", "github.com/qrrrv/Glowpath-app/issues", "https://github.com/qrrrv/Glowpath-app/issues"),
        AboutAction(Icons.Rounded.Settings, "Open Source Libraries", "View Dependencies", "https://github.com/qrrrv/Glowpath-app/network/dependents"),
        AboutAction(Icons.Rounded.Chat, "Discord Community", "discord.gg/XjPyUYPQYc", "https://discord.gg/XjPyUYPQYc"),
        AboutAction(Icons.Rounded.Send, "Telegram Support", "t.me/RhythmSupport", "https://t.me/RhythmSupport"),
        AboutAction(Icons.Rounded.RestartAlt, "Replay Welcome Tour", "Show the introduction again")
    )

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
    }
    fun copy(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "$label скопировано", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("О приложении", fontFamily = font, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Назад") } },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                AnimatedVisibility(visible = visible, enter = fadeIn() + scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = "Glowpath", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(82.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Glowpath", style = MaterialTheme.typography.displaySmall, fontFamily = font, fontWeight = FontWeight.Bold)
                        }
                        Text("Music Player", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Твоя музыка, всегда с тобой.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
            item { DetailGrid(details = detailCards, onCopy = ::copy) }
            item {
                Text("Developer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                DeveloperCard(
                    name = "Anjishnu Nandi", github = "cromaguy", avatar = "https://github.com/cromaguy.png",
                    onOpen = ::openUrl, website = "https://rhythmweb.vercel.app/", support = "https://ko-fi.com/anjishnunandi"
                )
            }
            item { CommunityGroup("Package Maintainers", maintainers, ::openUrl) }
            item { CommunityGroup("Collaborators & Contributors", collaborators, ::openUrl) }
            item { CommunityGroup("Design & Testing", designTesting, ::openUrl) }
            item { ActionGroup("Actions", actions, ::openUrl) }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Rhythm-inspired Material 3 design", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DetailGrid(details: List<AboutDetail>, onCopy: (String, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        details.chunked(2).forEachIndexed { row, cards ->
            Row(Modifier.fillMaxWidth().height(94.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                cards.forEachIndexed { col, detail ->
                    DetailCard(detail, Modifier.weight(1f), detailShape(row * 2 + col, details.size), onCopy)
                }
            }
        }
        DetailCard(
            AboutDetail(Icons.Rounded.ContentCopy, "Copy System Info", "Glowpath ${details.first().value} • Android ${Build.VERSION.RELEASE}"),
            Modifier.fillMaxWidth().height(82.dp), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp), onCopy
        )
    }
}

@Composable
private fun DetailCard(detail: AboutDetail, modifier: Modifier, shape: RoundedCornerShape, onCopy: (String, String) -> Unit) {
    Card(modifier = modifier.clickable { onCopy(detail.label, detail.value) }, shape = shape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(detail.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(detail.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(detail.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun detailShape(index: Int, total: Int) = when (index) {
    0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    else -> RoundedCornerShape(6.dp)
}

@Composable
private fun DeveloperCard(name: String, github: String, avatar: String, website: String, support: String, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(avatar).crossfade(true).build(), contentDescription = name, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(30.dp)), placeholder = painterResource(R.drawable.ic_music_placeholder), error = painterResource(R.drawable.ic_music_placeholder))
                Spacer(Modifier.height(16.dp))
                Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExpressiveButton(Icons.Rounded.Language, "Visit Website", Modifier.weight(1f)) { onOpen(website) }
                    ExpressiveButton(Icons.Rounded.Code, "View GitHub", Modifier.weight(1f)) { onOpen("https://github.com/$github") }
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { onOpen(support) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(vertical = 12.dp)) { Text("Support Development", fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Team ChromaHub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text("Passionate developers creating innovative experiences", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ExpressiveButton(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "about_button_scale")
    Button(onClick = onClick, modifier = modifier.animateContentSize().graphicsLayerCompat(scale), interactionSource = interaction, shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer), contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)) {
        Icon(icon, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

private fun Modifier.graphicsLayerCompat(scale: Float) = this.then(Modifier.graphicsLayer { scaleX = scale; scaleY = scale })

@Composable
private fun CommunityGroup(title: String, members: List<CommunityMember>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.animateContentSize()) { members.forEachIndexed { index, member -> CommunityRow(member, onOpen, index == 0, index == members.lastIndex) } }
    }
}

@Composable
private fun CommunityRow(member: CommunityMember, onOpen: (String) -> Unit, first: Boolean, last: Boolean) {
    val context = LocalContext.current
    val avatar = "https://github.com/${member.github}.png"
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen("https://github.com/${member.github}") }, shape = when { first && last -> RoundedCornerShape(24.dp); first -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp); last -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp); else -> RoundedCornerShape(6.dp) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = ImageRequest.Builder(context).data(avatar).crossfade(true).build(), contentDescription = member.name, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)), placeholder = painterResource(R.drawable.ic_music_placeholder), error = painterResource(R.drawable.ic_music_placeholder))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text(member.name, fontWeight = FontWeight.Bold); Text(member.role, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 24.sp)
        }
    }
}

@Composable
private fun ActionGroup(title: String, actions: List<AboutAction>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { actions.forEachIndexed { index, action ->
            val shape = when { actions.size == 1 -> RoundedCornerShape(24.dp); index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp); index == actions.lastIndex -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp); else -> RoundedCornerShape(6.dp) }
            Card(modifier = Modifier.fillMaxWidth().clickable { action.url?.let(onOpen) }, shape = shape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.padding(horizontal = 21.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(action.icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(21.dp)) } }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text(action.title, fontWeight = FontWeight.Bold); Text(action.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 24.sp)
                }
            }
        } }
    }
}

@Composable
private fun rememberPackageInfo(context: Context): Pair<String, String> = remember(context) {
    runCatching {
        @Suppress("DEPRECATION") val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val version = info.versionName ?: "неизвестно"
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toString()
        }
        version to code
    }.getOrDefault("неизвестно" to "неизвестно")
}
