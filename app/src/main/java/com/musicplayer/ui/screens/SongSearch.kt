package com.musicplayer.ui.screens

import com.musicplayer.data.Song

/**
 * Поиск по библиотеке.
 *
 * Что было не так:
 *  • искали по оригинальным тегам (song.title/artist), а в списке показываются ПЕРЕИМЕНОВАННЫЕ
 *    названия и исполнители — поэтому выдача не совпадала с тем, что видит пользователь;
 *  • совпадение "где угодно внутри слова" — запрос "ч" находил всё, где есть буква "ч" (и в альбоме тоже);
 *  • регистр/ё/знаки препинания влияли на результат, а порядок выдачи не зависел от релевантности.
 *
 * Как теперь:
 *  • ищем и по тому, что видно в списке, и по оригинальным тегам;
 *  • запрос режется на слова, каждое слово должно найтись (в любом порядке и в любом поле);
 *  • слова из 1–2 букв ищутся только по началу слова (и только в названии/исполнителе);
 *    с 3 букв — ещё и внутри слова, и в альбоме;
 *  • ё = е, знаки препинания и регистр не мешают;
 *  • выдача отсортирована по релевантности, при равенстве — в порядке текущей сортировки списка.
 */

/** Заранее нормализованные поля одной песни — считаются один раз, а не на каждый символ запроса. */
internal class SongSearchEntry(
    val song: Song,
    val title: String,      // то, что видно в списке
    val titleRaw: String,   // оригинальный тег (если не переименован — то же самое)
    val artist: String,
    val artistRaw: String,
    val album: String,
)

internal fun buildSongSearchIndex(
    songs: List<Song>,
    customTitles: Map<Long, String>,
    customArtists: Map<Long, String>,
): List<SongSearchEntry> = songs.map { song ->
    val shownTitle = customTitles[song.id] ?: song.title
    val shownArtist = customArtists[song.id] ?: song.artist
    SongSearchEntry(
        song = song,
        title = normalizeForSearch(shownTitle),
        titleRaw = normalizeForSearch(song.title),
        artist = normalizeForSearch(shownArtist.takeUnless { it == UNKNOWN_TAG }.orEmpty()),
        artistRaw = normalizeForSearch(song.artist.takeUnless { it == UNKNOWN_TAG }.orEmpty()),
        album = normalizeForSearch(song.album.takeUnless { it == UNKNOWN_TAG }.orEmpty()),
    )
}

/** Возвращает [songs] как есть при пустом запросе, иначе — найденные, лучшие первыми. */
internal fun searchSongs(
    songs: List<Song>,
    index: List<SongSearchEntry>,
    query: String,
): List<Song> {
    val q = normalizeForSearch(query)
    if (q.isEmpty()) return songs
    val tokens = q.split(' ')

    return index
        .mapNotNull { entry ->
            val score = scoreEntry(entry, q, tokens)
            if (score > 0) entry.song to score else null
        }
        // sortedByDescending стабилен: при равной релевантности сохраняется порядок списка.
        .sortedByDescending { it.second }
        .map { it.first }
}

private fun scoreEntry(e: SongSearchEntry, q: String, tokens: List<String>): Int {
    var total = 0
    for (token in tokens) {
        val longToken = token.length >= 3
        var best = 0
        best = maxOf(best, fieldScore(e.title, token, WORD_START_TITLE, INSIDE_TITLE))
        if (e.titleRaw != e.title) {
            best = maxOf(best, fieldScore(e.titleRaw, token, WORD_START_TITLE - 10, INSIDE_TITLE - 5))
        }
        best = maxOf(best, fieldScore(e.artist, token, WORD_START_ARTIST, INSIDE_ARTIST))
        if (e.artistRaw != e.artist) {
            best = maxOf(best, fieldScore(e.artistRaw, token, WORD_START_ARTIST - 10, INSIDE_ARTIST - 5))
        }
        // Альбом — только для нормальных слов: по одной-двум буквам он даёт один шум.
        if (longToken) {
            best = maxOf(best, fieldScore(e.album, token, WORD_START_ALBUM, INSIDE_ALBUM))
        }
        if (best == 0) return 0 // каждое слово запроса обязано найтись
        total += best
    }

    // Бонусы за совпадение запроса целиком.
    if (e.title == q) total += 300
    else if (e.title.startsWith(q)) total += 120
    else if (tokens.size > 1 && e.title.contains(q)) total += 60
    if (e.artist == q) total += 150
    else if (tokens.size > 1 && "${e.artist} ${e.title}".contains(q)) total += 80
    return total
}

/** Оценка одного слова запроса в одном поле. 0 — не найдено. */
private fun fieldScore(hay: String, token: String, wordStart: Int, inside: Int): Int = when {
    hay.isEmpty() -> 0
    hay.startsWith(token) -> wordStart + 20          // поле начинается с запроса
    hay.contains(" $token") -> wordStart              // какое-то слово начинается с запроса
    token.length >= 3 && hay.contains(token) -> inside // внутри слова — только для 3+ букв
    else -> 0
}

/** Нижний регистр, ё→е, всё кроме букв и цифр — пробел, пробелы схлопнуты. */
internal fun normalizeForSearch(text: String): String {
    val sb = StringBuilder(text.length)
    var lastWasSpace = true
    for (raw in text.lowercase()) {
        val ch = if (raw == 'ё') 'е' else raw
        if (ch.isLetterOrDigit()) {
            sb.append(ch)
            lastWasSpace = false
        } else if (!lastWasSpace) {
            sb.append(' ')
            lastWasSpace = true
        }
    }
    return sb.toString().trim()
}

private const val UNKNOWN_TAG = "<unknown>"

private const val WORD_START_TITLE = 100
private const val INSIDE_TITLE = 40
private const val WORD_START_ARTIST = 70
private const val INSIDE_ARTIST = 30
private const val WORD_START_ALBUM = 40
private const val INSIDE_ALBUM = 15
