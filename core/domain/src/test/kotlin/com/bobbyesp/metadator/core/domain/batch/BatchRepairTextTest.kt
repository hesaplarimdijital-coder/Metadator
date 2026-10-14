/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.batch

import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.tags.api.TagMap
import com.bobbyesp.metadator.tags.api.TagSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A batch that puts garbled text right, each file's own. */
class BatchRepairTextTest {
    private val item = BatchItem(ContentRef("content://1"), "01 - Bir Sana Bir Bana.mp3")

    private fun file(vararg tags: Pair<String, List<String>>) =
        TagSnapshot(TagMap.of(*tags), emptyList())

    @Test
    fun `each file's garbled text is put right, and nothing else is written`() {
        val snapshot =
            file(
                "TITLE" to listOf("Bir Sana Bir Bana"),
                "ARTIST" to listOf("MoÄŸollar", "Cahit Berkay"),
                "ALBUMARTIST" to listOf("Moðollar"),
            )

        val changes = BatchEdit(repairText = true).changesFor(item, 0, 1, snapshot)

        assertEquals(
            mapOf(
                "ARTIST" to listOf("Moğollar", "Cahit Berkay"),
                "ALBUMARTIST" to listOf("Moğollar"),
            ),
            changes.fields,
        )
    }

    @Test
    fun `a field set for every song wins over its repair`() {
        val snapshot = file("ALBUM" to listOf("Ã‡iÃ§ek"), "ARTIST" to listOf("MoÄŸollar"))
        val edit = BatchEdit(fields = mapOf("ALBUM" to listOf("Çiçekler")), repairText = true)

        val changes = edit.changesFor(item, 0, 1, snapshot)

        assertEquals(listOf("Çiçekler"), changes.fields["ALBUM"])
        assertEquals(listOf("Moğollar"), changes.fields["ARTIST"])
    }

    @Test
    fun `a file with nothing garbled is left as it is`() {
        val changes =
            BatchEdit(repairText = true)
                .changesFor(item, 0, 1, file("ARTIST" to listOf("Moğollar")))

        assertTrue(changes.isEmpty)
    }

    @Test
    fun `a batch that only repairs is something to apply`() {
        assertFalse(BatchEdit(repairText = true).isEmpty)
        assertTrue(BatchEdit().isEmpty)
    }
}
