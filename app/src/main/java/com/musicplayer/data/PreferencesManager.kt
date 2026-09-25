package com.musicplayer.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

class PreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)

    // ── Songs ────────────────────────────────────────────────────────────────

    fun saveSongs(songs: List<Song>) {
        val array = JSONArray()
        songs.forEach { song ->
            val obj = JSONObject().apply {
                put("id",           song.id)
                put("title",        song.title)
                put("artist",       song.artist)
                put("album",        song.album)
                put("duration",     song.duration)
                put("uri",          song.uri.toString())
                put("albumArtUri",  song.albumArtUri?.toString() ?: "")
                put("isHidden",     song.isHidden)
                put("customTitle",  song.customTitle ?: "")
                put("customArtist", song.customArtist ?: "")
                put("customAlbumArtUri", song.customAlbumArtUri?.toString() ?: "")
            }
            array.put(obj)
        }
        prefs.edit().putString("songs", array.toString()).apply()
    }

    fun loadSongs(): List<Song> {
        val raw = prefs.getString("songs", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val albumArtStr = obj.getString("albumArtUri")
                val customTitle = obj.optString("customTitle", "")
                val customArtist = obj.optString("customArtist", "")
                val customAlbumArtStr = obj.optString("customAlbumArtUri", "")
                Song(
                    id          = obj.getLong("id"),
                    title       = obj.getString("title"),
                    artist      = obj.getString("artist"),
                    album       = obj.getString("album"),
                    duration    = obj.getLong("duration"),
                    uri         = Uri.parse(obj.getString("uri")),
                    albumArtUri = if (albumArtStr.isNotBlank()) Uri.parse(albumArtStr) else null,
                    isHidden    = obj.optBoolean("isHidden", false),
                    customTitle = if (customTitle.isNotBlank()) customTitle else null,
                    customArtist = if (customArtist.isNotBlank()) customArtist else null,
                    customAlbumArtUri = if (customAlbumArtStr.isNotBlank()) Uri.parse(customAlbumArtStr) else null
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    fun saveSettings(settings: PlayerSettings) {
        prefs.edit().apply {
            putBoolean("shuffle",            settings.shuffleEnabled)
            putString("repeat_mode",         settings.repeatMode.name)
            putInt("crossfade_duration",     settings.crossfadeDuration)
            putBoolean("bass_boost",         settings.bassBoostEnabled)
            putBoolean("equalizer",          settings.equalizerEnabled)
            putBoolean("show_album_art",     settings.showAlbumArt)
            putString("theme",               settings.theme.name)
            putString("interface_style",     settings.interfaceStyle.name)
            putBoolean("interface_style_migrated_v1", true)
            putString("sort_order",          settings.sortOrder.name)
            putString("custom_font_uri",      settings.customFontUri)
            putString("selected_font_id",  settings.selectedFontId)
            putFloat("lyrics_font_scale",    settings.lyricsFontScale)
            putInt("lyrics_fade_style",      settings.lyricsFadeStyle)
            putInt("lyrics_alignment",       settings.lyricsAlignment)
            putBoolean("lyrics_curl_anim",   settings.lyricsCurlAnim)
            putBoolean("use_wavy_seekbar",   settings.useWavySeekBar)
            putBoolean("show_random_online_albums_shelf", settings.showRandomOnlineAlbumsShelf)
            // Типографика
            putFloat("player_title_size",     settings.playerTitleSize)
            putFloat("player_artist_size",    settings.playerArtistSize)
            putFloat("tracklist_title_size",  settings.trackListTitleSize)
            putFloat("tracklist_artist_size", settings.trackListArtistSize)
            putFloat("letter_spacing_em",     settings.letterSpacingEm)
            putFloat("line_height_scale",     settings.lineHeightScale)
            putBoolean("bold_titles",         settings.boldTitles)
            putBoolean("uppercase_titles",    settings.uppercaseTitles)
            putBoolean("text_shadow",         settings.textShadowEnabled)
            putFloat("text_shadow_intensity", settings.textShadowIntensity)
            putInt("track_item_density",      settings.trackItemDensity)
            putFloat("track_corner_radius",   settings.trackItemCornerRadius)
            putBoolean("show_track_number",   settings.showTrackNumber)
            putBoolean("show_duration_list",  settings.showDurationInList)
            putBoolean("show_bitrate_list",   settings.showBitrateInList)
            putBoolean("glow_now_playing",    settings.glowOnNowPlaying)
            putInt("artist_name_style",       settings.artistNameStyle)
            putFloat("player_time_size",      settings.playerTimeSize)
            putBoolean("show_album_list",     settings.showAlbumInList)
            putFloat("track_art_size",        settings.trackArtSize)
            putFloat("track_meta_opacity",    settings.trackMetaOpacity)
            putFloat("artist_letter_spacing", settings.artistLetterSpacingEm)
            putInt("track_text_align",        settings.trackTextAlign)
            putFloat("track_item_padding",    settings.trackItemPaddingScale)
            putFloat("now_playing_glow_strength", settings.nowPlayingGlowStrength)
            putBoolean("track_meta_capsule",  settings.trackMetaCapsule)
            putInt("track_title_weight_mode", settings.trackTitleWeightMode)
            putInt("track_meta_weight_mode",  settings.trackMetaWeightMode)
            putBoolean("track_title_italic",  settings.trackTitleItalic)
            putFloat("track_title_opacity",   settings.trackTitleOpacity)
            putBoolean("track_meta_uppercase", settings.trackMetaUppercase)
            putFloat("track_meta_spacing",    settings.trackMetaSpacingScale)
            putBoolean("track_title_two_lines", settings.trackTitleTwoLines)
            putInt("duration_badge_style",    settings.durationBadgeStyle)
            putFloat("track_title_accent_blend", settings.trackTitleAccentBlend)
            putInt("track_title_decor_style", settings.trackTitleDecorStyle)
            putInt("track_meta_separator_style", settings.trackMetaSeparatorStyle)
            // Анимации
            putInt("player_open_mode",        settings.playerOpenMode)
            putInt("player_enter_anim",       settings.playerEnterAnim)
            putBoolean("list_scroll_anim",    settings.listScrollAnim)
            putBoolean("row_press_anim",      settings.rowPressAnim)
            // Расширенные анимации
            putInt("screen_transition_anim",  settings.screenTransitionAnim)
            putInt("album_art_anim",          settings.albumArtAnim)
            putInt("list_item_entry_anim",    settings.listItemEntryAnim)
            putInt("mini_player_entry_anim",  settings.miniPlayerEntryAnim)
            putInt("button_press_style",      settings.buttonPressStyle)
            putInt("tab_switch_anim",         settings.tabSwitchAnim)
            putInt("track_delete_anim_style", settings.trackDeleteAnimStyle)
            // Кроссфейд вкладок
            putBoolean("tab_crossfade_enabled",  settings.tabCrossfadeEnabled)
            putInt("tab_crossfade_duration_ms",  settings.tabCrossfadeDurationMs)
            // AnimParams
            val p = settings.animParams
            putFloat("ap_player_speed",     p.playerEnterSpeed);  putInt("ap_player_easing",    p.playerEnterEasing)
            putInt("ap_player_damping",     p.playerEnterDamping); putInt("ap_player_stiff",     p.playerEnterStiffness)
            putFloat("ap_screen_speed",     p.screenTransSpeed);  putInt("ap_screen_easing",    p.screenTransEasing)
            putInt("ap_screen_damping",     p.screenTransDamping); putInt("ap_screen_stiff",     p.screenTransStiffness)
            putFloat("ap_tab_speed",        p.tabSwitchSpeed);    putInt("ap_tab_easing",       p.tabSwitchEasing)
            putInt("ap_tab_damping",        p.tabSwitchDamping);  putInt("ap_tab_stiff",        p.tabSwitchStiffness)
            putFloat("ap_art_speed",        p.albumArtSpeed);     putInt("ap_art_easing",       p.albumArtEasing)
            putInt("ap_art_damping",        p.albumArtDamping)
            putFloat("ap_list_speed",       p.listItemSpeed);     putInt("ap_list_easing",      p.listItemEasing)
            putInt("ap_list_damping",       p.listItemDamping);   putFloat("ap_list_delay",     p.listItemDelay)
            putFloat("ap_mini_speed",       p.miniPlayerSpeed);   putInt("ap_mini_easing",      p.miniPlayerEasing)
            putInt("ap_mini_damping",       p.miniPlayerDamping); putFloat("ap_mini_float",     p.miniPlayerFloatiness)
            putFloat("ap_btn_speed",        p.buttonSpeed);       putFloat("ap_btn_strength",   p.buttonStrength)
            putFloat("ap_tab_follow",       p.tabFollowThrough)
            putFloat("ap_delete_speed",     p.deleteAnimSpeed)
            putFloat("ap_delete_scatter",   p.deleteScatter)
            putFloat("ap_delete_density",   p.deleteParticleDensity)
            // Новые настройки v1
            putFloat("ui_scale", settings.uiScale)
            putFloat("mini_player_height", settings.miniPlayerHeight)
            putBoolean("pause_on_headphone_disconnect", settings.pauseOnHeadphoneDisconnect)
            putBoolean("resume_on_headphone_connect", settings.resumeOnHeadphoneConnect)
            putBoolean("show_hidden_tracks", settings.showHiddenTracks)
            putBoolean("genius_api_enabled", settings.geniusApiEnabled)
        }.apply()
    }

    fun loadSettings(): PlayerSettings {
        val savedInterfaceStyle = prefs.getString("interface_style", null)
        val migratedInterfaceStyle = prefs.getBoolean("interface_style_migrated_v1", false)
        val resolvedInterfaceStyle = when {
            savedInterfaceStyle == null -> InterfaceStyle.MATERIAL3
            !migratedInterfaceStyle && savedInterfaceStyle == InterfaceStyle.CLASSIC.name -> {
                prefs.edit()
                    .putString("interface_style", InterfaceStyle.MATERIAL3.name)
                    .putBoolean("interface_style_migrated_v1", true)
                    .apply()
                InterfaceStyle.MATERIAL3
            }
            else -> safeEnum(savedInterfaceStyle, InterfaceStyle.MATERIAL3)
        }

        return PlayerSettings(
            shuffleEnabled   = prefs.getBoolean("shuffle",         false),
            repeatMode       = safeEnum(prefs.getString("repeat_mode", null), RepeatMode.NONE),
            crossfadeDuration= prefs.getInt("crossfade_duration",  0),
            bassBoostEnabled = prefs.getBoolean("bass_boost",       false),
            equalizerEnabled = prefs.getBoolean("equalizer",        false),
            showAlbumArt     = prefs.getBoolean("show_album_art",   true),
            theme            = safeEnum(prefs.getString("theme", null), AppTheme.BLOOMEE),
            interfaceStyle   = resolvedInterfaceStyle,
            sortOrder        = safeEnum(prefs.getString("sort_order", null), SortOrder.TITLE),
            customFontUri     = prefs.getString("custom_font_uri",  "") ?: "",
            selectedFontId    = prefs.getString("selected_font_id", "montserrat") ?: "montserrat",
            lyricsFontScale   = prefs.getFloat("lyrics_font_scale", 1.0f),
            lyricsFadeStyle   = prefs.getInt("lyrics_fade_style", 1),
            lyricsAlignment   = prefs.getInt("lyrics_alignment",  0),
            lyricsCurlAnim    = prefs.getBoolean("lyrics_curl_anim", true),
            useWavySeekBar    = prefs.getBoolean("use_wavy_seekbar", true),
            showRandomOnlineAlbumsShelf = prefs.getBoolean("show_random_online_albums_shelf", false),
            // Типографика
            playerTitleSize     = prefs.getFloat("player_title_size",     22f),
            playerArtistSize    = prefs.getFloat("player_artist_size",    15f),
            trackListTitleSize  = prefs.getFloat("tracklist_title_size",  14f),
            trackListArtistSize = prefs.getFloat("tracklist_artist_size", 12f),
            letterSpacingEm     = prefs.getFloat("letter_spacing_em",     0f),
            lineHeightScale     = prefs.getFloat("line_height_scale",     1.0f),
            boldTitles          = prefs.getBoolean("bold_titles",         false),
            uppercaseTitles     = prefs.getBoolean("uppercase_titles",    false),
            textShadowEnabled   = prefs.getBoolean("text_shadow",         false),
            textShadowIntensity = prefs.getFloat("text_shadow_intensity", 0.5f),
            trackItemDensity    = prefs.getInt("track_item_density",      1),
            trackItemCornerRadius= prefs.getFloat("track_corner_radius",  12f),
            showTrackNumber     = prefs.getBoolean("show_track_number",   false),
            showDurationInList  = prefs.getBoolean("show_duration_list",  true),
            showBitrateInList   = prefs.getBoolean("show_bitrate_list",   false),
            glowOnNowPlaying    = prefs.getBoolean("glow_now_playing",    true),
            artistNameStyle     = prefs.getInt("artist_name_style",       0),
            playerTimeSize      = prefs.getFloat("player_time_size",      12f),
            showAlbumInList     = prefs.getBoolean("show_album_list",     false),
            trackArtSize        = prefs.getFloat("track_art_size",        52f),
            trackMetaOpacity    = prefs.getFloat("track_meta_opacity",    0.78f),
            artistLetterSpacingEm = prefs.getFloat("artist_letter_spacing", 0f),
            trackTextAlign      = prefs.getInt("track_text_align",        0),
            trackItemPaddingScale = prefs.getFloat("track_item_padding",  1f),
            nowPlayingGlowStrength = prefs.getFloat("now_playing_glow_strength", 0.78f),
            trackMetaCapsule    = prefs.getBoolean("track_meta_capsule",  false),
            trackTitleWeightMode = prefs.getInt("track_title_weight_mode", 2),
            trackMetaWeightMode = prefs.getInt("track_meta_weight_mode", 1),
            trackTitleItalic    = prefs.getBoolean("track_title_italic", false),
            trackTitleOpacity   = prefs.getFloat("track_title_opacity",  1f),
            trackMetaUppercase  = prefs.getBoolean("track_meta_uppercase", false),
            trackMetaSpacingScale = prefs.getFloat("track_meta_spacing", 1f),
            trackTitleTwoLines  = prefs.getBoolean("track_title_two_lines", false),
            durationBadgeStyle  = prefs.getInt("duration_badge_style",   0),
            trackTitleAccentBlend = prefs.getFloat("track_title_accent_blend", 0f),
            trackTitleDecorStyle = prefs.getInt("track_title_decor_style", 0),
            trackMetaSeparatorStyle = prefs.getInt("track_meta_separator_style", 0),
            // Анимации
            playerOpenMode      = prefs.getInt("player_open_mode",        0),
            playerEnterAnim     = prefs.getInt("player_enter_anim",       0),
            listScrollAnim      = prefs.getBoolean("list_scroll_anim",    true),
            rowPressAnim        = prefs.getBoolean("row_press_anim",      true),
            // Расширенные анимации
            screenTransitionAnim = prefs.getInt("screen_transition_anim", 0),
            albumArtAnim         = prefs.getInt("album_art_anim",         0),
            listItemEntryAnim    = prefs.getInt("list_item_entry_anim",   0),
            miniPlayerEntryAnim  = prefs.getInt("mini_player_entry_anim", 0),
            buttonPressStyle     = prefs.getInt("button_press_style",     0),
            tabSwitchAnim        = prefs.getInt("tab_switch_anim",        0),
            trackDeleteAnimStyle = prefs.getInt("track_delete_anim_style", 3),
            tabCrossfadeEnabled  = prefs.getBoolean("tab_crossfade_enabled", false),
            tabCrossfadeDurationMs = prefs.getInt("tab_crossfade_duration_ms", 400),
            animParams           = AnimParams(
                playerEnterSpeed   = prefs.getFloat("ap_player_speed",  1f),
                playerEnterEasing  = prefs.getInt("ap_player_easing",   0),
                playerEnterDamping = prefs.getInt("ap_player_damping",  0),
                playerEnterStiffness= prefs.getInt("ap_player_stiff",   0),
                screenTransSpeed   = prefs.getFloat("ap_screen_speed",  1f),
                screenTransEasing  = prefs.getInt("ap_screen_easing",   0),
                screenTransDamping = prefs.getInt("ap_screen_damping",  0),
                screenTransStiffness= prefs.getInt("ap_screen_stiff",   0),
                tabSwitchSpeed     = prefs.getFloat("ap_tab_speed",     1f),
                tabSwitchEasing    = prefs.getInt("ap_tab_easing",      0),
                tabSwitchDamping   = prefs.getInt("ap_tab_damping",     0),
                tabSwitchStiffness = prefs.getInt("ap_tab_stiff",       0),
                albumArtSpeed      = prefs.getFloat("ap_art_speed",     1f),
                albumArtEasing     = prefs.getInt("ap_art_easing",      0),
                albumArtDamping    = prefs.getInt("ap_art_damping",     0),
                listItemSpeed      = prefs.getFloat("ap_list_speed",    1f),
                listItemEasing     = prefs.getInt("ap_list_easing",     0),
                listItemDamping    = prefs.getInt("ap_list_damping",    0),
                listItemDelay      = prefs.getFloat("ap_list_delay",    1f),
                miniPlayerSpeed    = prefs.getFloat("ap_mini_speed",    1f),
                miniPlayerEasing   = prefs.getInt("ap_mini_easing",     0),
                miniPlayerDamping  = prefs.getInt("ap_mini_damping",    0),
                miniPlayerFloatiness = prefs.getFloat("ap_mini_float",  1f),
                buttonSpeed        = prefs.getFloat("ap_btn_speed",     1f),
                buttonStrength     = prefs.getFloat("ap_btn_strength",  1f),
                tabFollowThrough   = prefs.getFloat("ap_tab_follow",    1f),
                deleteAnimSpeed    = prefs.getFloat("ap_delete_speed",  1f),
                deleteScatter      = prefs.getFloat("ap_delete_scatter", 1f),
                deleteParticleDensity = prefs.getFloat("ap_delete_density", 1f),
            ),
            uiScale = prefs.getFloat("ui_scale", 1.0f),
            miniPlayerHeight = prefs.getFloat("mini_player_height", 72f),
            pauseOnHeadphoneDisconnect = prefs.getBoolean("pause_on_headphone_disconnect", true),
            resumeOnHeadphoneConnect = prefs.getBoolean("resume_on_headphone_connect", false),
            showHiddenTracks = prefs.getBoolean("show_hidden_tracks", false),
            geniusApiEnabled = prefs.getBoolean("genius_api_enabled", true)
        )
    }

    // ── Volume / Speed ────────────────────────────────────────────────────────

    fun saveVolume(volume: Float)         = prefs.edit().putFloat("volume", volume).apply()
    fun loadVolume(): Float               = prefs.getFloat("volume", 0.8f)

    fun saveTransitionSettings(t: com.musicplayer.data.TransitionSettings) {
        prefs.edit()
            .putString("transition_mode",      t.mode.name)
            .putInt("transition_duration_ms",  t.durationMs)
            .putString("transition_curve_in",  t.curveIn.name)
            .putString("transition_curve_out", t.curveOut.name)
            .commit()  // synchronous write — avoids lost data if app is killed/rotated
    }

    fun loadTransitionSettings(): com.musicplayer.data.TransitionSettings {
        return com.musicplayer.data.TransitionSettings(
            mode       = safeEnum(prefs.getString("transition_mode", null), com.musicplayer.data.TransitionMode.NONE),
            durationMs = prefs.getInt("transition_duration_ms", 2000),
            curveIn    = safeEnum(prefs.getString("transition_curve_in", null), com.musicplayer.data.Curve.EASE_IN),
            curveOut   = safeEnum(prefs.getString("transition_curve_out", null), com.musicplayer.data.Curve.EASE_OUT)
        )
    }

    fun saveSpeed(speed: Float)           = prefs.edit().putFloat("speed", speed).apply()
    fun loadSpeed(): Float                = prefs.getFloat("speed", 1.0f)

    // ── EQ Gains ─────────────────────────────────────────────────────────────

    fun saveEqGains(gains: List<Float>) {
        val arr = JSONArray()
        gains.forEach { arr.put(it.toDouble()) }
        prefs.edit().putString("eq_gains", arr.toString()).apply()
    }

    fun loadEqGains(): List<Float> {
        val raw = prefs.getString("eq_gains", null) ?: return List(10) { 0f }
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getDouble(it).toFloat() }
        } catch (e: Exception) {
            List(10) { 0f }
        }
    }

    fun saveEqEnabled(enabled: Boolean) = prefs.edit().putBoolean("eq_enabled_v2", enabled).apply()
    fun loadEqEnabled(): Boolean        = prefs.getBoolean("eq_enabled_v2", false)

    fun saveEqPresetName(name: String)  = prefs.edit().putString("eq_preset_name", name).apply()
    fun loadEqPresetName(): String      = prefs.getString("eq_preset_name", "Плоский") ?: "Плоский"

    fun saveAudioOutput(output: String) = prefs.edit().putString("audio_output", output).apply()
    fun loadAudioOutput(): String       = prefs.getString("audio_output", "STEREO") ?: "STEREO"

    fun saveSpatialAudio(enabled: Boolean) = prefs.edit().putBoolean("spatial_audio", enabled).apply()
    fun loadSpatialAudio(): Boolean        = prefs.getBoolean("spatial_audio", false)

    // ── Pre-Amp gain (-12..+12 dB) ───────────────────────────────────────────
    fun savePreAmpGain(gain: Float) = prefs.edit().putFloat("preamp_gain", gain).apply()
    fun loadPreAmpGain(): Float     = prefs.getFloat("preamp_gain", 0f)

    // ── L/R Balance (-1f = full left, 0f = center, 1f = full right) ───────────────
    fun saveAudioBalance(balance: Float) = prefs.edit().putFloat("audio_balance", balance).apply()
    fun loadAudioBalance(): Float        = prefs.getFloat("audio_balance", 0f)

    // ── Lyrics (plain text, stored per song ID) ───────────────────────────────

    fun saveLyrics(songId: Long, lyrics: String) =
        prefs.edit().putString("lyrics_$songId", lyrics).apply()

    fun loadLyrics(songId: Long): String =
        prefs.getString("lyrics_$songId", "") ?: ""

    fun deleteLyrics(songId: Long) =
        prefs.edit().remove("lyrics_$songId").apply()

    // ── Custom Artwork per song ───────────────────────────────────────────────

    fun saveCustomArt(songId: Long, uri: String?) {
        if (uri.isNullOrBlank()) {
            prefs.edit().remove("custom_art_$songId").apply()
        } else {
            prefs.edit().putString("custom_art_$songId", uri).apply()
        }
    }

    fun loadCustomArt(songId: Long): Uri? {
        val raw = prefs.getString("custom_art_$songId", null)
        return if (raw.isNullOrBlank()) null else Uri.parse(raw)
    }

    fun loadAllCustomArts(): Map<Long, Uri> {
        return prefs.all
            .filter { it.key.startsWith("custom_art_") }
            .mapNotNull { (key, value) ->
                val songId = key.removePrefix("custom_art_").toLongOrNull() ?: return@mapNotNull null
                val uri = (value as? String)?.let { if (it.isNotBlank()) Uri.parse(it) else null } ?: return@mapNotNull null
                songId to uri
            }.toMap()
    }

    // ── Orb Settings ──────────────────────────────────────────────────────────

    fun saveOrbSettings(orb: OrbSettings) {
        prefs.edit()
            // Базовые
            .putFloat("orb_speed",           orb.speed)
            .putFloat("orb_contrast",        orb.contrast)
            .putFloat("orb_coverage",        orb.coverage)
            .putBoolean("orb_bass",          orb.bassReactive)
            .putBoolean("orb_lyrics",        orb.showInLyrics)
            .putBoolean("orb_in_player",     orb.showInPlayer)
            // Количество и форма
            .putInt("orb_count",             orb.orbCount)
            .putInt("orb_shape",             orb.orbShape)
            .putFloat("orb_spread",          orb.orbSpread)
            .putFloat("orb_v_bias",          orb.verticalBias)
            // Визуал
            .putFloat("orb_glow",            orb.glowIntensity)
            .putFloat("orb_blur",            orb.blurRadius)
            .putFloat("orb_saturation",      orb.saturation)
            .putFloat("orb_brightness",      orb.brightness)
            .putBoolean("orb_border_glow",   orb.borderGlow)
            .putFloat("orb_border_thick",    orb.borderThickness)
            .putBoolean("orb_frosted",       orb.frostedGlass)
            .putFloat("orb_noise",           orb.noiseAmount)
            // Цвет
            .putFloat("orb_color_shift",     orb.colorShift)
            .putFloat("orb_color_pull",      orb.colorPullStrength)
            .putFloat("orb_color_cycle",     orb.colorCycleSpeed)
            .putBoolean("orb_chroma",        orb.chromaEffect)
            // Движение
            .putBoolean("orb_rotation",      orb.rotationEnabled)
            .putBoolean("orb_magnetic",      orb.magneticToArt)
            .putFloat("orb_mag_strength",    orb.magneticStrength)
            .putBoolean("orb_pulse",         orb.pulseOnBeat)
            .putFloat("orb_beat_scale",      orb.beatScale)
            .putBoolean("orb_wave_mode",     orb.waveMode)
            .putInt("orb_flow_mode",         orb.flowMode)
            // Эффекты
            .putBoolean("orb_trail",         orb.trailEffect)
            .putFloat("orb_trail_len",       orb.trailLength)
            .putBoolean("orb_particles",     orb.particleEmission)
            .putInt("orb_particle_count",    orb.particleCount)
            .putBoolean("orb_kaleid",        orb.kaleidoscopeMode)
            .putBoolean("orb_depth",         orb.depthEffect)
            .putBoolean("orb_viz_bars",      orb.showVisualizerBars)
            .apply()
    }

    fun loadOrbSettings(): OrbSettings = OrbSettings(
        // Базовые
        speed             = prefs.getFloat("orb_speed",        1.0f),
        contrast          = prefs.getFloat("orb_contrast",     0.7f),
        coverage          = prefs.getFloat("orb_coverage",     0.9f),
        bassReactive      = prefs.getBoolean("orb_bass",       true),
        showInLyrics      = prefs.getBoolean("orb_lyrics",     true),
        showInPlayer      = prefs.getBoolean("orb_in_player",  true),
        // Количество и форма
        orbCount          = prefs.getInt("orb_count",          3),
        orbShape          = prefs.getInt("orb_shape",          0),
        orbSpread         = prefs.getFloat("orb_spread",       0.6f),
        verticalBias      = prefs.getFloat("orb_v_bias",       0f),
        // Визуал
        glowIntensity     = prefs.getFloat("orb_glow",         0.6f),
        blurRadius        = prefs.getFloat("orb_blur",         0.7f),
        saturation        = prefs.getFloat("orb_saturation",   0.8f),
        brightness        = prefs.getFloat("orb_brightness",   0.75f),
        borderGlow        = prefs.getBoolean("orb_border_glow",false),
        borderThickness   = prefs.getFloat("orb_border_thick", 0.3f),
        frostedGlass      = prefs.getBoolean("orb_frosted",    false),
        noiseAmount       = prefs.getFloat("orb_noise",        0.1f),
        // Цвет
        colorShift        = prefs.getFloat("orb_color_shift",  0f),
        colorPullStrength = prefs.getFloat("orb_color_pull",   0.5f),
        colorCycleSpeed   = prefs.getFloat("orb_color_cycle",  0f),
        chromaEffect      = prefs.getBoolean("orb_chroma",     false),
        // Движение
        rotationEnabled   = prefs.getBoolean("orb_rotation",   true),
        magneticToArt     = prefs.getBoolean("orb_magnetic",   false),
        magneticStrength  = prefs.getFloat("orb_mag_strength",  0.3f),
        pulseOnBeat       = prefs.getBoolean("orb_pulse",       true),
        beatScale         = prefs.getFloat("orb_beat_scale",    0.18f),
        waveMode          = prefs.getBoolean("orb_wave_mode",   false),
        flowMode          = prefs.getInt("orb_flow_mode",       0),
        // Эффекты
        trailEffect       = prefs.getBoolean("orb_trail",       false),
        trailLength       = prefs.getFloat("orb_trail_len",     0.4f),
        particleEmission  = prefs.getBoolean("orb_particles",   false),
        particleCount     = prefs.getInt("orb_particle_count",  20),
        kaleidoscopeMode  = prefs.getBoolean("orb_kaleid",      false),
        depthEffect       = prefs.getBoolean("orb_depth",       false),
        showVisualizerBars= prefs.getBoolean("orb_viz_bars",    false)
    )

    // ── Top Bar Settings ──────────────────────────────────────────────────────

    fun saveTopBarSettings(t: TopBarSettings) {
        prefs.edit().apply {
            putInt("tb_style_preset",      t.stylePreset)
            putBoolean("tb_show_icon",     t.showIcon)
            putInt("tb_icon_style",        t.iconStyle)
            putInt("tb_icon_anim",         t.iconAnimation)
            putFloat("tb_icon_size",       t.iconSize)
            putFloat("tb_icon_glow",       t.iconGlowRadius)
            putBoolean("tb_icon_ring",     t.iconRingVisible)
            putString("tb_title_text",     t.titleText)
            putFloat("tb_title_size",      t.titleSize)
            putInt("tb_title_weight",      t.titleWeight)
            putFloat("tb_title_spacing",   t.titleLetterSpacing)
            putFloat("tb_title_glow",      t.titleGlowStrength)
            putBoolean("tb_show_count",    t.showSongCount)
            putString("tb_count_prefix",   t.songCountPrefix)
            putFloat("tb_subtitle_size",   t.subtitleSize)
            putInt("tb_bg_style",          t.bgStyle)
            putFloat("tb_bg_opacity",      t.bgOpacity)
            putBoolean("tb_orbs_visible",  t.orbsVisible)
            putInt("tb_orb_count",         t.orbCount)
            putFloat("tb_orb_opacity",     t.orbOpacity)
            putFloat("tb_orb_speed",       t.orbSpeed)
            putFloat("tb_orb_size",        t.orbSize)
            putInt("tb_orb_color_mode",    t.orbColorMode)
            putBoolean("tb_sparkles",      t.sparklesVisible)
            putInt("tb_sparkle_count",     t.sparkleCount)
            putFloat("tb_sparkle_speed",   t.sparkleSpeed)
            putInt("tb_divider_style",     t.dividerStyle)
            putFloat("tb_divider_opacity", t.dividerOpacity)
            putFloat("tb_height_extra",    t.headerHeightExtra)
            putInt("tb_btn_style",         t.buttonStyle)
            putFloat("tb_btn_corner",      t.buttonCorner)
            putBoolean("tb_accent_btns",   t.accentButtons)
            putFloat("tb_btn_size",        t.buttonSize)
            putBoolean("tb_btn_border",    t.buttonBorderVisible)
            putFloat("tb_btn_gap",         t.buttonGap)
            putBoolean("tb_title_italic",  t.titleItalic)
            putInt("tb_title_color_mode",  t.titleColorMode)
            putBoolean("tb_icon_bg",       t.iconBgVisible)
            putInt("tb_icon_tint_mode",    t.iconTintMode)
            putFloat("tb_divider_thick",   t.dividerThickness)
            putInt("tb_glass_tint",        t.glassTintMode)
            putFloat("tb_glass_depth",     t.glassDepth)
            putFloat("tb_edge_shine",      t.edgeShine)
            putBoolean("tb_title_capsule", t.titleCapsuleVisible)
            putFloat("tb_title_capsule_opacity", t.titleCapsuleOpacity)
            putInt("tb_subtitle_style",    t.subtitleStyle)
        }.apply()
    }

    fun loadTopBarSettings(): TopBarSettings = TopBarSettings(
        stylePreset       = prefs.getInt("tb_style_preset",     6),
        showIcon          = prefs.getBoolean("tb_show_icon",    true),
        iconStyle         = prefs.getInt("tb_icon_style",       1),
        iconAnimation     = prefs.getInt("tb_icon_anim",        4),
        iconSize          = prefs.getFloat("tb_icon_size",      30f),
        iconGlowRadius    = prefs.getFloat("tb_icon_glow",      0.82f),
        iconRingVisible   = prefs.getBoolean("tb_icon_ring",    true),
        titleText         = prefs.getString("tb_title_text",    "Bloomee Music") ?: "Bloomee Music",
        titleSize         = prefs.getFloat("tb_title_size",     23f),
        titleWeight       = prefs.getInt("tb_title_weight",     0),
        titleLetterSpacing= prefs.getFloat("tb_title_spacing",  -0.3f),
        titleGlowStrength = prefs.getFloat("tb_title_glow",     0.14f),
        showSongCount     = prefs.getBoolean("tb_show_count",   true),
        songCountPrefix   = prefs.getString("tb_count_prefix",  "") ?: "",
        subtitleSize      = prefs.getFloat("tb_subtitle_size",  11f),
        bgStyle           = prefs.getInt("tb_bg_style",         5),
        bgOpacity         = prefs.getFloat("tb_bg_opacity",     0.94f),
        orbsVisible       = prefs.getBoolean("tb_orbs_visible", true),
        orbCount          = prefs.getInt("tb_orb_count",        3),
        orbOpacity        = prefs.getFloat("tb_orb_opacity",    0.28f),
        orbSpeed          = prefs.getFloat("tb_orb_speed",      1.1f),
        orbSize           = prefs.getFloat("tb_orb_size",       0.78f),
        orbColorMode      = prefs.getInt("tb_orb_color_mode",   1),
        sparklesVisible   = prefs.getBoolean("tb_sparkles",     true),
        sparkleCount      = prefs.getInt("tb_sparkle_count",    10),
        sparkleSpeed      = prefs.getFloat("tb_sparkle_speed",  1.15f),
        dividerStyle      = prefs.getInt("tb_divider_style",    3),
        dividerOpacity    = prefs.getFloat("tb_divider_opacity",0.72f),
        headerHeightExtra = prefs.getFloat("tb_height_extra",   0f),
        buttonStyle       = prefs.getInt("tb_btn_style",        5),
        buttonCorner      = prefs.getFloat("tb_btn_corner",     18f),
        accentButtons     = prefs.getBoolean("tb_accent_btns",  true),
        buttonSize        = prefs.getFloat("tb_btn_size",       44f),
        buttonBorderVisible = prefs.getBoolean("tb_btn_border", true),
        buttonGap         = prefs.getFloat("tb_btn_gap",        4f),
        titleItalic       = prefs.getBoolean("tb_title_italic", false),
        titleColorMode    = prefs.getInt("tb_title_color_mode", 2),
        iconBgVisible     = prefs.getBoolean("tb_icon_bg",      true),
        iconTintMode      = prefs.getInt("tb_icon_tint_mode",   0),
        dividerThickness  = prefs.getFloat("tb_divider_thick",  1.5f),
        glassTintMode     = prefs.getInt("tb_glass_tint",       0),
        glassDepth        = prefs.getFloat("tb_glass_depth",    0.72f),
        edgeShine         = prefs.getFloat("tb_edge_shine",     0.58f),
        titleCapsuleVisible = prefs.getBoolean("tb_title_capsule", false),
        titleCapsuleOpacity = prefs.getFloat("tb_title_capsule_opacity", 0.20f),
        subtitleStyle     = prefs.getInt("tb_subtitle_style",   0)
    )

    // ── Favourites ────────────────────────────────────────────────────────────

    fun saveFavourites(ids: Set<Long>) =
        prefs.edit().putStringSet("favourites", ids.map { it.toString() }.toSet()).apply()

    fun loadFavourites(): Set<Long> =
        prefs.getStringSet("favourites", emptySet())
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

    // ── User Albums ──────────────────────────────────────────────────────────

    fun saveUserAlbums(albums: List<UserAlbum>) {
        val array = JSONArray()
        albums.forEach { album ->
            val songIds = JSONArray().apply { album.songIds.forEach { put(it) } }
            array.put(
                JSONObject().apply {
                    put("id", album.id)
                    put("name", album.name)
                    put("songIds", songIds)
                    put("createdAt", album.createdAt)
                    put("updatedAt", album.updatedAt)
                    put("description", album.description)
                    put("isPinned", album.isPinned)
                    if (album.coverSongId != null) {
                        put("coverSongId", album.coverSongId)
                    } else {
                        put("coverSongId", JSONObject.NULL)
                    }
                    if (album.customCoverUri != null) {
                        put("customCoverUri", album.customCoverUri)
                    } else {
                        put("customCoverUri", JSONObject.NULL)
                    }
                }
            )
        }
        prefs.edit().putString("user_albums_v1", array.toString()).apply()
    }

    fun loadUserAlbums(): List<UserAlbum> {
        val raw = prefs.getString("user_albums_v1", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id").trim().ifBlank { return@mapNotNull null }
                val name = obj.optString("name").trim().ifBlank { "Мой плейлист" }
                val songIdsJson = obj.optJSONArray("songIds") ?: JSONArray()
                val songIds = buildList {
                    for (songIndex in 0 until songIdsJson.length()) {
                        val songId = songIdsJson.optLong(songIndex, Long.MIN_VALUE)
                        if (songId != Long.MIN_VALUE) add(songId)
                    }
                }.distinct()
                val coverSongId = if (obj.isNull("coverSongId")) null else obj.optLong("coverSongId")
                val createdAt = obj.optLong("createdAt").takeIf { it > 0L } ?: System.currentTimeMillis()
                val updatedAt = obj.optLong("updatedAt").takeIf { it > 0L } ?: createdAt
                val customCoverUri = obj.optString("customCoverUri").trim().ifBlank { null }
                val motionCoverUri: String? = null
                val description = obj.optString("description").trim()
                val isPinned = obj.optBoolean("isPinned", false)
                UserAlbum(
                    id = id,
                    name = name,
                    songIds = songIds,
                    coverSongId = coverSongId,
                    customCoverUri = customCoverUri,
                    motionCoverUri = motionCoverUri,
                    description = description,
                    isPinned = isPinned,
                    updatedAt = updatedAt,
                    createdAt = createdAt
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Custom Theme Colors ───────────────────────────────────────────────────

    fun saveCustomTheme(c: CustomThemeColors) {
        prefs.edit().apply {
            putLong("ct_bgDeep",        c.bgDeep)
            putLong("ct_bgSurface",     c.bgSurface)
            putLong("ct_bgCard",        c.bgCard)
            putLong("ct_bgElevated",    c.bgElevated)
            putLong("ct_accent",        c.accent)
            putLong("ct_accentVar",     c.accentVar)
            putLong("ct_accentMuted",   c.accentMuted)
            putLong("ct_textPrimary",   c.textPrimary)
            putLong("ct_textSecondary", c.textSecondary)
            putLong("ct_textDisabled",  c.textDisabled)
            putLong("ct_divider",       c.divider)
            putFloat("ct_btnCorner",    c.buttonCornerRadius)
            putFloat("ct_cardCorner",   c.cardCornerRadius)
            putFloat("ct_sliderH",      c.sliderTrackHeight)
            putBoolean("ct_gradBg",     c.useGradientBg)
            putBoolean("ct_lightTheme", c.isLightTheme)
        }.apply()
    }

    fun loadCustomTheme(): CustomThemeColors = CustomThemeColors(
        bgDeep            = prefs.getLong("ct_bgDeep",        0xFF1C1008),
        bgSurface         = prefs.getLong("ct_bgSurface",     0xFF2A1A0A),
        bgCard            = prefs.getLong("ct_bgCard",         0xFF321E0D),
        bgElevated        = prefs.getLong("ct_bgElevated",    0xFF3D2612),
        accent            = prefs.getLong("ct_accent",         0xFFE8B88A),
        accentVar         = prefs.getLong("ct_accentVar",      0xFFD4956A),
        accentMuted       = prefs.getLong("ct_accentMuted",    0xFF8B6040),
        textPrimary       = prefs.getLong("ct_textPrimary",   0xFFF0DCC0),
        textSecondary     = prefs.getLong("ct_textSecondary", 0xFFB8956A),
        textDisabled      = prefs.getLong("ct_textDisabled",  0xFF6B4E30),
        divider           = prefs.getLong("ct_divider",        0xFF3D2612),
        buttonCornerRadius= prefs.getFloat("ct_btnCorner",    50f),
        cardCornerRadius  = prefs.getFloat("ct_cardCorner",   16f),
        sliderTrackHeight = prefs.getFloat("ct_sliderH",      4f),
        useGradientBg     = prefs.getBoolean("ct_gradBg",     true),
        isLightTheme      = prefs.getBoolean("ct_lightTheme", false)
    )

    // ── Custom Title / Artist overrides per song ──────────────────────────────

    fun saveCustomTitle(songId: Long, title: String?) {
        if (title.isNullOrBlank()) prefs.edit().remove("custom_title_$songId").apply()
        else prefs.edit().putString("custom_title_$songId", title).apply()
    }

    fun loadAllCustomTitles(): Map<Long, String> {
        return prefs.all
            .filter { it.key.startsWith("custom_title_") }
            .mapNotNull { (key, value) ->
                val id = key.removePrefix("custom_title_").toLongOrNull() ?: return@mapNotNull null
                val t  = value as? String ?: return@mapNotNull null
                id to t
            }.toMap()
    }

    fun saveCustomArtist(songId: Long, artist: String?) {
        if (artist.isNullOrBlank()) prefs.edit().remove("custom_artist_$songId").apply()
        else prefs.edit().putString("custom_artist_$songId", artist).apply()
    }

    fun loadAllCustomArtists(): Map<Long, String> {
        return prefs.all
            .filter { it.key.startsWith("custom_artist_") }
            .mapNotNull { (key, value) ->
                val id = key.removePrefix("custom_artist_").toLongOrNull() ?: return@mapNotNull null
                val a  = value as? String ?: return@mapNotNull null
                id to a
            }.toMap()
    }

    // ── First-launch welcome ─────────────────────────────────────────────────

    fun hasSeenWelcome(): Boolean = prefs.getBoolean("has_seen_welcome", false)

    fun markWelcomeSeen() = prefs.edit().putBoolean("has_seen_welcome", true).apply()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private inline fun <reified T : Enum<T>> safeEnum(name: String?, default: T): T {
        if (name == null) return default
        return try { enumValueOf(name) } catch (e: Exception) { default }
    }
}
