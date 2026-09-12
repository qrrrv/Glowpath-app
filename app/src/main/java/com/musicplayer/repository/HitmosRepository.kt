package com.musicplayer.repository

import android.util.Log
import com.musicplayer.data.OnlineAlbumDetail
import com.musicplayer.data.OnlineAlbumSection
import com.musicplayer.data.OnlineAlbumSummary
import com.musicplayer.data.OnlineSearchPayload
import com.musicplayer.data.OnlineSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

object HitmosRepository {

    private const val TAG = "HitmosRepository"

    private val BASE_URLS = listOf(
        "https://eu.hitmoz.com",
        "https://hitmos.me",
        "https://music.hitmo.net"
    )

    private val USER_AGENTS = listOf(
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Android 13; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0",
        "Mozilla/5.0 (Linux; Android 12; SM-G998B) AppleWebKit/537.36 Chrome/120.0.6099.144 Mobile Safari/537.36"
    )

    suspend fun search(query: String): OnlineSearchPayload = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val (doc, baseUrl) = fetchDocument("/search?q=$encoded")
        val structured = parseHitmozSearchPayload(doc, baseUrl)
        val songs = structured?.songs?.ifEmpty { parseSongs(doc, baseUrl) } ?: parseSongs(doc, baseUrl)
        val albums = structured?.albums?.ifEmpty { parseAlbums(doc, baseUrl) } ?: parseAlbums(doc, baseUrl)
        OnlineSearchPayload(songs = dedupeSongs(songs), albums = dedupeAlbums(albums))
    }

    suspend fun fetchAlbumDetail(albumUrl: String): OnlineAlbumDetail = withContext(Dispatchers.IO) {
        val (doc, baseUrl) = fetchDocument(albumUrl)
        val resolvedAlbumUrl = resolveUrl(albumUrl, baseUrl) ?: albumUrl
        parseHitmozAlbumDetail(doc = doc, albumUrl = resolvedAlbumUrl, baseUrl = baseUrl)
            ?: parseAlbumDetail(doc = doc, albumUrl = resolvedAlbumUrl, baseUrl = baseUrl)
    }

    private fun fetchDocument(target: String): Pair<Document, String> {
        var lastError: Exception? = null
        for (candidate in candidateUrls(target)) {
            try {
                val response = buildConnection(candidate.url, candidate.baseUrl).execute()
                if (response.statusCode() !in 200..299) {
                    throw IllegalStateException("HTTP ${response.statusCode()} for ${candidate.url}")
                }
                val finalUrl = response.url().toString()
                val finalBaseUrl = finalUrl.toBaseUrl()
                Log.d(TAG, "Loaded ${candidate.url} via $finalBaseUrl")
                return response.parse() to finalBaseUrl
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Failed to load ${candidate.url}", e)
            }
        }
        throw lastError ?: IllegalStateException("Не удалось загрузить страницу")
    }

    private fun buildConnection(url: String, baseUrl: String): Connection {
        return Jsoup.connect(url)
            .userAgent(USER_AGENTS.random())
            .referrer("$baseUrl/")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .timeout(15_000)
            .followRedirects(true)
    }

    private fun parseSongs(doc: Element, baseUrl: String): List<OnlineSong> {
        val results = mutableListOf<OnlineSong>()

        val containers = buildList {
            addAll(doc.select("li"))
            addAll(doc.select(".item, .track-item, .song-item, .audio-item, .mp3-item, article, .card, .track, .result, .playlist-item"))
        }

        for (container in containers) {
            val mp3Url = extractMp3Url(container, baseUrl) ?: continue
            val song = extractSongFromElement(container, mp3Url, baseUrl)
            if (song != null) results += song
        }

        if (results.isEmpty()) {
            val links = doc.select("a[href~=\\.mp3], [data-url*=\\.mp3], [data-mp3], [data-audio]")
            for (link in links) {
                val mp3Url = extractMp3Url(link, baseUrl) ?: continue
                val text = deTranslitIfNeeded(link.text().trim())
                val (artist, title) = splitAndClean(text.ifBlank { deTranslitIfNeeded(filenameFromUrl(mp3Url)) })
                val duration = link.parent()?.selectFirst(".time, .duration, span.time, .dur")?.text()?.trim() ?: ""
                val sizeLabel = link.parent()?.selectFirst(".size, .file-size, .filesize")?.text()?.trim() ?: ""
                results += OnlineSong(
                    title = title,
                    artist = artist,
                    duration = duration,
                    downloadUrl = mp3Url,
                    sizeLabel = sizeLabel,
                    coverUrl = extractTrackCoverUrl(link.parent() ?: link, baseUrl)
                )
            }
        }

        return dedupeSongs(results)
    }

    private fun parseAlbums(doc: Document, baseUrl: String): List<OnlineAlbumSummary> {
        parseHitmozSearchPayload(doc, baseUrl)?.albums?.takeIf { it.isNotEmpty() }?.let { return it }

        val results = mutableListOf<OnlineAlbumSummary>()
        val seen = mutableSetOf<String>()

        val albumLinks = doc.select(
            "a[href*=/album/], a[href*=album/], a[href*=album?id], .album a[href], .albums a[href], .release a[href], article a[href]"
        )

        for (link in albumLinks) {
            val rawHref = link.absUrl("href").ifBlank { link.attr("href") }
            val albumUrl = resolveUrl(rawHref, baseUrl) ?: continue
            if (!looksLikeAlbumUrl(albumUrl) || !seen.add(albumUrl)) continue

            val card = findAlbumCard(link)
            val title = cleanTitle(
                firstNonBlank(
                    card.selectFirst(".album-title, .title, h3, h2, strong, [itemprop=name]")?.text(),
                    link.attr("title"),
                    link.text()
                )
            )
            if (title.isBlank()) continue

            val artist = cleanArtist(
                firstNonBlank(
                    card.selectFirst(".artist, .album-artist, .subtitle, .meta a[href*=/artist/], [itemprop=byArtist]")?.text(),
                    link.parent()?.selectFirst(".artist, .subtitle")?.text()
                )
            )

            val subtitle = extractAlbumMeta(card, title, artist)
            val badge = extractAlbumBadge(card)
            val coverUrl = extractImageUrl(card, baseUrl)

            results += OnlineAlbumSummary(
                title = title,
                artist = artist,
                albumUrl = albumUrl,
                coverUrl = coverUrl,
                subtitle = subtitle,
                badge = badge
            )
        }

        return dedupeAlbums(results)
    }

    private fun parseAlbumDetail(doc: Document, albumUrl: String, baseUrl: String): OnlineAlbumDetail {
        val header = doc.selectFirst(
            ".album, .album-page, .release, .content, .page, main, body"
        ) ?: doc.body()

        val title = cleanTitle(
            firstNonBlank(
                header.selectFirst("h1, .album-title, .release-title, [itemprop=name]")?.text(),
                doc.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" - "),
                doc.title().substringBefore(" - ")
            )
        )

        val artist = cleanArtist(
            firstNonBlank(
                header.selectFirst(".artist, .album-artist, .release-artist, [itemprop=byArtist], .breadcrumbs a[href*=/artist/]")?.text(),
                doc.selectFirst("meta[property=og:title]")?.attr("content")?.substringAfter(" - ", "")
            )
        )

        val description = firstNonBlank(
            header.selectFirst(".description, .album-description, .release-description, .lead, .summary")?.text(),
            doc.selectFirst("meta[name=description]")?.attr("content")
        )

        val chips = header.select(
            ".tag, .badge, .meta span, .meta a, .info span, .release-meta span, .album-meta span"
        )
            .mapNotNull { it.text().trim().takeIf { text -> text.isNotBlank() && text.length <= 40 } }
            .distinct()
            .take(6)

        val coverUrl = firstNonBlank(
            extractImageUrl(header, baseUrl),
            doc.selectFirst("meta[property=og:image]")?.attr("content")
        )

        val sections = parseAlbumSections(doc, baseUrl)

        return OnlineAlbumDetail(
            albumUrl = albumUrl,
            title = title.ifBlank { "Альбом" },
            artist = artist,
            coverUrl = coverUrl,
            description = description,
            chips = chips,
            sections = sections
        )
    }

    private fun parseAlbumSections(doc: Document, baseUrl: String): List<OnlineAlbumSection> {
        val visitedTracks = mutableSetOf<String>()
        val sections = mutableListOf<OnlineAlbumSection>()

        val containers = doc.select(
            ".track-list, .tracks, .songs, .playlist, .album-tracks, .release-tracks, section, article, ol, ul"
        )

        for (container in containers) {
            val tracks = parseSongs(container, baseUrl)
                .filter { visitedTracks.add(it.downloadUrl) }
            if (tracks.isEmpty()) continue

            val title = firstNonBlank(
                container.selectFirst("h2, h3, h4, .section-title, .title, .heading")?.text(),
                container.previousElementSibling()?.takeIf { it.tagName().matches(Regex("h[1-4]")) }?.text()
            )
            val subtitle = container.selectFirst(".subtitle, .section-subtitle, .meta")?.text()?.trim().orEmpty()
            sections += OnlineAlbumSection(
                title = title.ifBlank { if (sections.isEmpty()) "Треки" else "Раздел ${sections.size + 1}" },
                subtitle = subtitle,
                tracks = tracks
            )
        }

        if (sections.isNotEmpty()) return sections

        val tracks = parseSongs(doc, baseUrl)
        return if (tracks.isEmpty()) {
            emptyList()
        } else {
            listOf(OnlineAlbumSection(title = "Треки", tracks = tracks))
        }
    }

    private fun parseHitmozSearchPayload(doc: Document, baseUrl: String): OnlineSearchPayload? {
        val root = doc.selectFirst(".content-inner .p-info") ?: doc.selectFirst(".p-info") ?: return null
        val albums = parseHitmozAlbumsBlock(root, baseUrl)
        val songs = parseHitmozTracksBlock(root, baseUrl)
        return if (albums.isEmpty() && songs.isEmpty()) null else OnlineSearchPayload(
            songs = dedupeSongs(songs),
            albums = dedupeAlbums(albums)
        )
    }

    private fun parseHitmozAlbumsBlock(root: Element, baseUrl: String): List<OnlineAlbumSummary> {
        val heading = root.select("h2.p-info-title").firstOrNull { heading ->
            heading.text().contains("альбом", ignoreCase = true)
        } ?: return emptyList()
        val list = heading.nextElementSibling()?.takeIf { it.`is`("ul.album-list") } ?: return emptyList()
        return list.select("li.album-item")
            .mapNotNull { item ->
                val link = item.selectFirst("a[href*=/album/]") ?: return@mapNotNull null
                val albumUrl = resolveUrl(link.absUrl("href").ifBlank { link.attr("href") }, baseUrl) ?: return@mapNotNull null
                val rawTitle = collapseWhitespace(
                    firstNonBlank(
                        link.selectFirst(".album-title")?.text(),
                        item.selectFirst(".sidebar-album-title")?.text(),
                        link.text()
                    )
                )
                if (rawTitle.isBlank()) return@mapNotNull null
                val badge = Regex("\\((\\d{4})\\)\\s*$").find(rawTitle)?.groupValues?.getOrNull(1).orEmpty()
                val title = cleanTitle(rawTitle.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), ""))
                val artist = cleanArtist(
                    collapseWhitespace(
                        firstNonBlank(
                            item.selectFirst(".album-singer, .sidebar-album-singer")?.text(),
                            link.selectFirst(".album-artist, .artist")?.text()
                        )
                    )
                )
                val coverUrl = firstNonBlank(
                    extractBackgroundImageUrl(
                        item.selectFirst(".album-image, .sidebar-album-image")?.attr("style").orEmpty(),
                        baseUrl
                    ),
                    extractImageUrl(item, baseUrl)
                )
                OnlineAlbumSummary(
                    title = title.ifBlank { "Альбом" },
                    artist = artist,
                    albumUrl = albumUrl,
                    coverUrl = coverUrl,
                    subtitle = if (artist.isBlank()) "Открыть список треков" else "",
                    badge = badge
                )
            }
    }

    private fun parseHitmozTracksBlock(root: Element, baseUrl: String): List<OnlineSong> {
        val heading = root.select("h2.p-info-title").firstOrNull { heading ->
            heading.text().contains("трек", ignoreCase = true)
        } ?: return emptyList()
        val tracksList = generateSequence(heading.nextElementSibling()) { it.nextElementSibling() }
            .firstOrNull { it.`is`("ul.tracks__list") }
            ?: return emptyList()
        return parseHitmozTrackList(tracksList, baseUrl)
    }

    private fun parseHitmozAlbumDetail(doc: Document, albumUrl: String, baseUrl: String): OnlineAlbumDetail? {
        val info = doc.selectFirst(".p-info.p-inner") ?: return null
        val heading = info.selectFirst("h1.p-info-title")?.text()?.trim().orEmpty()
        if (heading.isBlank()) return null

        val badge = Regex("\\((\\d{4})\\)\\s*$").find(heading)?.groupValues?.getOrNull(1).orEmpty()
        val title = cleanTitle(heading.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), ""))
        val artist = cleanArtist(info.selectFirst(".album--info a[href*=/artist/]")?.text().orEmpty())
        val description = collapseWhitespace(info.selectFirst(".album--info")?.text().orEmpty())
            .removePrefix("Исполнитель:")
            .trim()
        val coverUrl = firstNonBlank(
            extractBackgroundImageUrl(info.selectFirst(".p-info--image")?.attr("style").orEmpty(), baseUrl),
            doc.selectFirst("meta[property=og:image]")?.attr("content")
        )
        val chips = buildList {
            if (badge.isNotBlank()) add(badge)
            addAll(
                info.select(".album--info a")
                    .mapNotNull { it.text().trim().takeIf { text -> text.isNotBlank() } }
            )
        }.distinct().take(6)
        val tracks = info.selectFirst("ul.tracks__list")
            ?.let { parseHitmozTrackList(it, baseUrl, fallbackCoverUrl = coverUrl) }
            .orEmpty()

        return OnlineAlbumDetail(
            albumUrl = albumUrl,
            title = title.ifBlank { "Альбом" },
            artist = artist,
            coverUrl = coverUrl,
            description = description,
            chips = chips,
            sections = if (tracks.isEmpty()) emptyList() else listOf(
                OnlineAlbumSection(
                    title = "Треки",
                    tracks = tracks
                )
            )
        )
    }

    private fun parseHitmozTrackList(
        list: Element,
        baseUrl: String,
        fallbackCoverUrl: String = ""
    ): List<OnlineSong> {
        return dedupeSongs(
            list.select("li.tracks__item.track, li.track.mustoggler")
                .mapNotNull { item ->
                    val meta = parseTrackMeta(item)
                    val mp3Url = resolveUrl(
                        firstNonBlank(
                            meta?.optString("url"),
                            item.selectFirst("a.track__download-btn, a[href*=get/music], a[href*=\\.mp3]")?.attr("href")
                        ),
                        baseUrl
                    ) ?: return@mapNotNull null
                    val title = cleanTitle(
                        firstNonBlank(
                            meta?.optString("title"),
                            item.selectFirst(".track__title, .track-title, .title")?.text(),
                            filenameFromUrl(mp3Url)
                        )
                    )
                    if (title.isBlank()) return@mapNotNull null
                    val artist = cleanArtist(
                        firstNonBlank(
                            meta?.optString("artist"),
                            item.selectFirst(".track__desc, .artist, .track__artist")?.text()
                        )
                    )
                    val duration = item.selectFirst(".track__fulltime, .track__time, .duration")?.text()?.trim().orEmpty()
                    val coverUrl = firstNonBlank(
                        meta?.optString("img"),
                        extractTrackCoverUrl(item, baseUrl),
                        fallbackCoverUrl
                    )
                    OnlineSong(
                        title = title,
                        artist = artist,
                        duration = duration,
                        downloadUrl = mp3Url,
                        coverUrl = resolveUrl(coverUrl, baseUrl).orEmpty()
                    )
                }
        )
    }

    private fun extractMp3Url(el: Element, baseUrl: String): String? {
        val attrCandidates = listOf(
            el.attr("data-mp3"),
            el.attr("data-url"),
            el.attr("data-audio"),
            el.attr("data-src"),
            el.attr("data-download")
        ).firstOrNull { it.isNotBlank() }

        val directLink = el.selectFirst(
            "a[href~=\\.mp3], a.download, a[download], a[href*=/get/], a[href*=/download/]"
        )
        val raw = attrCandidates
            ?: directLink?.absUrl("href")?.takeIf { it.isNotBlank() }
            ?: directLink?.attr("href")
            ?: return null

        return resolveUrl(raw, baseUrl)
    }

    private fun extractSongFromElement(el: Element, mp3Url: String, baseUrl: String): OnlineSong? {
        val trackMeta = parseTrackMeta(el)
        val titleEl = el.selectFirst(
            ".track__title, .song-title, .title, .track-title, .name, b, strong, h3, h4, span.song, [itemprop=name]"
        )
        val artistEl = el.selectFirst(
            ".track__desc, .song-artist, .artist, .singer, .author, span.grey, span.artist, [itemprop=byArtist]"
        )
        val durEl = el.selectFirst(".track__fulltime, .song-time, .time, .duration, span.time, .dur")
        val sizeEl = el.selectFirst(".size, .file-size, .filesize")

        val rawTitle = deTranslitIfNeeded(firstNonBlank(trackMeta?.optString("title"), titleEl?.text()))
        val rawArtist = deTranslitIfNeeded(firstNonBlank(trackMeta?.optString("artist"), artistEl?.text()))
        val duration = durEl?.text()?.trim().orEmpty()
        val sizeLabel = sizeEl?.text()?.trim().orEmpty()
        val coverUrl = firstNonBlank(
            trackMeta?.optString("img"),
            extractTrackCoverUrl(el, baseUrl)
        )

        return when {
            rawTitle.isNotBlank() && rawArtist.isNotBlank() -> {
                val title = cleanTitle(rawTitle)
                val artist = cleanArtist(rawArtist)
                if (title.isBlank()) null else OnlineSong(title, artist, duration, mp3Url, sizeLabel, coverUrl)
            }

            rawTitle.isNotBlank() -> {
                val (artist, title) = splitAndClean(rawTitle)
                if (title.isBlank()) null else OnlineSong(title, artist, duration, mp3Url, sizeLabel, coverUrl)
            }

            else -> {
                val (artist, title) = splitAndClean(deTranslitIfNeeded(filenameFromUrl(mp3Url)))
                if (title.isBlank()) null else OnlineSong(title, artist, duration, mp3Url, sizeLabel, coverUrl)
            }
        }
    }

    private fun findAlbumCard(link: Element): Element {
        var current: Element? = link
        repeat(5) {
            if (current == null) return@repeat
            val el = current ?: return@repeat
            val hasImage = el.selectFirst("img, picture, source, .cover, .poster") != null
            val hasMeta = el.selectFirst(".artist, .subtitle, .meta, .badge, .tag") != null
            if (hasImage || hasMeta || el.tagName() in setOf("article", "li")) return el
            current = el.parent()
        }
        return link.parent() ?: link
    }

    private fun extractAlbumMeta(card: Element, title: String, artist: String): String {
        return card.select(
            ".subtitle, .meta, .description, .tracks, small, .info"
        )
            .mapNotNull { it.text().trim().takeIf { text -> text.isNotBlank() } }
            .firstOrNull { text ->
                text != title && text != artist && !text.contains(title, ignoreCase = true)
            }
            .orEmpty()
    }

    private fun extractAlbumBadge(card: Element): String {
        return card.select(
            ".badge, .tag, .year, .count, .tracks-count"
        )
            .mapNotNull { it.text().trim().takeIf { text -> text.isNotBlank() } }
            .firstOrNull()
            .orEmpty()
    }

    private fun extractImageUrl(card: Element, baseUrl: String): String {
        val raw = listOfNotNull(
            card.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotBlank() },
            card.selectFirst("img")?.attr("data-src")?.takeIf { it.isNotBlank() },
            card.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() },
            card.selectFirst("source")?.attr("srcset")?.substringBefore(" ")?.takeIf { it.isNotBlank() },
            extractBackgroundImageUrl(card.attr("style"), baseUrl).takeIf { it.isNotBlank() },
            extractBackgroundImageUrl(
                card.selectFirst(".album-image, .sidebar-album-image, .track__img, .cover, .poster")?.attr("style").orEmpty(),
                baseUrl
            ).takeIf { it.isNotBlank() }
        ).firstOrNull().orEmpty()

        return resolveUrl(raw, baseUrl).orEmpty()
    }

    private fun extractTrackCoverUrl(card: Element, baseUrl: String): String {
        return firstNonBlank(
            extractBackgroundImageUrl(card.selectFirst(".track__img, .album-image, .cover, .poster")?.attr("style").orEmpty(), baseUrl),
            extractImageUrl(card, baseUrl)
        )
    }

    private fun parseTrackMeta(el: Element): JSONObject? {
        val raw = el.attr("data-musmeta").trim().takeIf { it.startsWith("{") } ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun extractBackgroundImageUrl(style: String, baseUrl: String): String {
        val raw = Regex("url\\((['\"]?)(.*?)\\1\\)").find(style)?.groupValues?.getOrNull(2).orEmpty()
        return resolveUrl(raw, baseUrl).orEmpty()
    }

    private fun looksLikeAlbumUrl(url: String): Boolean {
        val normalized = url.lowercase()
        return "/album/" in normalized || "album?id" in normalized
    }

    private fun candidateUrls(target: String): List<CandidateUrl> {
        if (target.startsWith("http://") || target.startsWith("https://")) {
            val uri = runCatching { URI(target) }.getOrNull()
            if (uri == null) return BASE_URLS.map { base -> CandidateUrl(base, target) }
            val pathWithQuery = buildString {
                append(uri.rawPath ?: "")
                if (!uri.rawQuery.isNullOrBlank()) append('?').append(uri.rawQuery)
            }.ifBlank { "/" }
            val originalBase = target.toBaseUrl()
            return listOf(CandidateUrl(originalBase, target)) + BASE_URLS
                .distinct()
                .filterNot { it == originalBase }
                .map { base -> CandidateUrl(base, "$base$pathWithQuery") }
        }

        val normalizedPath = if (target.startsWith("/")) target else "/$target"
        return BASE_URLS.map { base -> CandidateUrl(base, "$base$normalizedPath") }
    }

    private data class CandidateUrl(
        val baseUrl: String,
        val url: String
    )

    private fun firstNonBlank(vararg values: String?): String {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
    }

    private fun collapseWhitespace(value: String): String {
        return value.replace(Regex("\\s+"), " ").trim()
    }

    private fun dedupeSongs(songs: List<OnlineSong>): List<OnlineSong> {
        val seen = mutableSetOf<String>()
        return songs.filter { it.downloadUrl.isNotBlank() && seen.add(it.downloadUrl) }
    }

    private fun dedupeAlbums(albums: List<OnlineAlbumSummary>): List<OnlineAlbumSummary> {
        val seen = mutableSetOf<String>()
        return albums.filter { it.albumUrl.isNotBlank() && seen.add(it.albumUrl) }
    }

    private fun String.toBaseUrl(): String {
        val uri = URI(this)
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        return "${uri.scheme}://${uri.host}$port"
    }

    private fun cleanTitle(raw: String): String {
        var s = raw.trim()
        s = s.replace(Regex("\\s+\\d{6,}\\s*$"), "").trim()
        s = removeRepeatedWords(s)
        s = s.trim('-', ' ', '_', '.')
        return s
    }

    private fun cleanArtist(raw: String): String {
        var s = raw.trim()
        s = s.replace(Regex("\\s+\\d{6,}\\s*$"), "").trim()
        s = removeRepeatedWords(s)
        s = s.trim('-', ' ', '_', '.')
        return s
    }

    private fun removeRepeatedWords(s: String): String {
        val words = s.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size < 2) return s

        val half = words.size / 2
        if (words.size % 2 == 0 && half >= 1) {
            val first = words.subList(0, half)
            val second = words.subList(half, words.size)
            if (first.map { it.uppercase() } == second.map { it.uppercase() }) {
                return first.joinToString(" ")
            }
        }

        val result = mutableListOf<String>()
        for (word in words) {
            if (result.isEmpty() || result.last().uppercase() != word.uppercase()) {
                result.add(word)
            }
        }
        return result.joinToString(" ")
    }

    private fun splitAndClean(raw: String): Pair<String, String> {
        val s = raw.trim()
        val dashIdx = s.indexOf(" - ").takeIf { it > 0 }
            ?: s.indexOf(" \u2013 ").takeIf { it > 0 }
            ?: s.indexOf(" \u2014 ").takeIf { it > 0 }

        return if (dashIdx != null) {
            val artist = cleanArtist(s.substring(0, dashIdx))
            val title = cleanTitle(s.substring(dashIdx + 3))
            artist to title
        } else {
            "" to cleanTitle(s)
        }
    }

    private fun filenameFromUrl(url: String): String {
        return url
            .substringAfterLast("/")
            .substringBefore("?")
            .removeSuffix(".mp3")
            .replace("_", " ")
            .replace("+", " ")
            .replace("%20", " ")
            .trim()
    }

    private fun resolveUrl(raw: String, baseUrl: String): String? {
        if (raw.isBlank()) return null
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> "$baseUrl$raw"
            else -> "$baseUrl/$raw"
        }
    }

    private fun deTranslitIfNeeded(text: String): String {
        if (text.isBlank()) return text
        val hasCyrillic = text.any { it in '\u0400'..'\u04FF' }
        if (hasCyrillic) return text
        val hasLatin = text.any { it.isLetter() && it.code < 128 }
        if (!hasLatin) return text
        return text.split(" ").joinToString(" ") { word ->
            if (word.isBlank()) word else deTranslitWord(word)
        }
    }

    private fun deTranslitWord(word: String): String {
        val isAllCaps = word == word.uppercase() && word.any { it.isLetter() }
        val isCapFirst = word.isNotEmpty() && word[0].isUpperCase() && !isAllCaps

        var s = word.lowercase()

        s = s
            .replace("shch", "\u0449")
            .replace("sch", "\u0449")
            .replace("sh", "\u0448")
            .replace("ch", "\u0447")
            .replace("zh", "\u0436")
            .replace("kh", "\u0445")
            .replace("ts", "\u0446")
            .replace("jj", "\u0439")
            .replace("jo", "\u0451")
            .replace("ju", "\u044E")
            .replace("ja", "\u044F")
            .replace("je", "\u044D")
            .replace("yo", "\u0451")
            .replace("yu", "\u044E")
            .replace("ya", "\u044F")
            .replace("ye", "\u0435")
            .replace("yj", "\u0439")

        s = s
            .replace("a", "\u0430")
            .replace("b", "\u0431")
            .replace("v", "\u0432")
            .replace("g", "\u0433")
            .replace("d", "\u0434")
            .replace("e", "\u0435")
            .replace("z", "\u0437")
            .replace("i", "\u0438")
            .replace("j", "\u0439")
            .replace("k", "\u043A")
            .replace("l", "\u043B")
            .replace("m", "\u043C")
            .replace("n", "\u043D")
            .replace("o", "\u043E")
            .replace("p", "\u043F")
            .replace("r", "\u0440")
            .replace("s", "\u0441")
            .replace("t", "\u0442")
            .replace("u", "\u0443")
            .replace("f", "\u0444")
            .replace("x", "\u043A\u0441")
            .replace("y", "\u044B")
            .replace("w", "\u0432")
            .replace("'", "\u044C")

        return when {
            isAllCaps -> s.uppercase()
            isCapFirst -> s.replaceFirstChar { it.uppercase() }
            else -> s
        }
    }
}
