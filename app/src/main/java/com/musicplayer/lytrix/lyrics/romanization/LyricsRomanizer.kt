package com.musicplayer.lytrix.lyrics.romanization

import android.os.Build

/**
 * Offline lyrics romanization supporting Hangul, Kana, Han/Kanji (via Android ICU API 29+),
 * Indic scripts (Devanagari/Hindi, Gurmukhi/Punjabi, Bengali), Cyrillic, and Arabic.
 */
object LyricsRomanizer {

    private enum class Script {
        LATIN,
        HANGUL,
        JAPANESE_KANA,
        HAN,
        DEVANAGARI,
        BENGALI,
        GURMUKHI,
        CYRILLIC,
        ARABIC
    }

    fun needsRomanization(text: String): Boolean {
        if (text.isBlank()) return false
        return text.any { ch ->
            isHangulSyllable(ch) ||
                ch.isKana() ||
                ch.isHan() ||
                ch.isDevanagari() ||
                ch.isBengali() ||
                ch.isGurmukhi() ||
                ch.isCyrillic() ||
                ch.isArabic()
        }
    }

    fun romanizeLines(lines: List<String>): List<String> {
        return lines.map { line -> romanizeLine(line) ?: "" }
    }

    fun romanizeLine(line: String): String? {
        if (line.isBlank()) return null

        val romanized = when (detectScript(line)) {
            Script.LATIN -> null
            Script.HANGUL -> HangulRomanizer.romanize(line)
            Script.JAPANESE_KANA -> {
                val kanaFixed = KanaRomanizer.romanize(line)
                if (kanaFixed.any { it.isHan() }) icuTransliterate(kanaFixed, "Any-Latin; Latin-ASCII") ?: kanaFixed
                else kanaFixed
            }
            Script.HAN -> icuTransliterate(line, "Han-Latin")
                ?: icuTransliterate(line, "Any-Latin; Latin-ASCII")
            Script.DEVANAGARI -> IndicRomanizer.romanizeDevanagari(line)
            Script.BENGALI -> IndicRomanizer.romanizeBengali(line)
            Script.GURMUKHI -> IndicRomanizer.romanizeGurmukhi(line)
            Script.CYRILLIC -> CyrillicRomanizer.romanize(line)
            Script.ARABIC -> ArabicRomanizer.romanize(line)
        } ?: return null

        val cleaned = romanized.replace(Regex(" {2,}"), " ").trim()
        return cleaned.takeIf { it.isNotBlank() && it != line.trim() }
    }

    private fun icuTransliterate(text: String, id: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val transliterator = android.icu.text.Transliterator.getInstance(id)
            transliterator.transliterate(text)
        }.getOrNull()
    }

    private fun detectScript(line: String): Script {
        var hasHan = false
        var hasKana = false
        for (ch in line) {
            when {
                isHangulSyllable(ch) -> return Script.HANGUL
                ch.isKana() -> hasKana = true
                ch.isHan() -> hasHan = true
                ch.isBengali() -> return Script.BENGALI
                ch.isDevanagari() -> return Script.DEVANAGARI
                ch.isGurmukhi() -> return Script.GURMUKHI
                ch.isCyrillic() -> return Script.CYRILLIC
                ch.isArabic() -> return Script.ARABIC
            }
        }
        return when {
            hasKana -> Script.JAPANESE_KANA
            hasHan -> Script.HAN
            else -> Script.LATIN
        }
    }

    private fun isHangulSyllable(c: Char): Boolean = c.code in 0xAC00..0xD7A3
    private fun Char.isKana(): Boolean = code in 0x3040..0x309F || code in 0x30A0..0x30FF
    private fun Char.isHan(): Boolean = code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF
    private fun Char.isDevanagari(): Boolean = code in 0x0900..0x097F
    private fun Char.isBengali(): Boolean = code in 0x0980..0x09FF
    private fun Char.isGurmukhi(): Boolean = code in 0x0A00..0x0A7F
    private fun Char.isCyrillic(): Boolean = code in 0x0400..0x052F
    private fun Char.isArabic(): Boolean = code in 0x0600..0x06FF
}

private object HangulRomanizer {
    private const val SYLLABLE_BASE = 0xAC00
    private const val MEDIAL_COUNT = 21
    private const val FINAL_COUNT = 28
    private const val SILENT_INITIAL = 11

    private val INITIALS = listOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp",
        "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
    )
    private val MEDIALS = listOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o",
        "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu",
        "eu", "ui", "i"
    )
    private val FINALS = listOf(
        "", "k", "k", "k", "n", "n", "n", "t", "l", "k", "m",
        "l", "l", "l", "p", "l", "m", "p", "p", "t", "t",
        "ng", "t", "t", "k", "t", "p", "t"
    )
    private val FINALS_AS_INITIAL = listOf(
        "", "g", "kk", "ks", "n", "nj", "nh", "d", "l", "lg", "lm",
        "lb", "ls", "lt", "lp", "lh", "m", "b", "bs", "s", "ss",
        "ng", "j", "ch", "k", "t", "p", "h"
    )
    private val ASSIMILATIONS: Map<Pair<Int, Int>, String> = buildMap {
        for (final in listOf(1, 2, 24)) {
            put(final to 2, "ng")
            put(final to 6, "ng")
        }
        for (final in listOf(17, 26)) {
            put(final to 2, "m")
            put(final to 6, "m")
        }
        for (final in listOf(7, 19, 20, 22, 23, 25, 27)) {
            put(final to 2, "n")
            put(final to 6, "n")
        }
        put(4 to 5, "l")
        put(8 to 2, "l")
    }

    private data class Jamo(val initial: Int, val medial: Int, val final: Int)

    private fun decompose(char: Char): Jamo? {
        val offset = char.code - SYLLABLE_BASE
        if (offset < 0 || offset >= MEDIAL_COUNT * FINAL_COUNT * INITIALS.size) return null
        return Jamo(
            initial = offset / (MEDIAL_COUNT * FINAL_COUNT),
            medial = (offset % (MEDIAL_COUNT * FINAL_COUNT)) / FINAL_COUNT,
            final = offset % FINAL_COUNT
        )
    }

    fun romanize(line: String): String {
        val sb = StringBuilder()
        for (i in line.indices) {
            val jamo = decompose(line[i])
            if (jamo == null) {
                sb.append(line[i])
                continue
            }
            val next = line.getOrNull(i + 1)?.let(::decompose)
            sb.append(INITIALS[jamo.initial])
            sb.append(MEDIALS[jamo.medial])
            val finalStr = when {
                jamo.final == 0 -> ""
                next == null -> FINALS[jamo.final]
                next.initial == SILENT_INITIAL -> FINALS_AS_INITIAL[jamo.final]
                else -> ASSIMILATIONS[jamo.final to next.initial] ?: FINALS[jamo.final]
            }
            sb.append(finalStr)
        }
        return sb.toString()
    }
}

private object KanaRomanizer {
    private val YOUON = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo",
        "うぃ" to "wi", "うぇ" to "we", "てぃ" to "ti", "でぃ" to "di",
        "とぅ" to "tu", "どぅ" to "du", "しぇ" to "she", "ちぇ" to "che", "じぇ" to "je"
    )
    private val KANA = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "o", 'ん' to "n",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o",
        'ゃ' to "ya", 'ゅ' to "yu", 'ょ' to "yo", 'ゔ' to "vu"
    )

    fun romanize(text: String): String {
        val kana = buildString {
            text.forEach { ch ->
                if (ch in 'ァ'..'ヶ') append((ch.code - 0x60).toChar()) else append(ch)
            }
        }
        val sb = StringBuilder()
        var idx = 0
        while (idx < kana.length) {
            val ch = kana[idx]
            if (ch == 'っ') {
                val nextPair = if (idx + 2 < kana.length) kana.substring(idx + 1, idx + 3) else null
                val next = nextPair?.let { YOUON[it] } ?: kana.getOrNull(idx + 1)?.let { KANA[it] }
                if (!next.isNullOrEmpty() && next[0] !in "aiueo") sb.append(next[0])
                idx++
                continue
            }
            if (ch == 'ー') {
                sb.lastOrNull()?.let { if (it in "aiueo") sb.append(it) }
                idx++
                continue
            }
            val pair = if (idx + 1 < kana.length) kana.substring(idx, idx + 2) else null
            val youon = pair?.let { YOUON[it] }
            if (youon != null) {
                sb.append(youon)
                idx += 2
                continue
            }
            sb.append(KANA[ch] ?: ch)
            idx++
        }
        return sb.toString()
    }
}

private object IndicRomanizer {
    private val DEVANAGARI_CONSONANTS = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
        'ष' to "sh", 'स' to "s", 'ह' to "h", 'ळ' to "l"
    )
    private val DEVANAGARI_NUKTA_FORMS = mapOf(
        'क' to "q", 'ख' to "kh", 'ग' to "gh", 'ज' to "z", 'ड' to "r", 'ढ' to "rh", 'फ' to "f"
    )
    private val DEVANAGARI_VOWELS = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u",
        'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au"
    )
    private val DEVANAGARI_MATRAS = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo",
        'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au"
    )
    private val DEVANAGARI_SIGNS = mapOf('ं' to "n", 'ँ' to "n", 'ः' to "h")

    private val BENGALI_CONSONANTS = mapOf(
        'ক' to "k", 'খ' to "kh", 'গ' to "g", 'ঘ' to "gh", 'ঙ' to "ng",
        'চ' to "ch", 'ছ' to "chh", 'জ' to "j", 'ঝ' to "jh", 'ঞ' to "n",
        'ট' to "t", 'ঠ' to "th", 'ড' to "d", 'ঢ' to "dh", 'ণ' to "n",
        'ত' to "t", 'থ' to "th", 'দ' to "d", 'ধ' to "dh", 'ন' to "n",
        'প' to "p", 'ফ' to "ph", 'ব' to "b", 'ভ' to "bh", 'ম' to "m",
        'য' to "j", 'র' to "r", 'ল' to "l", 'শ' to "sh", 'ষ' to "sh",
        'স' to "s", 'হ' to "h", 'ড়' to "r", 'ঢ়' to "rh", 'য়' to "y", 'ৎ' to "t"
    )
    private val BENGALI_VOWELS = mapOf(
        'অ' to "o", 'আ' to "a", 'ই' to "i", 'ঈ' to "ee", 'উ' to "u",
        'ঊ' to "oo", 'ঋ' to "ri", 'এ' to "e", 'ঐ' to "oi", 'ও' to "o", 'ঔ' to "ou"
    )
    private val BENGALI_MATRAS = mapOf(
        'া' to "a", 'ি' to "i", 'ী' to "ee", 'ু' to "u", 'ূ' to "oo",
        'ৃ' to "ri", 'ে' to "e", 'ৈ' to "oi", 'ো' to "o", 'ৌ' to "ou"
    )
    private val BENGALI_SIGNS = mapOf('ং' to "ng", 'ঁ' to "n", 'ঃ' to "h")

    private val GURMUKHI_CONSONANTS = mapOf(
        'ਕ' to "k", 'ਖ' to "kh", 'ਗ' to "g", 'ਘ' to "gh", 'ਙ' to "n",
        'ਚ' to "ch", 'ਛ' to "chh", 'ਜ' to "j", 'ਝ' to "jh", 'ਞ' to "n",
        'ਟ' to "t", 'ਠ' to "th", 'ਡ' to "d", 'ਢ' to "dh", 'ਣ' to "n",
        'ਤ' to "t", 'ਥ' to "th", 'ਦ' to "d", 'ਧ' to "dh", 'ਨ' to "n",
        'ਪ' to "p", 'ਫ' to "ph", 'ਬ' to "b", 'ਭ' to "bh", 'ਮ' to "m",
        'ਯ' to "y", 'ਰ' to "r", 'ਲ' to "l", 'ਵ' to "v", 'ਸ' to "s", 'ਹ' to "h", 'ੜ' to "r"
    )
    private val GURMUKHI_NUKTA_FORMS = mapOf(
        'ਸ' to "sh", 'ਲ' to "l", 'ਖ' to "kh", 'ਗ' to "gh", 'ਜ' to "z", 'ਫ' to "f"
    )
    private val GURMUKHI_VOWELS = mapOf(
        'ਅ' to "a", 'ਆ' to "aa", 'ਇ' to "i", 'ਈ' to "ee", 'ਉ' to "u",
        'ਊ' to "oo", 'ਏ' to "e", 'ਐ' to "ai", 'ਓ' to "o", 'ਔ' to "au"
    )
    private val GURMUKHI_MATRAS = mapOf(
        'ਾ' to "aa", 'ਿ' to "i", 'ੀ' to "ee", 'ੁ' to "u", 'ੂ' to "oo",
        'ੇ' to "e", 'ੈ' to "ai", 'ੋ' to "o", 'ੌ' to "au"
    )
    private val GURMUKHI_SIGNS = mapOf('ਂ' to "n", 'ੰ' to "n", 'ਃ' to "h")

    fun romanizeDevanagari(line: String): String =
        romanizeAbugida(line, DEVANAGARI_CONSONANTS, DEVANAGARI_VOWELS, DEVANAGARI_MATRAS, DEVANAGARI_SIGNS, '\u094D', '\u093C', DEVANAGARI_NUKTA_FORMS, "a")

    fun romanizeBengali(line: String): String =
        romanizeAbugida(line, BENGALI_CONSONANTS, BENGALI_VOWELS, BENGALI_MATRAS, BENGALI_SIGNS, '\u09CD', '\u09BC', mapOf('ড' to "r", 'ঢ' to "rh", 'য' to "y"), "o")

    fun romanizeGurmukhi(line: String): String =
        romanizeAbugida(line, GURMUKHI_CONSONANTS, GURMUKHI_VOWELS, GURMUKHI_MATRAS, GURMUKHI_SIGNS, '\u0A4D', '\u0A3C', GURMUKHI_NUKTA_FORMS, "a")

    private fun romanizeAbugida(
        line: String,
        consonants: Map<Char, String>,
        vowels: Map<Char, String>,
        matras: Map<Char, String>,
        signs: Map<Char, String>,
        virama: Char,
        nukta: Char,
        nuktaForms: Map<Char, String>,
        inherentVowel: String
    ): String {
        val sb = StringBuilder()
        var idx = 0
        while (idx < line.length) {
            val ch = line[idx]
            val consonant = consonants[ch]
            if (consonant != null) {
                var cursor = idx
                if (line.getOrNull(cursor + 1) == nukta) {
                    sb.append(nuktaForms[ch] ?: consonant)
                    cursor++
                } else {
                    sb.append(consonant)
                }
                idx = cursor
                val next = line.getOrNull(idx + 1)
                when {
                    next == virama -> idx++
                    next != null && matras.containsKey(next) -> {
                        sb.append(matras.getValue(next))
                        idx++
                    }
                    ch != 'ৎ' -> sb.append(inherentVowel)
                }
                idx++
                continue
            }
            sb.append(vowels[ch] ?: signs[ch] ?: ch)
            idx++
        }
        return sb.toString()
    }
}

private object CyrillicRomanizer {
    private val BASE = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y",
        'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh",
        'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e",
        'ю' to "yu", 'я' to "ya", 'ё' to "yo", 'і' to "i", 'ї' to "yi", 'є' to "ye", 'ґ' to "g"
    )
    private val WORD_INITIAL_FORMS = mapOf('е' to "ye", 'ё' to "ye", 'ю' to "yu", 'я' to "ya")
    private const val VOWELS = "аеёиоуыэюя"

    fun romanize(line: String): String {
        val sb = StringBuilder()
        line.forEachIndexed { idx, ch ->
            val low = ch.lowercaseChar()
            val mapped = if (low in WORD_INITIAL_FORMS) {
                val prev = line.getOrNull(idx - 1)?.lowercaseChar()
                val afterVowelOrSign = prev != null && (prev in VOWELS || prev == 'ь' || prev == 'ъ')
                val initial = prev == null || prev.code !in 0x0400..0x052F
                if (initial || afterVowelOrSign) WORD_INITIAL_FORMS.getValue(low) else BASE[low]
            } else {
                BASE[low]
            }
            if (mapped == null) sb.append(ch)
            else sb.append(if (ch.isUpperCase()) mapped.replaceFirstChar { it.uppercaseChar() } else mapped)
        }
        return sb.toString()
    }
}

private object ArabicRomanizer {
    private val MAP = mapOf(
        'ا' to "a", 'أ' to "a", 'إ' to "i", 'آ' to "aa", 'ب' to "b", 'ت' to "t",
        'ث' to "th", 'ج' to "j", 'ح' to "h", 'خ' to "kh", 'د' to "d", 'ذ' to "dh",
        'ر' to "r", 'ز' to "z", 'س' to "s", 'ش' to "sh", 'ص' to "s", 'ض' to "d",
        'ط' to "t", 'ظ' to "z", 'ع' to "'a", 'غ' to "gh", 'ف' to "f", 'ق' to "q",
        'ك' to "k", 'ل' to "l", 'م' to "m", 'ن' to "n", 'ه' to "h", 'ة' to "a",
        'و' to "w", 'ي' to "y", 'ى' to "a", 'ئ' to "i", 'ؤ' to "u", 'ء' to "'",
        '\u064E' to "a", '\u064F' to "u", '\u0650' to "i", '\u0652' to ""
    )

    fun romanize(line: String): String = buildString {
        line.forEach { ch -> append(MAP[ch] ?: ch) }
    }
}
