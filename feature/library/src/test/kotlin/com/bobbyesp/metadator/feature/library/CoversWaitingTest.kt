/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import com.bobbyesp.metadator.core.domain.artwork.LibraryCoverState
import com.bobbyesp.metadator.core.domain.quality.LibraryQualityState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoversWaitingTest {
    private val covers = LibraryCoverState(checked = 10, total = 100)
    private val lossless = LibraryQualityState(checked = 5, total = 50)

    @Test
    fun `the check of covers is said to wait while the lossless check has the files`() {
        assertTrue(LibraryState(covers = covers, quality = lossless).coversWaiting)
    }

    @Test
    fun `it is not said to wait once the lossless check is done, nor once it is done itself`() {
        assertFalse(
            LibraryState(covers = covers, quality = lossless.copy(checked = 50)).coversWaiting
        )
        assertFalse(
            LibraryState(covers = covers.copy(checked = 100), quality = lossless).coversWaiting
        )
    }
}
