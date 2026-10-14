/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.text

import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackField
import com.bobbyesp.metadator.core.model.TrackId
import com.bobbyesp.metadator.tags.api.TagMap
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GarbledTextTest {

    /** [text]'s bytes in [written], decoded as [read]: what a reader with the wrong guess shows. */
    private fun misread(text: String, written: String, read: String): String =
        String(text.toByteArray(Charset.forName(written)), Charset.forName(read))

    private fun assertRepaired(expected: String, garbled: String) =
        assertEquals(garbled, Garbling.Repairable(expected), GarbledText.inspect(garbled))

    private fun assertRight(text: String) = assertNull(text, GarbledText.inspect(text))

    @Test
    fun `UTF-8 read as Windows-1252 is put right`() {
        assertRepaired("Moğollar", "MoÄŸollar")
        assertRepaired("ö", "Ã¶")
        assertRepaired("ı", "Ä±")
        assertRepaired("Ç", "Ã‡")
        assertRepaired("ş", "ÅŸ")
        assertRepaired("İ", "Ä°")
        assertRepaired("Ğ", "Äž")
        assertRepaired("Ş", "Åž")
        assertRepaired("Ö", "Ã–")
        assertRepaired("Ü", "Ãœ")
        assertRepaired("Barış Manço", "BarÄ±ÅŸ ManÃ§o")
        assertRepaired("Çiçek Dağı", "Ã‡iÃ§ek DaÄŸÄ±")
        assertRepaired("Beyoncé", "BeyoncÃ©")
        assertRepaired("Don’t Stop", "Donâ€™t Stop")
    }

    @Test
    fun `any text, read either way, is put right`() {
        for (text in
            listOf("Moğollar", "Gülümse", "İstanbul'da Sonbahar", "Ölürüm Sana", "Şımarık")) {
            assertRepaired(text, misread(text, "UTF-8", "windows-1252"))
            // TagLib reads a tag marked Latin-1 as Latin-1: bytes 128 to 159 become controls.
            assertRepaired(text, misread(text, "UTF-8", "ISO-8859-1"))
        }
    }

    @Test
    fun `text garbled twice over is put right`() {
        val twice = misread(misread("Moğollar", "UTF-8", "windows-1252"), "UTF-8", "windows-1252")
        assertEquals("MoÃ„Å¸ollar", twice)
        assertRepaired("Moğollar", twice)
    }

    @Test
    fun `other scripts garbled the same way are put right`() {
        assertRepaired("привет", misread("привет", "UTF-8", "windows-1252"))
        assertRepaired("陈奕迅", misread("陈奕迅", "UTF-8", "windows-1252"))
        assertRepaired("Ελλάδα", misread("Ελλάδα", "UTF-8", "windows-1252"))
    }

    @Test
    fun `Turkish in Windows-1254 read as Latin-1 is put right`() {
        assertRepaired("Moğollar", "Moðollar")
        assertRepaired("MOĞOLLAR", "MOÐOLLAR")
        assertRepaired("Barış Manço", "Barýþ Manço")
        assertRepaired("Aşk", "Aþk")
        assertRepaired("Şarkı", "Þarký")
        assertRepaired("İstanbul", "Ýstanbul")
        assertRepaired("Kırmızı", "Kýrmýzý")
        assertRepaired("Işık", "Iþýk")
        assertRepaired("Güneş", "Güneþ")
        assertRepaired("Çağdaş", "Çaðdaþ")
        assertRepaired("Yağmur", "Yaðmur")
        assertRepaired("Değil", "Deðil")
        assertRepaired("Doğum Günün Kutlu Olsun", "Doðum Günün Kutlu Olsun")
        assertRepaired("Resimdeki Gözyaşları", "Resimdeki Gözyaþlarý")
        assertRepaired("Müslüm Gürses - İtirazım Var", "Müslüm Gürses - Ýtirazým Var")
        assertRepaired("Şımarık", misread("Şımarık", "windows-1254", "ISO-8859-1"))
    }

    @Test
    fun `Icelandic and Faroese stay as they are`() {
        listOf(
                "Sigur Rós",
                "Ágætis byrjun",
                "Það",
                "Þetta",
                "Þú ert jörðin",
                "Dauðalogn",
                "Varðeldur",
                "Yfirborð",
                "Veður",
                "Veðurguðirnir",
                "Sigurður",
                "Mývatn",
                "Kýr",
                "Hjartað hamast",
                "Eyþór Ingi",
                "Með suð í eyrum",
                "Bláþráður",
                "Trøllabundin",
                "Svarthöfði",
            )
            .forEach(::assertRight)
    }

    @Test
    fun `text that is right is left alone`() {
        listOf(
                "Moğollar",
                "Barış Manço",
                "Çiçek Dağı",
                "İstanbul",
                "ÇİÇEK",
                "Beyoncé",
                "Tiësto",
                "Mötley Crüe",
                "Don’t Stop Me Now",
                "Señorita",
                "Œuvre",
                "Šostakovič",
                "Dobrý den",
                "Starý",
                "Straße“",
                "Fuß…",
                "«CANCIÓ»",
                "¡OLÉ…!",
                "¿Quién será?",
                "10°C",
                "Nº1",
                "Col·lecció",
                "GLAY×EXILE",
                "SÃO PAULO",
                "ONE OK ROCKの曲",
                "BLACKPINK뚜두뚜두",
                "周杰伦 Jay Chou",
                "Jay周杰伦",
                "IU의 팔레트",
                "ポケモンGO",
                "Ёлка",
                "AC/DC",
            )
            .forEach(::assertRight)
    }

    @Test
    fun `a wrong guess at another character set is garbled past repair`() {
        for (read in listOf("GBK", "Big5", "Shift_JIS", "EUC-KR", "windows-1251")) {
            for (text in
                listOf("Moğollar", "Barış Manço", "Sezen Aksu - Gülümse", "Müslüm Gürses")) {
                val garbled = misread(text, "windows-1254", read)
                assertEquals(
                    "$text read as $read: $garbled",
                    Garbling.Unrepairable,
                    inspect(garbled),
                )
            }
        }
    }

    @Test
    fun `characters that stand for nothing are garbled past repair`() {
        assertEquals(Garbling.Unrepairable, GarbledText.inspect("Kıra�"))
        assertEquals(Garbling.Unrepairable, GarbledText.inspect("Mo\u0081ollar"))
        // Chinese in GBK read as Latin-1: "ÖÜ½ÜÂ×".
        assertEquals(
            Garbling.Unrepairable,
            GarbledText.inspect(misread("周杰伦", "GBK", "ISO-8859-1")),
        )
    }

    @Test
    fun `garbling is ranked`() {
        assertTrue(
            GarbledText.inspect("Moğollar").severity < GarbledText.inspect("Moðollar").severity
        )
        assertTrue(
            GarbledText.inspect("Moðollar").severity <
                GarbledText.inspect(misread("Moğollar", "windows-1254", "GBK")).severity
        )
    }

    @Test
    fun `repairs are offered for every value of a field that needs one`() {
        val tags =
            TagMap.of(
                "TITLE" to listOf("Bir Sana Bir Bana"),
                "ARTIST" to listOf("MoÄŸollar", "Cahit Berkay"),
                "ALBUM" to listOf("Moðollar"),
                "COMMENT" to listOf("Mo\u0081ollar"),
            )

        assertEquals(
            mapOf("ARTIST" to listOf("Moğollar", "Cahit Berkay"), "ALBUM" to listOf("Moğollar")),
            tags.textRepairs(),
        )
        assertEquals(
            emptyMap<String, List<String>>(),
            TagMap.of("TITLE" to listOf("Aşk")).textRepairs(),
        )
    }

    @Test
    fun `a song says which of its fields look garbled`() {
        val song = track(title = "Bir Sana Bir Bana", artist = "MoÄŸollar", album = "1968 - 2000")
        assertEquals(setOf(TrackField.Artist), song.garbledFields)
        assertTrue(song.hasGarbledText)
        assertFalse(song.copy(artist = "Moğollar").hasGarbledText)
    }

    private fun inspect(text: String) = GarbledText.inspect(text)

    private fun track(title: String, artist: String?, album: String?) =
        Track(
            id = TrackId(1),
            ref = ContentRef("content://1"),
            title = title,
            artist = artist,
            album = album,
            albumArtist = null,
            albumId = 1,
            durationMs = 1000,
            trackNumber = 1,
            discNumber = null,
            year = null,
            genre = null,
            mimeType = "audio/mpeg",
            sizeBytes = 10,
            displayName = "1.mp3",
            folder = "Music/",
            dateAddedEpochSeconds = 1,
            dateModifiedEpochSeconds = 1,
            artworkRef = null,
        )
}
