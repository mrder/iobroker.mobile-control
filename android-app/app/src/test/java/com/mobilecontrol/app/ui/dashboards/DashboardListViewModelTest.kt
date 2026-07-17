package com.mobilecontrol.app.ui.dashboards

import com.mobilecontrol.app.domain.model.Dashboard
import com.mobilecontrol.app.domain.model.DashboardLayout
import com.mobilecontrol.app.domain.model.SizeClass
import com.mobilecontrol.app.domain.repository.DashboardRepository
import com.mobilecontrol.app.util.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun testDashboard(id: String, name: String, revision: Long = 0): Dashboard = Dashboard(
    id = id,
    name = name,
    revision = revision,
    layouts = SizeClass.entries.map { DashboardLayout(it, it.defaultColumns, emptyList()) },
)

private class FakeDashboardRepository(seed: List<Dashboard> = emptyList()) : DashboardRepository {
    private val _dashboards = MutableStateFlow(seed)
    var refreshCalls = 0
    var refreshResult: Result<Unit> = Result.success(Unit)
    val created = mutableListOf<Dashboard>()
    val deleted = mutableListOf<String>()
    val updated = mutableListOf<Dashboard>()
    var startDashboardId: String? = null
    var createResult: ((Dashboard) -> Result<Dashboard>) = { Result.success(it) }

    override fun observeDashboards(): Flow<List<Dashboard>> = _dashboards

    override suspend fun refreshDashboards(): Result<Unit> {
        refreshCalls++
        return refreshResult
    }

    override suspend fun getDashboard(id: String): Dashboard? = _dashboards.value.firstOrNull { it.id == id }

    override suspend fun createDashboard(dashboard: Dashboard): Result<Dashboard> {
        created.add(dashboard)
        val result = createResult(dashboard)
        result.onSuccess { _dashboards.value = _dashboards.value + it }
        return result
    }

    override suspend fun updateDashboard(dashboard: Dashboard): Result<Dashboard> {
        updated.add(dashboard)
        _dashboards.value = _dashboards.value.map { if (it.id == dashboard.id) dashboard else it }
        return Result.success(dashboard)
    }

    override suspend fun deleteDashboard(id: String): Result<Unit> {
        deleted.add(id)
        _dashboards.value = _dashboards.value.filterNot { it.id == id }
        return Result.success(Unit)
    }

    override suspend fun setStartDashboard(id: String) {
        startDashboardId = id
    }

    override suspend fun getStartDashboardId(): String? = startDashboardId
}

class DashboardListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `init triggers an initial refresh from the repository`() = runTest {
        val repo = FakeDashboardRepository()
        DashboardListViewModel(repo)
        advanceUntilIdle()

        assertEquals(1, repo.refreshCalls)
    }

    @Test
    fun `dashboards reflects the repository's observed list while collected`() = runTest {
        val repo = FakeDashboardRepository(listOf(testDashboard("a", "A")))
        val viewModel = DashboardListViewModel(repo)
        val collector = launch { viewModel.dashboards.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("A"), viewModel.dashboards.value.map { it.name })
        collector.cancel()
    }

    @Test
    fun `createDashboard builds a fresh layout per size class at revision 0 and invokes the callback with the new id`() = runTest {
        val repo = FakeDashboardRepository()
        val viewModel = DashboardListViewModel(repo)
        var createdId: String? = null

        viewModel.createDashboard("Wohnzimmer") { id -> createdId = id }
        advanceUntilIdle()

        assertEquals(1, repo.created.size)
        val createdDashboard = repo.created.single()
        assertEquals("Wohnzimmer", createdDashboard.name)
        assertEquals(0, createdDashboard.revision)
        assertEquals(SizeClass.entries.size, createdDashboard.layouts.size)
        assertTrue(createdDashboard.layouts.all { it.widgets.isEmpty() })
        assertEquals(createdDashboard.id, createdId)
    }

    @Test
    fun `createDashboard does not invoke the callback when the repository call fails`() = runTest {
        val repo = FakeDashboardRepository()
        repo.createResult = { Result.failure(RuntimeException("offline")) }
        val viewModel = DashboardListViewModel(repo)
        var callbackInvoked = false

        viewModel.createDashboard("Büro") { callbackInvoked = true }
        advanceUntilIdle()

        assertTrue(!callbackInvoked)
    }

    @Test
    fun `duplicateDashboard creates a copy with a new id, reset revision and a German 'Kopie' suffix`() = runTest {
        val original = testDashboard("orig", "Küche", revision = 7)
        val repo = FakeDashboardRepository(listOf(original))
        val viewModel = DashboardListViewModel(repo)

        viewModel.duplicateDashboard(original)
        advanceUntilIdle()

        val copy = repo.created.single()
        assertEquals("Küche (Kopie)", copy.name)
        assertEquals(0, copy.revision)
        assertTrue(copy.id != original.id)
    }

    @Test
    fun `deleteDashboard forwards the id to the repository`() = runTest {
        val repo = FakeDashboardRepository(listOf(testDashboard("a", "A")))
        val viewModel = DashboardListViewModel(repo)

        viewModel.deleteDashboard("a")
        advanceUntilIdle()

        assertEquals(listOf("a"), repo.deleted)
    }

    @Test
    fun `renameDashboard preserves everything but the name`() = runTest {
        val original = testDashboard("a", "Altname", revision = 3)
        val repo = FakeDashboardRepository(listOf(original))
        val viewModel = DashboardListViewModel(repo)

        viewModel.renameDashboard(original, "Neuname")
        advanceUntilIdle()

        val updated = repo.updated.single()
        assertEquals("Neuname", updated.name)
        assertEquals(original.id, updated.id)
        assertEquals(original.revision, updated.revision)
    }

    @Test
    fun `setStartDashboard forwards the id to the repository`() = runTest {
        val repo = FakeDashboardRepository()
        val viewModel = DashboardListViewModel(repo)

        viewModel.setStartDashboard("dash-5")
        advanceUntilIdle()

        assertEquals("dash-5", repo.startDashboardId)
    }
}
