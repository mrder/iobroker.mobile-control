package com.mobilecontrol.app.domain.repository

import com.mobilecontrol.app.domain.model.Dashboard
import kotlinx.coroutines.flow.Flow

interface DashboardRepository {
    fun observeDashboards(): Flow<List<Dashboard>>
    suspend fun refreshDashboards(): Result<Unit>
    suspend fun getDashboard(id: String): Dashboard?

    suspend fun createDashboard(dashboard: Dashboard): Result<Dashboard>
    suspend fun updateDashboard(dashboard: Dashboard): Result<Dashboard>
    suspend fun deleteDashboard(id: String): Result<Unit>

    suspend fun setStartDashboard(id: String)
    suspend fun getStartDashboardId(): String?
}
