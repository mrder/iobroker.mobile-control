package com.mobilecontrol.app.ui.widgets

import androidx.lifecycle.ViewModel
import com.mobilecontrol.app.domain.model.HistoryEntry
import com.mobilecontrol.app.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin DI shim so [HistoryWidget] (a plain @Composable, not tied to a screen-level ViewModel) can
 * reach [HistoryRepository] via `hiltViewModel()`. Deliberately stateless - it forwards straight to
 * the repository and holds no mutable fields - so it is safe for every HistoryWidget instance on a
 * dashboard to share the same ViewModel instance (the default `hiltViewModel()` scoping), unlike a
 * typical screen ViewModel which owns per-screen state.
 */
@HiltViewModel
class HistoryWidgetViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
) : ViewModel() {
    suspend fun loadHistory(objectId: String, from: String?, to: String?, limit: Int?): Result<List<HistoryEntry>> =
        historyRepository.getHistory(objectId, from, to, limit)
}
