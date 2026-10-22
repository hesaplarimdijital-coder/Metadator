/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.batch

import com.bobbyesp.metadator.core.domain.lookup.FindAlbumUseCase
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val batchModule = module {
    // Only the batch editor looks for a whole album: it is the one to declare it.
    factory { FindAlbumUseCase(get()) }
    viewModelOf(::BatchViewModel)
    viewModelOf(::AlbumLookupViewModel)
}
