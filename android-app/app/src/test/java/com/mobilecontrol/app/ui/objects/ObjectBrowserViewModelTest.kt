package com.mobilecontrol.app.ui.objects

import com.mobilecontrol.app.domain.model.LiveValue
import com.mobilecontrol.app.domain.model.ObjectCatalogItem
import com.mobilecontrol.app.domain.model.ValueType
import com.mobilecontrol.app.domain.repository.ConnectionState
import com.mobilecontrol.app.domain.repository.ObjectCatalogRepository
import com.mobilecontrol.app.domain.repository.StateRepository
import com.mobilecontrol.app.util.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun catalogItem(
    id: String,
    name: String,
    room: String,
    role: String? = null,
    canWrite: Boolean = false,
): ObjectCatalogItem = ObjectCatalogItem(
    id = id,
    name = name,
    path = listOf(room, name),
    role = role,
    valueType = ValueType.NUMBER,
    unit = null,
    canRead = true,
    canWrite = canWrite,
    hasHistory = false,
    suggestedWidgets = emptyList(),
)

private class FakeObjectCatalogRepository(private val items: List<ObjectCatalogItem>) : ObjectCatalogRepository {
    var refreshCalls = 0
    private val _catalog = MutableStateFlow(items)

    override fun observeCatalog(): Flow<List<ObjectCatalogItem>> = _catalog

    override suspend fun refreshCatalog(): Result<Unit> {
        refreshCalls++
        return Result.success(Unit)
    }
}

private class FakeStateRepository : StateRepository {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _liveValues = MutableStateFlow<Map<String, LiveValue>>(emptyMap())
    override val liveValues: StateFlow<Map<String, LiveValue>> = _liveValues

    val subscribedBatches = mutableListOf<Set<String>>()
    val unsubscribedBatches = mutableListOf<Set<String>>()

    override suspend fun fetchInitialStates(objectIds: List<String>): Result<Unit> = Result.success(Unit)
    override fun subscribe(objectIds: Set<String>) { subscribedBatches.add(objectIds) }
    override fun unsubscribe(objectIds: Set<String>) { unsubscribedBatches.add(objectIds) }
    override fun connect() {}
    override fun disconnect() {}
}

class ObjectBrowserViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val livingRoomLamp = catalogItem("lamp", "Lampe", room = "Wohnzimmer", role = "light", canWrite = true)
    private val kitchenSensor = catalogItem("sensor", "Temperatursensor", room = "Küche", role = "sensor", canWrite = false)

    @Test
    fun `init triggers a catalog refresh and toggles isRefreshing back off`() = runTest {
        val catalogRepo = FakeObjectCatalogRepository(listOf(livingRoomLamp))
        val viewModel = ObjectBrowserViewModel(catalogRepo, FakeStateRepository())
        advanceUntilIdle()

        assertEquals(1, catalogRepo.refreshCalls)
        val collector = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
        collector.cancel()
    }

    @Test
    fun `filteredObjects narrows by search query across name and path`() = runTest {
        val catalogRepo = FakeObjectCatalogRepository(listOf(livingRoomLamp, kitchenSensor))
        val viewModel = ObjectBrowserViewModel(catalogRepo, FakeStateRepository())
        val collector = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSearchQuery("temperatur")

        assertEquals(listOf("sensor"), viewModel.uiState.value.filteredObjects.map { it.id })
        collector.cancel()
    }

    @Test
    fun `filteredObjects narrows by room and role filters combined`() = runTest {
        val catalogRepo = FakeObjectCatalogRepository(listOf(livingRoomLamp, kitchenSensor))
        val viewModel = ObjectBrowserViewModel(catalogRepo, FakeStateRepository())
        val collector = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setRoomFilter("Wohnzimmer")
        viewModel.setRoleFilter("sensor") // no object is both in Wohnzimmer and role=sensor

        assertTrue(viewModel.uiState.value.filteredObjects.isEmpty())
        collector.cancel()
    }

    @Test
    fun `writableOnly filter excludes read-only objects`() = runTest {
        val catalogRepo = FakeObjectCatalogRepository(listOf(livingRoomLamp, kitchenSensor))
        val viewModel = ObjectBrowserViewModel(catalogRepo, FakeStateRepository())
        val collector = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setWritableOnly(true)

        assertEquals(listOf("lamp"), viewModel.uiState.value.filteredObjects.map { it.id })
        collector.cancel()
    }

    @Test
    fun `rooms and roles are derived, distinct and sorted from the full catalog regardless of active filters`() = runTest {
        val catalogRepo = FakeObjectCatalogRepository(listOf(livingRoomLamp, kitchenSensor))
        val viewModel = ObjectBrowserViewModel(catalogRepo, FakeStateRepository())
        val collector = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setRoomFilter("Wohnzimmer") // filtering must not shrink the room/role option lists themselves

        assertEquals(listOf("Küche", "Wohnzimmer"), viewModel.uiState.value.rooms)
        assertEquals(listOf("light", "sensor"), viewModel.uiState.value.roles)
        collector.cancel()
    }

    @Test
    fun `subscribeVisible subscribes and fetches initial states for exactly the given ids`() = runTest {
        val stateRepo = FakeStateRepository()
        val viewModel = ObjectBrowserViewModel(FakeObjectCatalogRepository(emptyList()), stateRepo)

        viewModel.subscribeVisible(setOf("lamp", "sensor"))
        advanceUntilIdle()

        assertEquals(listOf(setOf("lamp", "sensor")), stateRepo.subscribedBatches)
    }

    @Test
    fun `unsubscribeVisible forwards the id set to the repository`() {
        val stateRepo = FakeStateRepository()
        val viewModel = ObjectBrowserViewModel(FakeObjectCatalogRepository(emptyList()), stateRepo)

        viewModel.unsubscribeVisible(setOf("lamp"))

        assertEquals(listOf(setOf("lamp")), stateRepo.unsubscribedBatches)
    }
}
