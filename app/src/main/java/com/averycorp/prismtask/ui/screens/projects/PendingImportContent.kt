package com.averycorp.prismtask.ui.screens.projects

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-level one-shot holder for pasted import text. Compose
 * Navigation's `SavedStateHandle` arg has a practical size cap that
 * truncates real schedules, so the paste-import flow stages the text
 * here and the preview ViewModel reads it via [consume] (which clears
 * the slot). File imports use the URI nav arg directly — only paste
 * goes through this holder.
 */
@Singleton
class PendingImportContent @Inject constructor() {
    @Volatile private var pending: String? = null

    fun stage(content: String) {
        pending = content
    }

    fun consume(): String? {
        val current = pending
        pending = null
        return current
    }
}
