package com.mobilecontrol.app.ui.objects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilecontrol.app.domain.model.LiveValue
import com.mobilecontrol.app.domain.model.ObjectCatalogItem
import com.mobilecontrol.app.domain.repository.ConnectionState
import com.mobilecontrol.app.domain.repository.ObjectCatalogRepository
import com.mobilecontrol.app.domain.repository.StateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ObjectBrowserUiState(
    val allObjects: List<ObjectCatalogItem> = emptyList(),
    val searchQuery: String = "",
    val selectedRoom: String? = null,
    val selectedRole: String? = null,
    val writableOnly: Boolean = false,
    val liveValues: Map<String, LiveValue> = emptyMap(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isRefreshing: Boolean = false,
) {
    val rooms: List<String> get() = allObjects.map { it.roomHeuristic }.distinct().sorted()
    val roles: List<String> get() = allObjects.mapNotNull { it.role }.distinct().sorted()

    val filteredObjects: List<ObjectCatalogItem>
        get() = allObjects.filter { item ->
            (searchQuery.isBlank() || item.name.contains(searchQuery, true) || item.path.joinToString("/").contains(searchQuery, true)) &&
                (selectedRoom == null || item.roomHeuristic == selectedRoom) &&
                (selectedRole == null || item.role == selectedRole) &&
                (!writableOnly || item.canWrite)
        }
}

@HiltViewModel
class ObjectBrowserViewModel @Inject constructor(
    private val catalogRepository: ObjectCatalogRepository,
    private val stateRepository: StateRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(ObjectBrowserUiState())

    val uiState: StateFlow<ObjectBrowserUiState> = combine(
        catalogRepository.observeCatalog(),
        filters,
        stateRepository.liveValues,
        stateRepository.connectionState,
    ) { catalog, filterState, live, connection ->
        filterState.copy(allObjects = catalog, liveValues = live, connectionState = connection)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ObjectBrowserUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            filters.update { it.copy(isRefreshing = true) }
            catalogRepository.refreshCatalog()
            filters.update { it.copy(isRefreshing = false) }
        }
    }

    fun setSearchQuery(query: String) = filters.update { it.copy(searchQuery = query) }
    fun setRoomFilter(room: String?) = filters.update { it.copy(selectedRoom = room) }
    fun setRoleFilter(role: String?) = filters.update { it.copy(selectedRole = role) }
    fun setWritableOnly(writableOnly: Boolean) = filters.update { it.copy(writableOnly = writableOnly) }

    fun subscribeVisible(objectIds: Set<String>) {
        stateRepository.subscribe(objectIds)
        viewModelScope.launch { stateRepository.fetchInitialStates(objectIds.toList()) }
    }

    fun unsubscribeVisible(objectIds: Set<String>) {
        stateRepository.unsubscribe(objectIds)
    }
}
