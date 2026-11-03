package com.musicplayer.repository.search

import java.text.Normalizer
import java.util.Locale

/**
 * Text helpers shared by the online search pipeline.
 *
 * Every string is folded into a comparable form: lowercase, Cyrillic transliterated
 * to Latin, diacritics stripped and punctuation turned into spaces. That makes
 * "Мияги" match "Miyagi", "Ёлка" match "Elka" and "Numb (Live)" match "Numb".
 */
internal object SearchText {

    private val CYRILLIC: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e",
        'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k",
        'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    private val BRACKETED = Regex("""[\(\[\{][^\)\]\}]*[\)\]\}]""")
    private val FEATURING = Regex("""(?i)\b(feat|ft|featuring|prod)\b.*$""")
    private val NON_WORD = Regex("""[^\p{L}\p{Nd}]+""")
    private val SPACES = Regex("""\s+""")

    /** Folds [raw] into lowercase latin words separated by single spaces. */
    fun fold(raw: String): String {
        if (raw.isBlank()) return ""
        val lowered = raw.lowercase(Locale.ROOT).replace('ё', 'е')
        val latin = StringBuilder(lowered.length + 8)
        for (ch in lowered) {
            val mapped = CYRILLIC[ch]
            if (mapped == null) latin.append(ch) else latin.append(mapped)
        }
        val decomposed = Normalizer.normalize(latin.toString(), Normalizer.Form.NFD)
        val plain = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
            plain.append(ch)
        }
        return SPACES.replace(NON_WORD.replace(plain.toString(), " "), " ").trim()
    }

    fun tokens(raw: String): List<String> {
        val folded = fold(raw)
        if (folded.isEmpty()) return emptyList()
        return folded.split(' ').filter { it.isNotEmpty() }
    }

    /** Removes bracketed annotations and "feat." tails before comparing titles. */
    fun matchable(raw: String): String {
        if (raw.isBlank()) return ""
        val withoutBrackets = BRACKETED.replace(raw, " ")
        return FEATURING.replace(withoutBrackets, " ")
    }

    /** Tokens of a title/artist cleaned from brackets and featuring credits. */
    fun keyTokens(raw: String): List<String> = tokens(matchable(raw))

    /** Share of [needle] tokens present in [haystack]. */
    fun coverage(needle: Collection<String>, haystack: Collection<String>): Double {
        if (needle.isEmpty()) return 0.0
        val wanted = needle.toSet()
        if (wanted.isEmpty()) return 0.0
        val available = haystack.toSet()
        val hits = wanted.count { it in available }
        return hits.toDouble() / wanted.size
    }

    /** Sorensen-Dice coefficient over token sets. */
    fun dice(first: Collection<String>, second: Collection<String>): Double {
        val a = first.toSet()
        val b = second.toSet()
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val overlap = a.count { it in b }
        return 2.0 * overlap / (a.size + b.size)
    }

    /** Splits a credit line such as "Miyagi & Эндшпиль, Kadi" into single artists. */
    fun artistParts(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(',', '&', '/', ';')
            .map { part -> part.replace(Regex("""(?i)\b(feat|ft|featuring|x|vs)\.?\b"""), " ") }
            .map { part -> fold(part) }
            .filter { it.isNotEmpty() }
    }
}
