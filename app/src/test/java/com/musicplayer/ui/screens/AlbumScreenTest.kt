package com.musicplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import com.musicplayer.data.AlbumDetail
import com.musicplayer.data.AlbumPageState
import com.musicplayer.data.AlbumTrack
import com.musicplayer.data.AppTheme
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.ui.theme.MusicPlayerTheme
import com.musicplayer.viewmodel.AlbumViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlbumScreenTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var ui by mutableStateOf(AlbumUiState())
    private val played = mutableListOf<String>()
    private val downloaded = mutableListOf<String>()

    private val album = AlbumDetail(
        id = 10L,
        title = "Мокрые кроссы",
        artist = "Тима Белорусских",
        year = "2019",
        kind = "Сингл",
        tracks = listOf(
            AlbumTrack(101L, "Мокрые кроссы", "Тима Белорусских", 197, 0),
            AlbumTrack(102L, "Мокрые кроссы (Remix)", "Тима Белорусских", 216, 1),
            AlbumTrack(103L, "Незабудка", "Тима Белорусских", 184, 2)
        )
    )

    private fun show(state: AlbumUiState) {
        ui = state
        compose.setContent {
            MusicPlayerTheme(appTheme = AppTheme.BLOOMEE) {
                MaterialTheme(typography = Typography()) {
                    CompositionLocalProvider(
                        LocalAppFontFamily provides FontFamily.SansSerif,
                        LocalDensity provides Density(LocalDensity.current.density, 1f)
                    ) {
                        AlbumContent(
                            state = ui,
                            actions = AlbumActions(
                                onBack = {},
                                onRetry = {},
                                onPlay = { played += it.title },
                                onDownload = { downloaded += it.title }
                            )
                        )
                    }
                }
            }
        }
    }

    @Test
    fun albumShowsHeaderAndTracks() {
        show(AlbumUiState(albumId = 10L, content = AlbumPageState.Success(album)))

        compose.onNodeWithTag("album_screen").assertIsDisplayed()
        compose.onNodeWithTag("album_title").assertIsDisplayed()
        compose.onNodeWithText("Треки").assertIsDisplayed()
        compose.onNodeWithText("Незабудка").assertIsDisplayed()
    }

    @Test
    fun tappingTrackStartsPlayback() {
        show(AlbumUiState(albumId = 10L, content = AlbumPageState.Success(album)))

        compose.onNodeWithText("Незабудка").performClick()

        assertEquals(listOf("Незабудка"), played)
    }

    @Test
    fun downloadButtonKeepsTrackInTheApp() {
        show(AlbumUiState(albumId = 10L, content = AlbumPageState.Success(album)))

        compose.onNodeWithTag("album_download_102").performClick()

        assertEquals(listOf("Мокрые кроссы (Remix)"), downloaded)
    }

    @Test
    fun savedTrackShowsTheCheckMark() {
        show(
            AlbumUiState(
                albumId = 10L,
                content = AlbumPageState.Success(album),
                savedTracks = setOf(101L)
            )
        )

        compose.onNodeWithContentDescription("Скачано").assertIsDisplayed()
    }

    @Test
    fun failureStateOffersRetry() {
        show(AlbumUiState(albumId = 10L, content = AlbumPageState.Empty(10L)))

        compose.onNodeWithText("Не удалось загрузить альбом").assertIsDisplayed()
        compose.onNodeWithTag("album_retry").performClick()
    }

    @Test
    fun renderAlbumDetail() {
        show(
            AlbumUiState(
                albumId = 10L,
                content = AlbumPageState.Success(album),
                tasks = mapOf(102L to AlbumViewModel.TrackTask.Downloading(42, playback = false)),
                savedTracks = setOf(103L)
            )
        )
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.findViewById<View>(android.R.id.content)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/reports/album-detail-preview").apply { mkdirs() }
            File(directory, "album-detail-dark.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
