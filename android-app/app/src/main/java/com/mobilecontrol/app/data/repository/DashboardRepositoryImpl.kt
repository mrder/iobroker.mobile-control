package com.mobilecontrol.app.data.repository

import com.mobilecontrol.app.data.local.SettingsDataStore
import com.mobilecontrol.app.data.local.dao.DashboardDao
import com.mobilecontrol.app.data.local.entity.DashboardEntity
import com.mobilecontrol.app.data.remote.ApiService
import com.mobilecontrol.app.data.remote.dto.DashboardDto
import com.mobilecontrol.app.data.remote.dto.DashboardLayoutDto
import com.mobilecontrol.app.data.remote.dto.WidgetDto
import com.mobilecontrol.app.data.remote.safeApiCall
import com.mobilecontrol.app.domain.model.Dashboard
import com.mobilecontrol.app.domain.model.DashboardLayout
import com.mobilecontrol.app.domain.model.SizeClass
import com.mobilecontrol.app.domain.model.Widget
import com.mobilecontrol.app.domain.model.WidgetType
import com.mobilecontrol.app.domain.repository.DashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class DashboardRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val dashboardDao: DashboardDao,
    private val settingsDataStore: SettingsDataStore,
) : DashboardRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun observeDashboards(): Flow<List<Dashboard>> =
        dashboardDao.observeAll().map { entities ->
            val startId = settingsDataStore.getStartDashboardId()
            entities.map { it.toDomain(startId) }
        }

    override suspend fun refreshDashboards(): Result<Unit> {
        val result = safeApiCall { apiService.getDashboards() }
        val body = result.getOrElse { return Result.failure(it) }
        dashboardDao.replaceAll(body.dashboards.map { it.toEntity() })
        return Result.success(Unit)
    }

    override suspend fun getDashboard(id: String): Dashboard? {
        val startId = settingsDataStore.getStartDashboardId()
        return dashboardDao.getById(id)?.toDomain(startId)
    }

    override suspend fun createDashboard(dashboard: Dashboard): Result<Dashboard> {
        val result = safeApiCall { apiService.createDashboard(dashboard.toDto()) }
        val body = result.getOrElse { return Result.failure(it) }
        dashboardDao.upsert(body.toEntity())
        return Result.success(body.toEntity().toDomain(settingsDataStore.getStartDashboardId()))
    }

    override suspend fun updateDashboard(dashboard: Dashboard): Result<Dashboard> {
        val result = safeApiCall { apiService.updateDashboard(dashboard.id, dashboard.toDto()) }
        val body = result.getOrElse { return Result.failure(it) }
        dashboardDao.upsert(body.toEntity())
        return Result.success(body.toEntity().toDomain(settingsDataStore.getStartDashboardId()))
    }

    override suspend fun deleteDashboard(id: String): Result<Unit> {
        val result = safeApiCall { apiService.deleteDashboard(id) }
        result.onSuccess { dashboardDao.delete(id) }
        return result.map { }
    }

    override suspend fun setStartDashboard(id: String) {
        settingsDataStore.setStartDashboardId(id)
    }

    override suspend fun getStartDashboardId(): String? = settingsDataStore.getStartDashboardId()

    private fun DashboardDto.toEntity() = DashboardEntity(
        id = id,
        name = name,
        revision = revision,
        layoutsJson = json.encodeToString(layouts),
    )

    private fun DashboardEntity.toDomain(startId: String?): Dashboard {
        val layouts = runCatching {
            json.decodeFromString<List<DashboardLayoutDto>>(layoutsJson)
        }.getOrDefault(emptyList())
        return Dashboard(
            id = id,
            name = name,
            revision = revision,
            layouts = layouts.map { it.toDomain() },
            isStartDashboard = id == startId,
        )
    }

    private fun DashboardLayoutDto.toDomain() = DashboardLayout(
        sizeClass = SizeClass.fromWireName(sizeClass),
        columns = columns,
        widgets = widgets.map { it.toDomain() },
    )

    private fun WidgetDto.toDomain() = Widget(
        id = id,
        objectId = objectId,
        type = WidgetType.fromWireName(type),
        title = title,
        x = x,
        y = y,
        w = w,
        h = h,
        config = config,
    )

    private fun Dashboard.toDto() = DashboardDto(
        id = id,
        name = name,
        revision = revision,
        layouts = layouts.map { layout ->
            DashboardLayoutDto(
                sizeClass = layout.sizeClass.wireName,
                columns = layout.columns,
                widgets = layout.widgets.map { widget ->
                    WidgetDto(
                        id = widget.id,
                        objectId = widget.objectId,
                        type = widget.type.wireName,
                        title = widget.title,
                        x = widget.x,
                        y = widget.y,
                        w = widget.w,
                        h = widget.h,
                        config = widget.config,
                    )
                },
            )
        },
    )
}
