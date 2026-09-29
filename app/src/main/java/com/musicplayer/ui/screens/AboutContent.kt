package com.musicplayer.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Редактируй этот файл, чтобы менять авторов и команды на экране «О приложении». */
data class CommunityMember(
    val name: String,
    val role: String,
    val github: String
)

data class AboutAction(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val url: String? = null
)

val aboutMaintainers = listOf(
    CommunityMember("Izzy", "Manages updates on IzzyOnDroid", "IzzySoft"),
    CommunityMember("linsui", "Manages updates on F-Droid", "linsui"),
    CommunityMember("Licaon_Kter", "Manages updates on F-Droid", "licaon-kter")
)

val aboutCollaborators = listOf(
    CommunityMember("theovilardo", "Guide & PixelPlayer's Lead Dev", "theovilardo"),
    CommunityMember("Nick", "Guide & Gramophone's Maintainer", "nift4"),
    CommunityMember("vivi", "Guide & Vivi Music's Lead Dev", "vivizzz007"),
    CommunityMember("Alex", "Lyrically API's Lead Dev", "Paxsenix0")
)

val aboutDesignTesting = listOf(
    CommunityMember("itzKane", "UI Concept Designer", "soykane")
)

val aboutActions = listOf(
    AboutAction(Icons.Rounded.Download, "Check for Updates", "Check for the latest version", "https://github.com/qrrrv/Glowpath-app/releases"),
    AboutAction(Icons.Rounded.BugReport, "Report Bug or Suggest Feature", "github.com/qrrrv/Glowpath-app/issues", "https://github.com/qrrrv/Glowpath-app/issues"),
    AboutAction(Icons.Rounded.Settings, "Open Source Libraries", "View Dependencies", "https://github.com/qrrrv/Glowpath-app/network/dependents"),
    AboutAction(Icons.Rounded.Chat, "Discord Community", "discord.gg/XjPyUYPQYc", "https://discord.gg/XjPyUYPQYc"),
    AboutAction(Icons.Rounded.Send, "Telegram Support", "t.me/RhythmSupport", "https://t.me/RhythmSupport"),
    AboutAction(Icons.Rounded.RestartAlt, "Replay Welcome Tour", "Show the introduction again")
)
