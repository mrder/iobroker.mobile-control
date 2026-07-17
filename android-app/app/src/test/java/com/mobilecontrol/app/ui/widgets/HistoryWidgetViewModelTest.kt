package com.mobilecontrol.app.ui.widgets

import com.mobilecontrol.app.domain.model.HistoryEntry
import com.mobilecontrol.app.domain.repository.HistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeHistoryRepository : HistoryRepository {
    data class Call(val objectId: String, val from: String?, val to: String?, val limit: Int?)

    val calls = mutableListOf<Call>()
    var result: Result<List<HistoryEntry>> = Result.success(emptyList())

    override suspend fun getHistory(objectId: String, from: String?, to: String?, limit: Int?): Result<List<HistoryEntry>> {
        calls.add(Call(objectId, from, to, limit))
        return result
    }
}

/**
 * [HistoryWidgetViewModel] never touches `viewModelScope` - it's a thin, stateless suspend
 * pass-through - so unlike the other ViewModels in this package no MainDispatcherRule / Main
 * dispatcher installation is required here.
 */
class HistoryWidgetViewModelTest {

    @Test
    fun `loadHistory forwards all parameters to the repository unchanged`() = runTest {
        val repo = FakeHistoryRepository()
        val entries = listOf(HistoryEntry(21.5, 1_000L), HistoryEntry(21.7, 2_000L))
        repo.result = Result.success(entries)
        val viewModel = HistoryWidgetViewModel(repo)

        val result = viewModel.loadHistory("obj1", from = "2024-01-01T00:00:00Z", to = "2024-01-02T00:00:00Z", limit = 100)

        assertEquals(listOf(FakeHistoryRepository.Call("obj1", "2024-01-01T00:00:00Z", "2024-01-02T00:00:00Z", 100)), repo.calls)
        assertEquals(entries, result.getOrNull())
    }

    @Test
    fun `loadHistory passes null optional bounds through untouched so the server can apply its defaults`() = runTest {
        val repo = FakeHistoryRepository()
        val viewModel = HistoryWidgetViewModel(repo)

        viewModel.loadHistory("obj1", from = null, to = null, limit = null)

        val call = repo.calls.single()
        assertEquals(null, call.from)
        assertEquals(null, call.to)
        assertEquals(null, call.limit)
    }

    @Test
    fun `loadHistory propagates a failure Result unchanged instead of throwing`() = runTest {
        val repo = FakeHistoryRepository()
        val error = RuntimeException("network down")
        repo.result = Result.failure(error)
        val viewModel = HistoryWidgetViewModel(repo)

        val result = viewModel.loadHistory("obj1", null, null, null)

        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }
}
