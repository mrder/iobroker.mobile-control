package com.mobilecontrol.app.ui.objects

import com.mobilecontrol.app.domain.model.CommandStatus
import com.mobilecontrol.app.domain.model.LiveValue
import com.mobilecontrol.app.domain.model.ObjectCatalogItem
import com.mobilecontrol.app.domain.model.ValueType
import com.mobilecontrol.app.domain.repository.CommandRepository
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

private class FakeObjectCatalogRepository(
    private val items: List<ObjectCatalogItem>,
    private val folderNames: Map<String, String> = emptyMap(),
) : ObjectCatalogRepository {
    var refreshCalls = 0
    private val _catalog = MutableStateFlow(items)

    override fun observeCatalog(): Flow<List<ObjectCatalogItem>> = _catalog

    override fun observeFolderNames(): Flow<Map<String, String>> = MutableStateFlow(folderNames)

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

private class FakeCommandRepository : CommandRepository {
    override val commandStates: StateFlow<Map<String, CommandStatus>> = MutableStateFlow(emptyMap())
    data class SentCommand(val objectId: String, val value: Any?, val confirmed: Boolean)
    val sentCommands = mutableListOf<SentCommand>()

    override suspend fun sendCommand(objectId: String, value: Any?, confirmed: Boolean): Result<String> {
        sentCommands.add(SentCommand(objectId, value, confirmed))
        return Result.success("cmd-1")
    }
}

class ObjectBrowserViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val livingRoomLamp = catalogItem("lamp", "Lampe", room = "Wohnzimmer", role = "light", canWrite = true)
    private val kitchenSensor = catalogItem("sensor", "Temperatursensor", room = "Küche", role = "sensor", canWrite = false)

    @Test
    fun `init triggers a catalog refresh and toggles isRefreshing back off`() = runTest {
        val catalogRepo = FakeObjectCatalogRepository(listOf(livingRoomLamp))
        val viewModel = ObjectBrowserViewModel(catalogRepo, FakeStateRepository(), FakeCommandRepository())
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
        val viewModel = ObjectBrowserViewModel(catalogRepo, FakeStateRepository(), FakeCommandRepository())
        val collector = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSearchQuery("temperatur")

        assertEquals(listOf("sensor"), viewModel.uiState.value.filteredObjects.map { it.id })
        collector.cancel()
    }

    @Test
    fun `filteredObjects narrows by room and role filters combined`() = runTest {
        val catalogRepo = FakeObjectCatalogRepository(listOf(livingRoomLamp, kitchenSensor))
        val viewModel = ObjectBrowserViewModel(catalogRepo, FakeStateRepository(), FakeCommandRepository())
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
        val viewModel = ObjectBrowserViewModel(catalogRepo, FakeStateRepository(), FakeCommandRepository())
        val collector = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setWritableOnly(true)

        assertEquals(listOf("lamp"), viewModel.uiState.value.filteredObjects.map { it.id })
        collector.cancel()
    }

    @Test
    fun `rooms and roles are derived, distinct and sorted from the full catalog regardless of active filters`() = runTest {
        val catalogRepo = FakeObjectCatalogRepository(listOf(livingRoomLamp, kitchenSensor))
        val viewModel = ObjectBrowserViewModel(catalogRepo, FakeStateRepository(), FakeCommandRepository())
        val collector = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setRoomFilter("Wohnzimmer") // filtering must not shrink the room/role option lists themselves

        assertEquals(listOf("Küche", "Wohnzimmer"), viewModel.uiState.value.rooms)
        assertEquals(listOf("light", "sensor"), viewModel.uiState.value.roles)
        collector.cancel()
    }

    @Test
    fun `hasActiveFilter is false unfiltered and true as soon as any single filter is set`() = runTest {
        val catalogRepo = FakeObjectCatalogRepository(listOf(livingRoomLamp, kitchenSensor))
        val viewModel = ObjectBrowserViewModel(catalogRepo, FakeStateRepository(), FakeCommandRepository())
        val collector = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasActiveFilter)
        viewModel.setSearchQuery("lampe")
        assertTrue(viewModel.uiState.value.hasActiveFilter)
        viewModel.setSearchQuery("")
        assertFalse(viewModel.uiState.value.hasActiveFilter)
        viewModel.setWritableOnly(true)
        assertTrue(viewModel.uiState.value.hasActiveFilter)
        collector.cancel()
    }

    @Test
    fun `tree groups the unfiltered catalog by room regardless of active filters`() = runTest {
        val catalogRepo = FakeObjectCatalogRepository(listOf(livingRoomLamp, kitchenSensor))
        val viewModel = ObjectBrowserViewModel(catalogRepo, FakeStateRepository(), FakeCommandRepository())
        val collector = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setRoomFilter("Wohnzimmer") // the tree itself must stay the full, unfiltered catalog

        val roomNames = viewModel.uiState.value.tree.children.map { it.name }.sorted()
        assertEquals(listOf("Küche", "Wohnzimmer"), roomNames)
        collector.cancel()
    }

    @Test
    fun `tree resolves folder display names from the repository's folderNames, not the raw path segment`() = runTest {
        val catalogRepo = FakeObjectCatalogRepository(
            listOf(livingRoomLamp),
            folderNames = mapOf("Wohnzimmer" to "Wohnzimmer (Erdgeschoss)"),
        )
        val viewModel = ObjectBrowserViewModel(catalogRepo, FakeStateRepository(), FakeCommandRepository())
        val collector = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("Wohnzimmer (Erdgeschoss)"), viewModel.uiState.value.tree.children.map { it.name })
        collector.cancel()
    }

    @Test
    fun `subscribeVisible subscribes and fetches initial states for exactly the given ids`() = runTest {
        val stateRepo = FakeStateRepository()
        val viewModel = ObjectBrowserViewModel(FakeObjectCatalogRepository(emptyList()), stateRepo, FakeCommandRepository())

        viewModel.subscribeVisible(setOf("lamp", "sensor"))
        advanceUntilIdle()

        assertEquals(listOf(setOf("lamp", "sensor")), stateRepo.subscribedBatches)
    }

    @Test
    fun `unsubscribeVisible forwards the id set to the repository`() {
        val stateRepo = FakeStateRepository()
        val viewModel = ObjectBrowserViewModel(FakeObjectCatalogRepository(emptyList()), stateRepo, FakeCommandRepository())

        viewModel.unsubscribeVisible(setOf("lamp"))

        assertEquals(listOf(setOf("lamp")), stateRepo.unsubscribedBatches)
    }

    @Test
    fun `sendCommand forwards the object id, value and confirmed flag to the command repository`() = runTest {
        val commandRepo = FakeCommandRepository()
        val viewModel = ObjectBrowserViewModel(FakeObjectCatalogRepository(emptyList()), FakeStateRepository(), commandRepo)

        viewModel.sendCommand("lamp", true, confirmed = false)
        advanceUntilIdle()

        assertEquals(listOf(FakeCommandRepository.SentCommand("lamp", true, false)), commandRepo.sentCommands)
    }
}
