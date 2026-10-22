/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.editor

import com.bobbyesp.metadator.feature.editor.lookup.LookupViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val editorModule = module {
    viewModelOf(::EditorViewModel)
    viewModelOf(::LookupViewModel)
}
