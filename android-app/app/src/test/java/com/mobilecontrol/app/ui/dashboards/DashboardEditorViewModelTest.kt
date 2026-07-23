package com.mobilecontrol.app.ui.dashboards

import androidx.lifecycle.SavedStateHandle
import com.mobilecontrol.app.domain.model.ApiErrorCode
import com.mobilecontrol.app.domain.model.ApiException
import com.mobilecontrol.app.domain.model.CommandStatus
import com.mobilecontrol.app.domain.model.Dashboard
import com.mobilecontrol.app.domain.model.DashboardLayout
import com.mobilecontrol.app.domain.model.LiveValue
import com.mobilecontrol.app.domain.model.ObjectCatalogItem
import com.mobilecontrol.app.domain.model.SizeClass
import com.mobilecontrol.app.domain.model.ValueType
import com.mobilecontrol.app.domain.model.Widget
import com.mobilecontrol.app.domain.model.WidgetType
import com.mobilecontrol.app.domain.repository.CommandRepository
import com.mobilecontrol.app.domain.repository.ConnectionState
import com.mobilecontrol.app.domain.repository.DashboardRepository
import com.mobilecontrol.app.domain.repository.ObjectCatalogRepository
import com.mobilecontrol.app.domain.repository.StateRepository
import com.mobilecontrol.app.ui.navigation.Routes
import com.mobilecontrol.app.util.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun widget(id: String, objectId: String? = null, x: Int = 0, y: Int = 0, w: Int = 2, h: Int = 1): Widget = Widget(
    id = id, objectId = objectId, type = WidgetType.TEXT_VALUE, title = id, x = x, y = y, w = w, h = h,
)

/** Builds a Dashboard whose COMPACT layout (4 columns) carries [widgets]; the other size classes stay empty. */
private fun testDashboard(id: String = "dash-1", revision: Long = 0, widgets: List<Widget> = emptyList()): Dashboard = Dashboard(
    id = id,
    name = "Test-Dashboard",
    revision = revision,
    layouts = SizeClass.entries.map { sizeClass ->
        DashboardLayout(sizeClass, sizeClass.defaultColumns, if (sizeClass == SizeClass.COMPACT) widgets else emptyList())
    },
)

private class FakeDashboardRepository(private var dashboard: Dashboard?) : DashboardRepository {
    var updateResult: ((Dashboard) -> Result<Dashboard>) = { Result.success(it) }
    val updateCalls = mutableListOf<Dashboard>()
    var getDashboardCalls = 0
    /** Simulates a server-side dashboard that has moved on to a higher revision than the client knows about. */
    var serverRevisionOverride: Long? = null

    override fun observeDashboards(): Flow<List<Dashboard>> = MutableStateFlow(listOfNotNull(dashboard))
    override suspend fun refreshDashboards(): Result<Unit> = Result.success(Unit)

    override suspend fun getDashboard(id: String): Dashboard? {
        getDashboardCalls++
        val current = dashboard ?: return null
        return serverRevisionOverride?.let { current.copy(revision = it) } ?: current
    }

    override suspend fun createDashboard(dashboard: Dashboard): Result<Dashboard> {
        this.dashboard = dashboard
        return Result.success(dashboard)
    }

    override suspend fun updateDashboard(dashboard: Dashboard): Result<Dashboard> {
        updateCalls.add(dashboard)
        val result = updateResult(dashboard)
        result.onSuccess { this.dashboard = it }
        return result
    }

    override suspend fun deleteDashboard(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun setStartDashboard(id: String) {}
    override suspend fun getStartDashboardId(): String? = null
}

private class FakeObjectCatalogRepository(private val items: List<ObjectCatalogItem> = emptyList()) : ObjectCatalogRepository {
    override fun observeCatalog(): Flow<List<ObjectCatalogItem>> = MutableStateFlow(items)
    override suspend fun refreshCatalog(): Result<Unit> = Result.success(Unit)
}

private class FakeStateRepository : StateRepository {
    override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.CONNECTED)
    override val liveValues: StateFlow<Map<String, LiveValue>> = MutableStateFlow(emptyMap())
    val subscribedBatches = mutableListOf<Set<String>>()

    override suspend fun fetchInitialStates(objectIds: List<String>): Result<Unit> = Result.success(Unit)
    override fun subscribe(objectIds: Set<String>) { subscribedBatches.add(objectIds) }
    override fun unsubscribe(objectIds: Set<String>) {}
    override fun connect() {}
    override fun disconnect() {}
}

private class FakeCommandRepository : CommandRepository {
    override val commandStates: StateFlow<Map<String, CommandStatus>> = MutableStateFlow(emptyMap())
    data class SentCommand(val objectId: String, val value: Any?, val confirmed: Boolean)
    val sentCommands = mutableListOf<SentCommand>()
    var sendResult: Result<String> = Result.success("cmd-1")

    override suspend fun sendCommand(objectId: String, value: Any?, confirmed: Boolean): Result<String> {
        sentCommands.add(SentCommand(objectId, value, confirmed))
        return sendResult
    }
}

/**
 * [DashboardEditorUiState] is built via stateIn(WhileSubscribed) - it only updates while someone is
 * actively collecting it. Uses [TestScope.backgroundScope] (not `this.launch`/the test's own scope)
 * because collecting a StateFlow never completes on its own - a plain child of the test's scope
 * would leave `runTest` waiting forever and fail with UncompletedCoroutinesError; backgroundScope
 * jobs are cancelled automatically when the test body returns.
 */
private fun TestScope.collectUiState(viewModel: DashboardEditorViewModel) {
    backgroundScope.launch { viewModel.uiState.collect {} }
}

class DashboardEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun buildViewModel(
        dashboard: Dashboard?,
        dashboardRepo: FakeDashboardRepository = FakeDashboardRepository(dashboard),
        catalogRepo: FakeObjectCatalogRepository = FakeObjectCatalogRepository(),
        stateRepo: FakeStateRepository = FakeStateRepository(),
        commandRepo: FakeCommandRepository = FakeCommandRepository(),
    ): DashboardEditorViewModel {
        val savedStateHandle = SavedStateHandle(mapOf(Routes.DASHBOARD_EDITOR_ARG to (dashboard?.id ?: "dash-1")))
        return DashboardEditorViewModel(dashboardRepo, catalogRepo, stateRepo, commandRepo, savedStateHandle)
    }

    @Test
    fun `init loads the dashboard and subscribes to every object referenced by the current layout`() = runTest {
        val dashboard = testDashboard(widgets = listOf(widget("w1", objectId = "obj1"), widget("w2", objectId = "obj2", x = 2)))
        val stateRepo = FakeStateRepository()
        val viewModel = buildViewModel(dashboard, stateRepo = stateRepo)
        collectUiState(viewModel)
        advanceUntilIdle()

        assertEquals(dashboard, viewModel.uiState.value.dashboard)
        assertEquals(setOf(setOf("obj1", "obj2")), stateRepo.subscribedBatches.toSet())
    }

    @Test
    fun `addWidget places the widget in a free grid slot, closes the dialog and subscribes to its object`() = runTest {
        val dashboard = testDashboard(widgets = listOf(widget("existing", x = 0, y = 0, w = 2, h = 1)))
        val stateRepo = FakeStateRepository()
        val viewModel = buildViewModel(dashboard, stateRepo = stateRepo)
        collectUiState(viewModel)
        advanceUntilIdle()
        viewModel.showAddWidgetDialog(true)

        val catalogItem = ObjectCatalogItem(
            id = "obj-new", name = "Neuer Sensor", path = listOf("Flur"), role = null,
            valueType = ValueType.NUMBER, unit = "°C",
            canRead = true, canWrite = false, hasHistory = false, suggestedWidgets = emptyList(),
        )
        viewModel.addWidget(catalogItem, WidgetType.TEMPERATURE, "Flur-Sensor")

        val layout = viewModel.uiState.value.currentLayout!!
        assertEquals(2, layout.widgets.size)
        val added = layout.widgets.first { it.id != "existing" }
        assertEquals("obj-new", added.objectId)
        assertEquals(2 to 0, added.x to added.y) // first free slot to the right of "existing" in a 4-column layout
        assertFalse(viewModel.uiState.value.showAddWidgetDialog)
        assertTrue(stateRepo.subscribedBatches.any { it == setOf("obj-new") })
    }

    @Test
    fun `addWidget with a urlEmbedId stores it in widget config and leaves objectId null`() = runTest {
        val dashboard = testDashboard()
        val viewModel = buildViewModel(dashboard)
        collectUiState(viewModel)
        advanceUntilIdle()
        viewModel.showAddWidgetDialog(true)

        viewModel.addWidget(null, WidgetType.URL_IMAGE, "Kamera-Link", urlEmbedId = "embed-1")

        val added = viewModel.uiState.value.currentLayout!!.widgets.single()
        assertNull(added.objectId)
        assertEquals("embed-1", added.config["urlEmbedId"])
    }

    @Test
    fun `removeWidget deletes only the targeted widget`() = runTest {
        val dashboard = testDashboard(widgets = listOf(widget("keep"), widget("drop", x = 2)))
        val viewModel = buildViewModel(dashboard)
        collectUiState(viewModel)
        advanceUntilIdle()

        viewModel.removeWidget("drop")

        assertEquals(listOf("keep"), viewModel.uiState.value.currentLayout!!.widgets.map { it.id })
    }

    @Test
    fun `moveWidgetTo applies a valid drop but rejects one that would overlap another widget`() = runTest {
        val dashboard = testDashboard(widgets = listOf(widget("a", x = 0, y = 0), widget("b", x = 2, y = 0)))
        val viewModel = buildViewModel(dashboard)
        collectUiState(viewModel)
        advanceUntilIdle()

        viewModel.moveWidgetTo("a", newX = 2, newY = 0) // would land exactly on top of "b" -> rejected
        val rejected = viewModel.uiState.value.currentLayout!!.widgets.first { it.id == "a" }
        assertEquals(0 to 0, rejected.x to rejected.y)

        viewModel.moveWidgetTo("a", newX = 1, newY = 1) // free cell -> accepted
        val moved = viewModel.uiState.value.currentLayout!!.widgets.first { it.id == "a" }
        assertEquals(1 to 1, moved.x to moved.y)
    }

    @Test
    fun `sendCommand records the returned commandId as pending on success, and nothing on failure`() = runTest {
        val dashboard = testDashboard()
        val commandRepo = FakeCommandRepository()
        val viewModel = buildViewModel(dashboard, commandRepo = commandRepo)
        collectUiState(viewModel)
        advanceUntilIdle()

        commandRepo.sendResult = Result.success("cmd-42")
        viewModel.sendCommand(objectId = "obj1", value = true, confirmed = true)
        advanceUntilIdle()
        assertEquals("cmd-42", viewModel.uiState.value.pendingCommandByObjectId["obj1"])
        assertEquals(FakeCommandRepository.SentCommand("obj1", true, true), commandRepo.sentCommands.last())

        commandRepo.sendResult = Result.failure(RuntimeException("rejected"))
        viewModel.sendCommand(objectId = "obj2", value = false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.pendingCommandByObjectId.containsKey("obj2"))
    }

    @Test
    fun `save() distinguishes a REVISION_CONFLICT (silent, flagged) from a generic failure (surfaced message)`() = runTest {
        val dashboard = testDashboard()
        val dashboardRepo = FakeDashboardRepository(dashboard)
        val viewModel = buildViewModel(dashboard, dashboardRepo = dashboardRepo)
        collectUiState(viewModel)
        advanceUntilIdle()

        dashboardRepo.updateResult = { Result.failure(ApiException(ApiErrorCode.REVISION_CONFLICT)) }
        viewModel.save()
        advanceUntilIdle()
        val conflictState = viewModel.uiState.value
        assertTrue(conflictState.revisionConflict)
        assertNull(conflictState.saveError)
        assertFalse(conflictState.isSaving)

        // clear the conflict flag, then hit a plain, non-conflict failure
        viewModel.resolveConflictDiscard()
        advanceUntilIdle()
        dashboardRepo.updateResult = { Result.failure(RuntimeException("Server nicht erreichbar")) }
        viewModel.save()
        advanceUntilIdle()
        val genericErrorState = viewModel.uiState.value
        assertFalse(genericErrorState.revisionConflict)
        assertEquals("Server nicht erreichbar", genericErrorState.saveError)
    }

    @Test
    fun `resolveConflictOverwrite forces the write past whatever revision the server currently holds`() = runTest {
        val dashboard = testDashboard(revision = 3)
        val dashboardRepo = FakeDashboardRepository(dashboard)
        val viewModel = buildViewModel(dashboard, dashboardRepo = dashboardRepo)
        collectUiState(viewModel)
        advanceUntilIdle() // initial load caches revision 3 into local state

        dashboardRepo.serverRevisionOverride = 9 // another device pushed a newer revision in the meantime
        viewModel.resolveConflictOverwrite()
        advanceUntilIdle()

        val forcedUpdate = dashboardRepo.updateCalls.single()
        assertEquals(10L, forcedUpdate.revision) // one past the server's revision (9), not the client's stale one (3)
        assertFalse(viewModel.uiState.value.revisionConflict)
        assertFalse(viewModel.uiState.value.editMode)
    }

    @Test
    fun `resolveConflictDiscard reloads the server's copy and drops local edits`() = runTest {
        val dashboard = testDashboard(widgets = listOf(widget("local-only")))
        val dashboardRepo = FakeDashboardRepository(dashboard)
        val viewModel = buildViewModel(dashboard, dashboardRepo = dashboardRepo)
        collectUiState(viewModel)
        advanceUntilIdle()
        viewModel.removeWidget("local-only") // unsaved local edit

        viewModel.resolveConflictDiscard()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.revisionConflict)
        assertFalse(state.editMode)
        assertEquals(listOf("local-only"), state.currentLayout!!.widgets.map { it.id }) // server copy restored
    }
}
