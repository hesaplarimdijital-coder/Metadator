/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.text

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** What is wrong with a text decoded with the wrong character set, as far as the text tells. */
sealed interface Garbling {
    /** The text says how it was garbled: [repaired] is what it was meant to say. */
    data class Repairable(val repaired: String) : Garbling

    /**
     * Garbled past what the text can undo: letters of another script inside Latin words, which is
     * what a wrong guess at a file's legacy character set gives, or characters that stand for
     * nothing. Only the file's own tag can say what it was.
     */
    data object Unrepairable : Garbling
}

/** How badly: 0 not at all, 1 repairable from the text itself, 2 past that. For comparing two. */
val Garbling?.severity: Int
    get() =
        when (this) {
            null -> 0
            is Garbling.Repairable -> 1
            Garbling.Unrepairable -> 2
        }

/**
 * Tells text decoded with the wrong character set from text that is right, and puts it right where
 * the text itself says how. Android's scanner decodes a tag marked Latin-1 with a guess at its real
 * character set, and old taggers wrote whatever bytes they had under that mark, so a library shows
 * both of the mistakes undone here:
 * - UTF-8 read as Windows-1252 or Latin-1: "MoÄŸollar" for "Moğollar", "Ã¶" for "ö", once or twice
 *   over;
 * - Turkish written in Windows-1254 read as Latin-1: "Moðollar" for "Moğollar". The two differ in
 *   six letters only, which Icelandic and Faroese use as they are, so this is undone only where the
 *   result reads as Turkish and nothing in it says otherwise.
 *
 * A wrong guess at an Asian character set (a Chinese character in the middle of a Turkish word) is
 * recognized, but cannot be undone from the text: the bytes it was made of are lost.
 *
 * Both repairs are conservative. One missed leaves the text as it was; a wrong one would break a
 * name that was right.
 */
object GarbledText {

    /** What is wrong with [text]; null when it looks right. */
    fun inspect(text: String): Garbling? {
        if (text.all { it < '\u0080' }) return null
        repaired(text)?.let {
            return Garbling.Repairable(it)
        }
        return if (isBroken(text)) Garbling.Unrepairable else null
    }

    /** [text] as it was meant, when it is garbled in a way the text itself can undo. */
    fun repair(text: String): String? = (inspect(text) as? Garbling.Repairable)?.repaired

    private fun repaired(text: String): String? {
        var current = text
        var passes = 0
        // Text written back garbled and read wrong again takes a pass for each time.
        while (passes < MAX_PASSES) {
            current = undoUtf8ReadAsSingleByte(current) ?: break
            passes++
        }
        val result = undoTurkishReadAsLatin1(current) ?: current
        return result.takeIf { it != text }
    }
}

private const val MAX_PASSES = 3

/*
 * UTF-8 read as Windows-1252 or Latin-1. Each character goes back to the byte it was decoded from,
 * and the bytes must then be UTF-8 without a fault: text that is right almost never is, since every
 * byte above 127 of UTF-8 comes in a sequence that ordinary Latin-1 text does not spell (an accented
 * capital followed by a symbol). What comes out must read as text too.
 */

private fun undoUtf8ReadAsSingleByte(text: String): String? {
    val bytes = ByteArray(text.length)
    var high = false
    for (index in text.indices) {
        val char = text[index]
        // Latin-1 gives every byte its own character, the controls 128 to 159 included.
        val byte = if (char.code < 0x100) char.code else Cp1252High[char] ?: return null
        if (byte >= 0x80) high = true
        bytes[index] = byte.toByte()
    }
    if (!high) return null
    val decoded =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            return null
        }
    return decoded.takeIf { it.readsAsText() }
}

/** The characters Windows-1252 gives the bytes 128 to 159, where Latin-1 has controls. */
private val Cp1252High: Map<Char, Int> =
    mapOf(
        '€' to 0x80,
        '‚' to 0x82,
        'ƒ' to 0x83,
        '„' to 0x84,
        '…' to 0x85,
        '†' to 0x86,
        '‡' to 0x87,
        'ˆ' to 0x88,
        '‰' to 0x89,
        'Š' to 0x8A,
        '‹' to 0x8B,
        'Œ' to 0x8C,
        'Ž' to 0x8E,
        '‘' to 0x91,
        '’' to 0x92,
        '“' to 0x93,
        '”' to 0x94,
        '•' to 0x95,
        '–' to 0x96,
        '—' to 0x97,
        '˜' to 0x98,
        '™' to 0x99,
        'š' to 0x9A,
        '›' to 0x9B,
        'œ' to 0x9C,
        'ž' to 0x9E,
        'Ÿ' to 0x9F,
    )

/**
 * Whether a repair gave text: no controls or characters that stand for nothing, Latin letters only
 * of the alphabets in use, and no word half Latin and half another script. A capital followed by a
 * symbol in text that was right ("Ó»", "ß“") decodes to a rare letter or a foreign one stuck to a
 * Latin word, and is left alone.
 */
private fun String.readsAsText(): Boolean {
    var previous = 0
    var index = 0
    while (index < length) {
        val point = codePointAt(index)
        if (point >= 0x80 && !isPlausible(point)) return false
        if (previous != 0 && mixesScripts(previous, point)) return false
        previous = point
        index += Character.charCount(point)
    }
    return true
}

private fun isPlausible(point: Int): Boolean {
    if (point == 0xFFFD) return false
    when (Character.getType(point).toByte()) {
        Character.CONTROL,
        Character.FORMAT,
        Character.SURROGATE,
        Character.PRIVATE_USE,
        Character.UNASSIGNED,
        Character.LINE_SEPARATOR,
        Character.PARAGRAPH_SEPARATOR -> return false
    }
    if (!Character.isLetter(point)) return true
    return Character.UnicodeScript.of(point) != Character.UnicodeScript.LATIN ||
        CommonLatin.any { point in it }
}

/**
 * The Latin letters beyond ASCII that languages are written with: Latin-1's, Latin Extended-A's
 * (Turkish, Polish, Czech…), Azerbaijani's schwa, Vietnamese's horned vowels, pinyin's tones,
 * Romanian's comma letters, and Vietnamese's stacked accents.
 */
private val CommonLatin =
    listOf(
        0x00AA..0x00AA,
        0x00BA..0x00BA,
        0x00C0..0x017F,
        0x018F..0x018F,
        0x01A0..0x01A1,
        0x01AF..0x01B0,
        0x01CD..0x01DC,
        0x0218..0x021B,
        0x0259..0x0259,
        0x1E00..0x1EFF,
    )

/** A Latin letter against a letter or a digit of another script, which no word has. */
private fun mixesScripts(first: Int, second: Int): Boolean {
    if (!Character.isLetterOrDigit(first) || !Character.isLetterOrDigit(second)) return false
    val a = Character.UnicodeScript.of(first)
    val b = Character.UnicodeScript.of(second)
    if (a in NeutralScripts || b in NeutralScripts) return false
    return (a == Character.UnicodeScript.LATIN) != (b == Character.UnicodeScript.LATIN)
}

private val NeutralScripts =
    setOf(
        Character.UnicodeScript.COMMON,
        Character.UnicodeScript.INHERITED,
        Character.UnicodeScript.UNKNOWN,
    )

/*
 * Turkish written in Windows-1254 and read as Latin-1, or as Windows-1252: the code pages differ in
 * six letters, ğ Ğ ş Ş ı İ, which come out as ð Ð þ Þ ý Ý. Icelandic and Faroese have those six
 * themselves, so the swap must give text that is Turkish by its letters and its shape:
 * - every letter is one Turkish is written with (Icelandic has á, é, í, ó, ú and æ nearly always
 *   somewhere);
 * - ğ follows exactly one vowel, as it always does in Turkish, where Icelandic's ð often follows a
 *   consonant or two vowels ("Varðeldur", "Dauðalogn");
 * - and something Turkish is there: ç, ü or a circumflex, a ğ inside a word that keeps Turkish vowel
 *   harmony, an ş before a consonant or ending a word, a word with ı and another of the six, or an İ
 *   starting one. A word with ğ that breaks vowel harmony counts against ("Veður").
 * Short words that both languages could spell ("Það", "Kýr") stay as they are.
 */

private val TurkishSwaps: Map<Char, Char> =
    mapOf(
        'ð' to 'ğ',
        'Ð' to 'Ğ',
        'þ' to 'ş',
        'Þ' to 'Ş',
        'ý' to 'ı',
        'Ý' to 'İ',
    )

/** The letters of Turkish beyond ASCII, circumflexed vowels included. */
private const val TurkishLetters = "çÇğĞıİöÖşŞüÜ" + "âÂîÎûÛ"

/** Turkish letters Icelandic and Faroese do not have: ç, ü and the circumflexed vowels. */
private const val TurkishOnlyLetters = "çÇüÜâÂîÎûÛ"

/** Read the Turkish way: I is the capital of ı, İ that of i. */
private const val BackVowels = "aAıIoOuUâÂûÛ"
private const val FrontVowels = "eEiİöÖüÜîÎ"

private fun Char.isTurkishVowel(): Boolean = this in BackVowels || this in FrontVowels

private fun undoTurkishReadAsLatin1(text: String): String? {
    if (text.none { it in TurkishSwaps }) return null
    val swapped = BooleanArray(text.length)
    val repaired =
        buildString(text.length) {
            text.forEachIndexed { index, char ->
                val turkish = TurkishSwaps[char]
                swapped[index] = turkish != null
                append(turkish ?: char)
            }
        }
    return repaired.takeIf { readsAsTurkish(it, swapped) }
}

private fun readsAsTurkish(text: String, swapped: BooleanArray): Boolean {
    val foreign = text.any {
        it.isLetter() && it !in 'a'..'z' && it !in 'A'..'Z' && it !in TurkishLetters
    }
    if (foreign) return false
    var evidence = if (text.any { it in TurkishOnlyLetters }) 2 else 0
    for (word in words(text)) {
        val swaps = word.filter { swapped[it] }
        if (swaps.isEmpty()) continue
        evidence += evidenceOf(text, word, swaps) ?: return false
    }
    return evidence >= 1
}

/** What one word says for Turkish, or null when it cannot be Turkish. */
private fun evidenceOf(text: String, word: IntRange, swaps: List<Int>): Int? {
    var evidence = 0
    var softG = false
    var softGInside = false
    for (index in swaps) {
        val char = text[index]
        val next = if (index < word.last) text[index + 1] else null
        when (char) {
            'ğ',
            'Ğ' -> {
                val oneVowelBefore =
                    index > word.first &&
                        text[index - 1].isTurkishVowel() &&
                        (index - 1 == word.first || !text[index - 2].isTurkishVowel())
                if (!oneVowelBefore) return null
                softG = true
                if (next != null) softGInside = true
            }
            'ş',
            'Ş' -> {
                // Icelandic's þ starts words, or comes before r or j; Turkish's ş ends them too.
                if (next == null || (!next.isTurkishVowel() && next.lowercaseChar() !in "rj")) {
                    evidence++
                }
            }
            'İ' -> {
                val upperCaseWord = word.all { !text[it].isLowerCase() }
                if (index != word.first && !upperCaseWord) return null
                if (
                    index == word.first &&
                        next != null &&
                        next.isLowerCase() &&
                        !next.isTurkishVowel() &&
                        word.count() >= 3
                ) {
                    evidence++
                }
            }
        }
    }
    if (softG) {
        if (!isHarmonic(text, word)) evidence -= 2 else if (softGInside) evidence++
    }
    if (swaps.size >= 2 && swaps.any { text[it] == 'ı' }) evidence++
    return evidence
}

/** Whether the vowels of a word are all back or all front ones, as Turkish words' are. */
private fun isHarmonic(text: String, word: IntRange): Boolean {
    val vowels = word.map { text[it] }.filter { it.isTurkishVowel() }
    return vowels.all { it in BackVowels } || vowels.all { it in FrontVowels }
}

/** The runs of letters of [text], as index ranges. */
private fun words(text: String): List<IntRange> = buildList {
    var start = -1
    for (index in text.indices) {
        if (text[index].isLetter()) {
            if (start < 0) start = index
        } else if (start >= 0) {
            add(start until index)
            start = -1
        }
    }
    if (start >= 0) add(start until text.length)
}

/*
 * Garbled past repair. A wrong guess at an Asian character set turns a Turkish letter and the byte
 * after it into one character: "Moğollar" comes out as "Mo", a Chinese character and "llar". So
 * characters of those scripts, or of another alphabet, against Latin letters in text that is mostly
 * Latin are taken for one: a single one inside a word, or a few among many Latin letters. A title
 * that mixes scripts on purpose has runs of its own script ("BLACKPINK뚜두뚜두", "Jay周杰伦"), and
 * kana is left out: a wrong guess does not give it, and Japanese titles tie it to Latin words.
 * Characters that stand for nothing (the replacement character, controls, private use) are always
 * garbled, and so are Latin-1's symbols inside a word, which is what text in an Asian character set
 * looks like read as Latin-1.
 */

private fun isBroken(text: String): Boolean {
    var latin = 0
    var foreign = 0
    var touching = false
    var alone = false
    for (index in text.indices) {
        val char = text[index]
        val before = text.getOrNull(index - 1)
        val after = text.getOrNull(index + 1)
        when {
            char == '�' || char in '\u0080'..'\u009F' || char in ''..'' -> return true
            char.isLatinLetter() -> latin++
            char.isWrongGuess() -> {
                foreign++
                if (before?.isLatinLetter() == true || after?.isLatinLetter() == true) {
                    touching = true
                    if (before?.isWrongGuess() != true && after?.isWrongGuess() != true) {
                        alone = true
                    }
                }
            }
            char in '¡'..'¿' && char != '·' ->
                if (before?.isLetter() == true && after?.isLetter() == true) return true
        }
    }
    return (alone && latin >= foreign * 2) || (touching && latin >= foreign * 3)
}

private fun Char.isLatinLetter(): Boolean =
    isLetter() && Character.UnicodeScript.of(code) == Character.UnicodeScript.LATIN

/** What a wrong guess at a character set gives in place of a Latin letter. */
private fun Char.isWrongGuess(): Boolean {
    if (!isLetter()) return false
    // Half-width katakana: Shift_JIS's single bytes, which no Japanese title is written in.
    if (this in '｡'..'ﾟ') return true
    return when (Character.UnicodeScript.of(code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HANGUL,
        Character.UnicodeScript.CYRILLIC,
        Character.UnicodeScript.GREEK,
        Character.UnicodeScript.ARABIC,
        Character.UnicodeScript.HEBREW,
        Character.UnicodeScript.THAI -> true
        else -> false
    }
}
