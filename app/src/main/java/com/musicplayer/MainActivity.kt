package com.musicplayer

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.musicplayer.bridge.ExteraGramBridgeCommand
import com.musicplayer.bridge.ExteraGramBridgeContract
import com.musicplayer.bridge.ExteraGramBridgeRuntime
import com.musicplayer.bridge.ExteraGramBridgeStateStore
import com.musicplayer.bridge.parseBridgeBatchItems
import com.musicplayer.bridge.toExteraGramBridgeCommand
import com.musicplayer.data.AppTheme
import com.musicplayer.data.RepeatMode
import com.musicplayer.data.Song
import com.musicplayer.ui.components.AnimatedPlaybackControls
import com.musicplayer.ui.components.AppBottomNavBar
import com.musicplayer.ui.components.AutoScrollingText
import com.musicplayer.ui.components.OptimizedAlbumArt
import com.musicplayer.ui.components.boomingDeleteItemModifier
import com.musicplayer.ui.navigation.enterTransition
import com.musicplayer.ui.navigation.exitTransition
import com.musicplayer.ui.navigation.popEnterTransition
import com.musicplayer.ui.navigation.popExitTransition
import com.musicplayer.ui.navigation.AnimationRegistry
import com.musicplayer.ui.navigation.AnimationsApplier
import com.musicplayer.ui.navigation.TabPagerTransition
import com.musicplayer.ui.navigation.aniSyncSharedAxisPage
import com.musicplayer.ui.screens.*
import com.musicplayer.ui.theme.*
import com.musicplayer.viewmodel.MusicViewModel
import com.musicplayer.viewmodel.OnlineSearchViewModel
import coil.size.Size as CoilSize
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.graphics.Color as ComposeColor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.navigationBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        // Check if launched from a stats share deep link
        val sharedStatsLink: String? = if (
            intent?.action == android.content.Intent.ACTION_VIEW &&
            intent.data?.scheme == "musicplayer" &&
            intent.data?.host == "share"
        ) {
            intent.dataString?.also { com.musicplayer.data.stats.SharedStatsHolder.pendingLink = it }
        } else null

        val initialBridgeCommand = intent?.toExteraGramBridgeCommand()?.also {
            ExteraGramBridgeStateStore.rememberCommand(this, it)
        }

        setContent {
            val viewModel: MusicViewModel = viewModel()
            val settings by viewModel.settings.collectAsState()
            val customThemeColors by viewModel.customThemeColors.collectAsState()

            MusicPlayerTheme(
                appTheme       = settings.theme,
                interfaceStyle = settings.interfaceStyle,
                selectedFontId = settings.selectedFontId,
                customFontUri  = settings.customFontUri,
                customColors   = customThemeColors
            ) {
                // ── Splash → App transition ───────────────────────────────────
                val isAppReady      by viewModel.isAppReady.collectAsState()
                val splashLoaded    by viewModel.splashLoadedCount.collectAsState()
                val splashTotal     by viewModel.splashTotalCount.collectAsState()

                Box(modifier = Modifier.fillMaxSize()) {
                    // Основной UI (рендерится заранее, но скрыт под сплешем)
                    MusicPlayerApp(
                        viewModel = viewModel,
                        sharedStatsLink = sharedStatsLink,
                        initialBridgeCommand = initialBridgeCommand,
                    )

                    // Сплеш-экран поверх до готовности
                    AnimatedVisibility(
                        visible = !isAppReady,
                        enter   = EnterTransition.None,
                        exit    = fadeOut(tween(480, easing = FastOutSlowInEasing)) +
                                  scaleOut(tween(480, easing = FastOutSlowInEasing), targetScale = 0.94f),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        SplashScreen(
                            loadedCount = splashLoaded,
                            totalCount  = splashTotal,
                            isReady     = isAppReady
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (
            intent.action == Intent.ACTION_VIEW &&
            intent.data?.scheme == "musicplayer" &&
            intent.data?.host == "share"
        ) {
            intent.dataString?.also { com.musicplayer.data.stats.SharedStatsHolder.pendingLink = it }
        }

        intent.toExteraGramBridgeCommand()?.let { command ->
            ExteraGramBridgeRuntime.dispatch(this, command)
        }
    }
}

@Composable
fun MusicPlayerApp(
    viewModel: MusicViewModel,
    sharedStatsLink: String? = null,
    initialBridgeCommand: ExteraGramBridgeCommand? = null,
) {
    val settings by viewModel.settings.collectAsState()
    val navController = rememberNavController()
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val bridgeQueueUris by viewModel.bridgeQueueUris.collectAsState()
    val currentRoute by navController.currentBackStackEntryAsState()
    val onlineSearchViewModel: OnlineSearchViewModel = viewModel()
    var showMiniQueue by remember { mutableStateOf(false) }
    var showMiniActions by remember { mutableStateOf(false) }
    val activeQueue = remember(songs, bridgeQueueUris, settings.sortOrder, currentSong?.uri) {
        viewModel.buildActiveQueueSnapshot()
    }
    val isCurrentFavourite = currentSong?.id?.let { it in favourites } == true

    LaunchedEffect(currentSong?.id) {
        if (currentSong == null) {
            showMiniQueue = false
            showMiniActions = false
        }
    }

    LaunchedEffect(sharedStatsLink) {
        if (!sharedStatsLink.isNullOrBlank()) {
            navController.navigate("shared_stats")
        }
    }

    val rootRouteOrder = remember { listOf("home", "albums", "online_search") }
    val route = currentRoute?.destination?.route
    var requestedRootRoute by rememberSaveable { mutableStateOf(rootRouteOrder.first()) }
    var animateRootTabRequest by rememberSaveable { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { rootRouteOrder.size })
    val rootTabAnimationSpec = remember { TabPagerTransition.snapSpec }
    val currentRootIndex by remember(pagerState) {
        derivedStateOf {
            (if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage)
                .coerceIn(0, rootRouteOrder.lastIndex)
        }
    }
    val currentRootRoute by remember {
        derivedStateOf { rootRouteOrder[currentRootIndex] }
    }
    val rootPagerPosition by remember(pagerState) {
        derivedStateOf {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, rootRouteOrder.lastIndex.toFloat())
        }
    }

    fun openRootTab(dest: String, animate: Boolean = true) {
        if (dest !in rootRouteOrder) return
        requestedRootRoute = dest
        animateRootTabRequest = animate
        if (route != "root_tabs") {
            navController.navigate("root_tabs") {
                popUpTo("root_tabs") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(route, requestedRootRoute, animateRootTabRequest) {
        if (route == "root_tabs") {
            val targetIndex = rootRouteOrder.indexOf(requestedRootRoute)
            if (targetIndex >= 0 && (
                targetIndex != pagerState.currentPage ||
                    abs(pagerState.currentPageOffsetFraction) > 0.001f
            )) {
                if (animateRootTabRequest) {
                    pagerState.animateScrollToPage(
                        page = targetIndex,
                        animationSpec = rootTabAnimationSpec
                    )
                } else {
                    pagerState.scrollToPage(targetIndex)
                }
            }
        }
    }

    fun handleBridgeCommand(command: ExteraGramBridgeCommand) {
        when (command.type) {
            ExteraGramBridgeContract.TYPE_OPEN_HUB -> {
                navController.navigate("exteragram_bridge")
            }

            ExteraGramBridgeContract.TYPE_OPEN_PLAYER -> {
                if (currentSong != null) {
                    navController.navigate("player")
                } else {
                    navController.navigate("exteragram_bridge")
                }
            }

            ExteraGramBridgeContract.TYPE_PLAY_PAUSE -> {
                if (currentSong != null) {
                    viewModel.togglePlayPause()
                } else {
                    navController.navigate("exteragram_bridge")
                }
            }

            ExteraGramBridgeContract.TYPE_NEXT -> {
                if (viewModel.songs.value.isNotEmpty()) {
                    viewModel.playNext()
                }
            }

            ExteraGramBridgeContract.TYPE_PREV -> {
                if (viewModel.songs.value.isNotEmpty()) {
                    viewModel.playPrevious()
                }
            }

            ExteraGramBridgeContract.TYPE_STOP -> {
                if (currentSong != null) {
                    viewModel.stopPlaybackFromBridge(clearSong = true)
                }
            }

            ExteraGramBridgeContract.TYPE_SEARCH_ONLINE -> {
                onlineSearchViewModel.openFromBridge(command.query)
                openRootTab("online_search")
            }

            ExteraGramBridgeContract.TYPE_PLAY_LIBRARY_QUERY -> {
                val played = viewModel.playByQuery(command.query)
                if (played != null) {
                    navController.navigate("player")
                } else {
                    onlineSearchViewModel.openFromBridge(command.query)
                    openRootTab("online_search")
                }
            }

            ExteraGramBridgeContract.TYPE_OPEN_LYRICS_QUERY -> {
                val played = viewModel.playByQuery(command.query)
                if (played != null) {
                    navController.navigate("lyrics")
                } else {
                    onlineSearchViewModel.openFromBridge(command.query)
                    openRootTab("online_search")
                }
            }

            ExteraGramBridgeContract.TYPE_IMPORT_URI,
            ExteraGramBridgeContract.TYPE_IMPORT_AND_PLAY_URI -> {
                val uri = android.net.Uri.parse(command.uri)
                if (uri != null && command.uri.isNotBlank()) {
                    viewModel.addSongFromUri(
                        uri = uri,
                        autoPlay = command.autoPlay || command.type == ExteraGramBridgeContract.TYPE_IMPORT_AND_PLAY_URI,
                    ) { song ->
                        if (song != null) {
                            navController.navigate("player")
                        } else {
                            onlineSearchViewModel.openFromBridge(command.query)
                            navController.navigate("exteragram_bridge")
                        }
                    }
                }
            }

            ExteraGramBridgeContract.TYPE_IMPORT_BATCH,
            ExteraGramBridgeContract.TYPE_IMPORT_BATCH_AND_PLAY,
            ExteraGramBridgeContract.TYPE_QUEUE_BATCH_APPEND -> {
                val items = parseBridgeBatchItems(command.payload)
                viewModel.importBridgeBatch(items, mode = command.type) { songs ->
                    if (songs.isNotEmpty() && command.type != ExteraGramBridgeContract.TYPE_IMPORT_BATCH) {
                        navController.navigate("player")
                    } else {
                        navController.navigate("exteragram_bridge")
                    }
                }
            }
        }
    }

    LaunchedEffect(initialBridgeCommand) {
        initialBridgeCommand?.let { handleBridgeCommand(it) }
    }

    LaunchedEffect(Unit) {
        ExteraGramBridgeRuntime.commands.collectLatest { command ->
            handleBridgeCommand(command)
        }
    }

    // ── Player open mode ──────────────────────────────────────────────────────
    fun navigateToPlayer() { navController.navigate("player") }
    fun handleSongTap(play: () -> Unit) {
        play()
        if (settings.playerOpenMode == 0) navigateToPlayer()
        // mode 1: just play — mini-player appears, user taps it to open full player
    }

    val showRootTabsBackdrop = route == "player"
    val showMiniPlayer = currentSong != null &&
            route == "root_tabs"

    val showBottomNav = route == "root_tabs"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.bgDeep)
    ) {
        if (showRootTabsBackdrop) {
            RootTabsPager(
                pagerState = pagerState,
                rootRouteOrder = rootRouteOrder,
                viewModel = viewModel,
                onlineSearchViewModel = onlineSearchViewModel,
                onSongClick = { song -> handleSongTap { viewModel.playSong(song) } },
                onAlbumSongClick = { song -> handleSongTap { viewModel.playFromActiveQueue(song) } },
                onOnlineAlbumClick = { album ->
                    onlineSearchViewModel.openAlbum(album)
                    openRootTab("online_search")
                },
                onSettingsClick = { navController.navigate("settings") },
                modifier = Modifier.fillMaxSize()
            )
        }

        NavHost(
            navController    = navController,
            startDestination = "root_tabs",
            modifier         = Modifier.fillMaxSize()
        ) {
            composable(
                "root_tabs",
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                RootTabsPager(
                    pagerState = pagerState,
                    rootRouteOrder = rootRouteOrder,
                    viewModel = viewModel,
                    onlineSearchViewModel = onlineSearchViewModel,
                    onSongClick = { song -> handleSongTap { viewModel.playSong(song) } },
                    onAlbumSongClick = { song -> handleSongTap { viewModel.playFromActiveQueue(song) } },
                    onOnlineAlbumClick = { album ->
                        onlineSearchViewModel.openAlbum(album)
                        openRootTab("online_search")
                    },
                    onSettingsClick = { navController.navigate("settings") },
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable("home") {
                LaunchedEffect(Unit) { openRootTab("home", animate = false) }
                Box(modifier = Modifier.fillMaxSize())
            }

            composable("albums") {
                LaunchedEffect(Unit) { openRootTab("albums", animate = false) }
                Box(modifier = Modifier.fillMaxSize())
            }

            composable("online_search") {
                LaunchedEffect(Unit) { openRootTab("online_search", animate = false) }
                Box(modifier = Modifier.fillMaxSize())
            }

            composable(
                "player",
                enterTransition = { EnterTransition.None },
                exitTransition  = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition  = { ExitTransition.None }
            ) {
                PlayerScreen(
                    viewModel    = viewModel,
                    onBack       = { navController.popBackStack() },
                    onLyricsClick = { navController.navigate("lyrics") }
                )
            }

            composable(
                "lyrics",
                enterTransition = { AnimationsApplier.screenEnter(settings.screenTransitionAnim, settings.animParams) },
                exitTransition  = { AnimationsApplier.screenExit(settings.screenTransitionAnim, settings.animParams) },
                popEnterTransition  = { AnimationsApplier.screenPopEnter(settings.screenTransitionAnim, settings.animParams) },
                popExitTransition   = { AnimationsApplier.screenPopExit(settings.screenTransitionAnim, settings.animParams) }
            ) {
                LyricsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable(
                "settings",
                enterTransition      = { AnimationsApplier.screenEnter(settings.screenTransitionAnim, settings.animParams) },
                exitTransition       = { AnimationsApplier.screenExit(settings.screenTransitionAnim, settings.animParams) },
                popEnterTransition   = { AnimationsApplier.screenPopEnter(settings.screenTransitionAnim, settings.animParams) },
                popExitTransition    = { AnimationsApplier.screenPopExit(settings.screenTransitionAnim, settings.animParams) }
            ) {
                SettingsScreen(
                    viewModel          = viewModel,
                    onBack             = { navController.popBackStack() },
                    onTransitionsClick = { navController.navigate("transitions") },
                    onAnimationsClick  = { navController.navigate("animations") },
                    onOrbsClick        = { navController.navigate("orb_settings") },
                    onStatsClick       = { navController.navigate("stats") },
                    onEqualizerClick   = { navController.navigate("equalizer") },
                    onCustomThemeClick = { navController.navigate("custom_theme") },
                    onTopBarClick      = { navController.navigate("topbar_settings") }
                )
            }

            composable("orb_settings", enterTransition = { AnimationsApplier.screenEnter(settings.screenTransitionAnim, settings.animParams) }, exitTransition = { AnimationsApplier.screenExit(settings.screenTransitionAnim, settings.animParams) }, popEnterTransition = { AnimationsApplier.screenPopEnter(settings.screenTransitionAnim, settings.animParams) }, popExitTransition = { AnimationsApplier.screenPopExit(settings.screenTransitionAnim, settings.animParams) }) {
                OrbSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable(
                "exteragram_bridge",
                enterTransition = { AnimationsApplier.screenEnter(settings.screenTransitionAnim, settings.animParams) },
                exitTransition = { AnimationsApplier.screenExit(settings.screenTransitionAnim, settings.animParams) },
                popEnterTransition = { AnimationsApplier.screenPopEnter(settings.screenTransitionAnim, settings.animParams) },
                popExitTransition = { AnimationsApplier.screenPopExit(settings.screenTransitionAnim, settings.animParams) }
            ) {
                ExteraGramBridgeScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = {
                        if (currentSong != null) {
                            navController.navigate("player")
                        }
                    },
                    onOpenSearch = { query ->
                        onlineSearchViewModel.openFromBridge(query)
                        openRootTab("online_search")
                    }
                )
            }

            composable("topbar_settings", enterTransition = { AnimationsApplier.screenEnter(settings.screenTransitionAnim, settings.animParams) }, exitTransition = { AnimationsApplier.screenExit(settings.screenTransitionAnim, settings.animParams) }, popEnterTransition = { AnimationsApplier.screenPopEnter(settings.screenTransitionAnim, settings.animParams) }, popExitTransition = { AnimationsApplier.screenPopExit(settings.screenTransitionAnim, settings.animParams) }) {
                TopBarSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable("custom_theme", enterTransition = { AnimationsApplier.screenEnter(settings.screenTransitionAnim, settings.animParams) }, exitTransition = { AnimationsApplier.screenExit(settings.screenTransitionAnim, settings.animParams) }, popEnterTransition = { AnimationsApplier.screenPopEnter(settings.screenTransitionAnim, settings.animParams) }, popExitTransition = { AnimationsApplier.screenPopExit(settings.screenTransitionAnim, settings.animParams) }) {
                CustomThemeScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable("equalizer", enterTransition = { AnimationsApplier.screenEnter(settings.screenTransitionAnim, settings.animParams) }, exitTransition = { AnimationsApplier.screenExit(settings.screenTransitionAnim, settings.animParams) }, popEnterTransition = { AnimationsApplier.screenPopEnter(settings.screenTransitionAnim, settings.animParams) }, popExitTransition = { AnimationsApplier.screenPopExit(settings.screenTransitionAnim, settings.animParams) }) {
                EqualizerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable("stats", enterTransition = { AnimationsApplier.screenEnter(settings.screenTransitionAnim, settings.animParams) }, exitTransition = { AnimationsApplier.screenExit(settings.screenTransitionAnim, settings.animParams) }, popEnterTransition = { AnimationsApplier.screenPopEnter(settings.screenTransitionAnim, settings.animParams) }, popExitTransition = { AnimationsApplier.screenPopExit(settings.screenTransitionAnim, settings.animParams) }) {
                StatsScreen(musicViewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable("shared_stats", enterTransition = { AnimationsApplier.screenEnter(settings.screenTransitionAnim, settings.animParams) }, exitTransition = { AnimationsApplier.screenExit(settings.screenTransitionAnim, settings.animParams) }, popEnterTransition = { AnimationsApplier.screenPopEnter(settings.screenTransitionAnim, settings.animParams) }, popExitTransition = { AnimationsApplier.screenPopExit(settings.screenTransitionAnim, settings.animParams) }) {
                val link = com.musicplayer.data.stats.SharedStatsHolder.pendingLink ?: ""
                SharedStatsScreen(encodedLink = link, onBack = { navController.popBackStack() })
            }

            composable("transitions", enterTransition = { AnimationsApplier.screenEnter(settings.screenTransitionAnim, settings.animParams) }, exitTransition = { AnimationsApplier.screenExit(settings.screenTransitionAnim, settings.animParams) }, popEnterTransition = { AnimationsApplier.screenPopEnter(settings.screenTransitionAnim, settings.animParams) }, popExitTransition = { AnimationsApplier.screenPopExit(settings.screenTransitionAnim, settings.animParams) }) {
                EditTransitionScreen(onBack = { navController.popBackStack() }, musicViewModel = viewModel)
            }

            composable("animations", enterTransition = { AnimationsApplier.screenEnter(settings.screenTransitionAnim, settings.animParams) }, exitTransition = { AnimationsApplier.screenExit(settings.screenTransitionAnim, settings.animParams) }, popEnterTransition = { AnimationsApplier.screenPopEnter(settings.screenTransitionAnim, settings.animParams) }, popExitTransition = { AnimationsApplier.screenPopExit(settings.screenTransitionAnim, settings.animParams) }) {
                AnimationSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }

        val bottomChromeRoute = currentRootRoute

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnimatedVisibility(
                visible  = showMiniPlayer,
                enter    = AnimationsApplier.miniPlayerEnter(settings.miniPlayerEntryAnim, settings.animParams),
                exit     = AnimationsApplier.miniPlayerExit(settings.miniPlayerEntryAnim, settings.animParams)
            ) {
                currentSong?.let { song ->
                    MiniPlayer(
                        title       = song.title,
                        artist      = if (song.artist != "<unknown>") song.artist else "Неизвестный",
                        albumArtUri = song.albumArtUri,
                    isPlaying   = isPlaying,
                    progress    = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                    queueCount  = activeQueue.size,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onPrevious  = { viewModel.playPrevious() },
                    onNext      = { viewModel.playNext() },
                    onMoreClick = { showMiniActions = true },
                    onClick     = { navController.navigate("player") },
                    modifier    = Modifier.padding(horizontal = 12.dp)
                )
                }
            }

            AnimatedVisibility(
                visible  = showBottomNav,
                enter    = slideInVertically(
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                ) { it } + fadeIn(tween(220)),
                exit     = slideOutVertically(
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) { it } + fadeOut(tween(180))
            ) {
                AppBottomNavBar(
                    currentRoute  = bottomChromeRoute,
                    pagePosition = if (route == "root_tabs" || route == "player") rootPagerPosition else null,
                    onNavigate    = { dest -> openRootTab(dest) }
                )
            }
        }

        if (showMiniActions && currentSong != null) {
            MiniPlayerActionsSheet(
                title = currentSong?.title.orEmpty(),
                isFavourite = isCurrentFavourite,
                queueCount = activeQueue.size,
                shuffleEnabled = settings.shuffleEnabled,
                repeatMode = settings.repeatMode,
                onOpenPlayer = {
                    showMiniActions = false
                    navController.navigate("player")
                },
                onOpenQueue = {
                    showMiniActions = false
                    showMiniQueue = true
                },
                onToggleFavourite = {
                    currentSong?.let { viewModel.toggleFavourite(it.id) }
                },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onToggleRepeat = { viewModel.toggleRepeat() },
                onDismiss = { showMiniActions = false }
            )
        }

        if (showMiniQueue && currentSong != null) {
            MiniQueueSheet(
                queue = activeQueue,
                currentSong = currentSong,
                onSelectSong = { viewModel.playFromActiveQueue(it) },
                onOpenPlayer = {
                    showMiniQueue = false
                    navController.navigate("player")
                },
                onDismiss = { showMiniQueue = false }
            )
        }
    }
}

@Composable
private fun RootTabsPager(
    pagerState: PagerState,
    rootRouteOrder: List<String>,
    viewModel: MusicViewModel,
    onlineSearchViewModel: OnlineSearchViewModel,
    onSongClick: (Song) -> Unit,
    onAlbumSongClick: (Song) -> Unit,
    onOnlineAlbumClick: (com.musicplayer.data.OnlineAlbumSummary) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = 2,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            snapAnimationSpec = TabPagerTransition.snapSpec
        )
    ) { page ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aniSyncSharedAxisPage(page, pagerState)
        ) {
            when (rootRouteOrder[page]) {
                "home" -> HomeScreen(
                    viewModel = viewModel,
                    onSongClick = onSongClick,
                    onSettingsClick = onSettingsClick,
                    onOnlineAlbumClick = onOnlineAlbumClick
                )

                "albums" -> AlbumsScreen(
                    viewModel = viewModel,
                    onSongClick = onAlbumSongClick
                )

                else -> OnlineSearchScreen(
                    musicViewModel = viewModel,
                    searchViewModel = onlineSearchViewModel
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassPane(
    modifier: Modifier = Modifier,
    shape: Shape,
    tint: ComposeColor,
    depth: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val safeDepth = depth.coerceIn(0.6f, 1.4f)
    Box(
        modifier = modifier.graphicsLayer {
            shadowElevation = 20.dp.toPx() * safeDepth
            clip = false
        }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            ComposeColor.White.copy(alpha = 0.02f + safeDepth * 0.04f),
                            tint.copy(alpha = 0.10f + safeDepth * 0.08f),
                            ComposeColor.Black.copy(alpha = 0.28f + safeDepth * 0.14f)
                        )
                    )
                )
                .border(
                    1.dp,
                    ComposeColor.White.copy(alpha = 0.05f + safeDepth * 0.10f),
                    shape
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            ComposeColor.White.copy(alpha = 0.03f + safeDepth * 0.05f),
                            ComposeColor.Transparent,
                            tint.copy(alpha = 0.02f + safeDepth * 0.04f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.88f)
                .height(30.dp)
                .offset(y = (-1).dp)
                .blur((16.dp * safeDepth).coerceAtLeast(10.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            ComposeColor.Transparent,
                            ComposeColor.White.copy(alpha = 0.04f + safeDepth * 0.10f),
                            tint.copy(alpha = 0.02f + safeDepth * 0.04f),
                            ComposeColor.Transparent
                        )
                    ),
                    RoundedCornerShape(999.dp)
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.72f)
                .height(24.dp)
                .offset(y = 6.dp)
                .blur((18.dp * safeDepth).coerceAtLeast(12.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            ComposeColor.Transparent,
                            tint.copy(alpha = 0.08f + safeDepth * 0.10f),
                            ComposeColor.Transparent
                        )
                    ),
                    RoundedCornerShape(999.dp)
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
        ) {
            content()
        }
    }
}

@Composable
fun MiniPlayer(
    title: String,
    artist: String,
    albumArtUri: Uri? = null,
    isPlaying: Boolean,
    progress: Float,
    queueCount: Int,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMoreClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    val haptic = LocalHapticFeedback.current
    val safeTitle = title.ifBlank { "Без названия" }
    val safeArtist = artist.ifBlank { "Неизвестный" }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "miniProgress"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isPlaying) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(220),
        label = "miniContainer"
    )
    val actionColor by animateColorAsState(
        targetValue = if (isPlaying) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = tween(220),
        label = "miniAction"
    )
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.015f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "miniArtScale"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = shape,
        color = containerColor.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        tonalElevation = 7.dp,
        shadowElevation = 12.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.98f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = onClick,
                        onLongClick = onMoreClick
                    )
                    .padding(start = 10.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer {
                            scaleX = artScale
                            scaleY = artScale
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (albumArtUri != null) {
                            OptimizedAlbumArt(
                                uri = albumArtUri,
                                title = title,
                                modifier = Modifier.fillMaxSize(),
                                targetSize = CoilSize(144, 144)
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_music_placeholder),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (isPlaying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(ComposeColor.Black.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    AutoScrollingText(
                        text = safeTitle,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = LocalAppFontFamily.current,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AutoScrollingText(
                        text = if (queueCount > 0) "$safeArtist • $queueCount в очереди" else safeArtist,
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontFamily = LocalAppFontFamily.current,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.width(8.dp))

                MiniPlayerTransportButton(
                    icon = Icons.Default.SkipPrevious,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPrevious()
                    },
                    containerColor = actionColor,
                    contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    size = 36.dp,
                    iconSize = 20.dp
                )
                Spacer(Modifier.width(8.dp))
                MiniPlayerTransportButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlayPause()
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    size = 36.dp,
                    iconSize = 20.dp
                )
                Spacer(Modifier.width(8.dp))
                MiniPlayerTransportButton(
                    icon = Icons.Default.SkipNext,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNext()
                    },
                    containerColor = actionColor,
                    contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    size = 36.dp,
                    iconSize = 20.dp
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerStatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: ComposeColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: ComposeColor = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(13.dp))
        Text(
            text = label,
            color = contentColor,
            fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MiniPlayerTransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    containerColor: ComposeColor,
    contentColor: ComposeColor,
    size: androidx.compose.ui.unit.Dp = 34.dp,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "miniBtnScale"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = tween(140),
        label = "miniBtnIconScale"
    )

    FilledTonalIconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.82f)) togetherWith
                    (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 1.08f))
            },
            label = "miniBtnContent"
        ) { currentIcon ->
            Icon(
                currentIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
        }
    }
}

@Composable
private fun MiniPlayerActionChip(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: ComposeColor,
    onClick: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        ComposeColor.White.copy(alpha = 0.03f),
                        tint.copy(alpha = 0.10f),
                        c.bgDeep.copy(alpha = 0.24f)
                    )
                )
            )
            .border(1.dp, ComposeColor.White.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(
            label,
            color = c.textSecondary,
            fontFamily = font,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiniPlayerActionsSheet(
    title: String,
    isFavourite: Boolean,
    queueCount: Int,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onOpenPlayer: () -> Unit,
    onOpenQueue: () -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.textDisabled.copy(alpha = 0.35f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title.ifBlank { "Текущий трек" },
                color = c.textPrimary,
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1
            )
            Text(
                "Быстрые действия для mini-player",
                color = c.textSecondary,
                fontFamily = font,
                fontSize = 12.sp
            )

            MiniActionRow(
                icon = Icons.Default.PlayArrow,
                title = "Открыть плеер",
                subtitle = "Перейти к большому экрану управления",
                accent = c.accent,
                onClick = onOpenPlayer
            )
            MiniActionRow(
                icon = Icons.Default.QueueMusic,
                title = "Очередь",
                subtitle = "Сейчас в очереди $queueCount трек(ов)",
                accent = c.accentVar,
                onClick = onOpenQueue
            )
            MiniActionRow(
                icon = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                title = if (isFavourite) "Убрать из избранного" else "Добавить в избранное",
                subtitle = "Быстрый доступ к любимым трекам",
                accent = c.accentMuted,
                onClick = onToggleFavourite
            )
            MiniActionRow(
                icon = Icons.Default.Shuffle,
                title = if (shuffleEnabled) "Перемешивание включено" else "Включить перемешивание",
                subtitle = "Меняет порядок следующего воспроизведения",
                accent = c.accent,
                onClick = onToggleShuffle
            )
            MiniActionRow(
                icon = when (repeatMode) {
                    RepeatMode.ONE -> Icons.Default.RepeatOne
                    RepeatMode.ALL -> Icons.Default.Repeat
                    RepeatMode.NONE -> Icons.Default.Repeat
                },
                title = "Режим повтора: ${repeatModeLabel(repeatMode)}",
                subtitle = "Переключает цикл между нет / все / один",
                accent = c.accentVar,
                onClick = onToggleRepeat
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MiniActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: ComposeColor,
    onClick: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = c.textSecondary, fontFamily = font, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = c.textDisabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiniQueueSheet(
    queue: List<Song>,
    currentSong: Song?,
    onSelectSong: (Song) -> Unit,
    onOpenPlayer: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.textDisabled.copy(alpha = 0.35f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Очередь", color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${queue.size} трек(ов) в текущем потоке", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
                }
                FilledTonalButton(
                    onClick = onOpenPlayer,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.OpenInFull, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Плеер", fontFamily = font, fontWeight = FontWeight.SemiBold)
                }
            }

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Очередь пока пустая", color = c.textSecondary, fontFamily = font, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    itemsIndexed(queue, key = { _, song -> song.uri.toString() }) { index, song ->
                        val isCurrent = currentSong?.uri == song.uri
                        Row(
                            modifier = boomingDeleteItemModifier()
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                )
                                .border(
                                    1.dp,
                                    if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f),
                                    RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    onSelectSong(song)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = font,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    song.title.ifBlank { "Без названия" },
                                    color = c.textPrimary,
                                    fontFamily = font,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1
                                )
                                Text(
                                    if (isCurrent) "Сейчас играет · ${song.artist}" else song.artist,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = font,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            Text(
                                song.formattedDuration(),
                                color = c.textDisabled,
                                fontFamily = font,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun repeatModeLabel(mode: RepeatMode): String = when (mode) {
    RepeatMode.NONE -> "нет"
    RepeatMode.ALL -> "все"
    RepeatMode.ONE -> "один"
}
