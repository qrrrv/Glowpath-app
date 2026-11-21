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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import com.musicplayer.data.AppTheme
import com.musicplayer.data.ArtistAlbum
import com.musicplayer.data.ArtistPage
import com.musicplayer.data.ArtistPageState
import com.musicplayer.data.ArtistProfile
import com.musicplayer.data.ArtistVideo
import com.musicplayer.data.RelatedArtist
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.ui.theme.MusicPlayerTheme
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
class ArtistScreenTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var ui by mutableStateOf(ArtistUiState())
    private val opened = mutableListOf<String>()
    private val urls = mutableListOf<String>()

    private val page = ArtistPage(
        profile = ArtistProfile(id = 1L, name = "Валентин Стрыкало", fans = 245_000L, albumCount = 12),
        related = listOf(
            RelatedArtist(2L, "Кровосток"),
            RelatedArtist(3L, "Нервы")
        ),
        albums = listOf(
            ArtistAlbum(10L, "Синглы", year = "2015", kind = "Сингл", link = "https://deezer.com/album/10"),
            ArtistAlbum(11L, "Русский рок", year = "2020", kind = "Сборник", link = "https://deezer.com/album/11")
        ),
        videos = listOf(
            ArtistVideo(20L, "Наше лето", artist = "Валентин Стрыкало", externalUrl = "https://example.com/video")
        )
    )

    private fun show(state: ArtistUiState) {
        ui = state
        compose.setContent {
            MusicPlayerTheme(appTheme = AppTheme.BLOOMEE) {
                MaterialTheme(typography = Typography()) {
                    CompositionLocalProvider(
                        LocalAppFontFamily provides FontFamily.SansSerif,
                        LocalDensity provides Density(LocalDensity.current.density, 1f)
                    ) {
                        ArtistContent(
                            state = ui,
                            actions = ArtistActions(
                                onBack = {},
                                onRetry = {},
                                onOpenArtist = { opened += it },
                                onOpenUrl = { urls += it }
                            )
                        )
                    }
                }
            }
        }
    }

    @Test
    fun showsEverySectionFromTheArtistPage() {
        show(ArtistUiState(name = "Валентин Стрыкало", content = ArtistPageState.Success(page)))

        compose.onNodeWithTag("artist_screen").assertIsDisplayed()
        compose.onAllNodesWithText("Валентин Стрыкало").onFirst().assertIsDisplayed()
        compose.onNodeWithTag("artist_related_title").assertIsDisplayed()
        compose.onNodeWithText("Кровосток").assertIsDisplayed()
        compose.onNodeWithTag("artist_videos_title").assertIsDisplayed()
        compose.onNodeWithText("Наше лето").assertIsDisplayed()
        compose.onNodeWithTag("artist_albums_title").assertIsDisplayed()
        compose.onNodeWithText("Синглы").assertIsDisplayed()
    }

    @Test
    fun tappingRelatedArtistOpensItsPage() {
        show(ArtistUiState(name = "Валентин Стрыкало", content = ArtistPageState.Success(page)))

        compose.onNodeWithText("Нервы").performClick()

        assertEquals(listOf("Нервы"), opened)
    }

    @Test
    fun failureStateOffersRetry() {
        show(ArtistUiState(name = "Кто-то", content = ArtistPageState.Empty("Кто-то")))

        compose.onNodeWithText("Не удалось загрузить исполнителя").assertIsDisplayed()
        compose.onNodeWithTag("artist_retry").performClick()
    }

    @Test
    fun loaderIsShownWhileDataArrives() {
        show(ArtistUiState(name = "Кто-то", content = ArtistPageState.Loading("Кто-то")))

        compose.onNodeWithTag("artist_loading").assertIsDisplayed()
    }

    @Test
    fun renderArtistPage() {
        show(ArtistUiState(name = "Валентин Стрыкало", content = ArtistPageState.Success(page)))
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.findViewById<View>(android.R.id.content)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/reports/artist-preview").apply { mkdirs() }
            File(directory, "artist-dark.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
