package com.mobilecontrol.app.ui.dashboards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilecontrol.app.domain.model.Dashboard
import com.mobilecontrol.app.domain.model.DashboardLayout
import com.mobilecontrol.app.domain.model.SizeClass
import com.mobilecontrol.app.domain.repository.DashboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DashboardListViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    val dashboards: StateFlow<List<Dashboard>> = dashboardRepository.observeDashboards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { dashboardRepository.refreshDashboards() }
    }

    fun createDashboard(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val newDashboard = Dashboard(
                id = UUID.randomUUID().toString(),
                name = name,
                revision = 0,
                layouts = SizeClass.entries.map { DashboardLayout(it, it.defaultColumns, emptyList()) },
            )
            dashboardRepository.createDashboard(newDashboard).onSuccess { onCreated(it.id) }
        }
    }

    fun deleteDashboard(id: String) {
        viewModelScope.launch { dashboardRepository.deleteDashboard(id) }
    }

    fun duplicateDashboard(dashboard: Dashboard) {
        viewModelScope.launch {
            val copy = dashboard.copy(id = UUID.randomUUID().toString(), name = "${dashboard.name} (Kopie)", revision = 0)
            dashboardRepository.createDashboard(copy)
        }
    }

    fun renameDashboard(dashboard: Dashboard, newName: String) {
        viewModelScope.launch { dashboardRepository.updateDashboard(dashboard.copy(name = newName)) }
    }

    fun setStartDashboard(id: String) {
        viewModelScope.launch { dashboardRepository.setStartDashboard(id) }
    }
}
