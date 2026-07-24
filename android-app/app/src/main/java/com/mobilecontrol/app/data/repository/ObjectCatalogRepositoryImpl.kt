package com.mobilecontrol.app.data.repository

import com.mobilecontrol.app.data.local.dao.CatalogDao
import com.mobilecontrol.app.data.local.entity.CatalogObjectEntity
import com.mobilecontrol.app.data.remote.ApiService
import com.mobilecontrol.app.data.remote.dto.ObjectDto
import com.mobilecontrol.app.data.remote.safeApiCall
import com.mobilecontrol.app.data.remote.toKotlinValue
import com.mobilecontrol.app.domain.model.ObjectCatalogItem
import com.mobilecontrol.app.domain.model.ValueType
import com.mobilecontrol.app.domain.repository.ObjectCatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObjectCatalogRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val catalogDao: CatalogDao,
) : ObjectCatalogRepository {

    // In-memory only - see ObjectCatalogRepository.observeFolderNames doc for why this
    // deliberately isn't part of the Room cache.
    private val folderNames = MutableStateFlow<Map<String, String>>(emptyMap())

    override fun observeCatalog(): Flow<List<ObjectCatalogItem>> =
        catalogDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeFolderNames(): Flow<Map<String, String>> = folderNames.asStateFlow()

    override suspend fun refreshCatalog(): Result<Unit> {
        val result = safeApiCall { apiService.getCatalog() }
        val body = result.getOrElse { return Result.failure(it) }
        catalogDao.replaceAll(body.objects.map { it.toEntity() })
        folderNames.value = body.folderNames
        return Result.success(Unit)
    }
}

private fun ObjectDto.toEntity() = CatalogObjectEntity(
    id = id,
    name = name,
    path = path,
    role = role,
    valueType = valueType ?: "unknown",
    unit = unit,
    canRead = read,
    canWrite = write,
    hasHistory = history,
    min = min,
    max = max,
    step = step,
    allowedValues = allowedValues.orEmpty().mapNotNull { it.toKotlinValue()?.toString() },
    localOnly = localOnly,
    confirmPolicy = confirmPolicy,
    suggestedWidgets = suggestedWidgets,
)

private fun CatalogObjectEntity.toDomain() = ObjectCatalogItem(
    id = id,
    name = name,
    path = path,
    role = role,
    valueType = when (valueType.lowercase()) {
        "boolean", "bool" -> ValueType.BOOLEAN
        "number" -> ValueType.NUMBER
        "string" -> ValueType.STRING
        "json" -> ValueType.JSON
        else -> ValueType.UNKNOWN
    },
    unit = unit,
    canRead = canRead,
    canWrite = canWrite,
    hasHistory = hasHistory,
    min = min,
    max = max,
    step = step,
    allowedValues = allowedValues,
    localOnly = localOnly,
    confirmPolicy = confirmPolicy,
    suggestedWidgets = suggestedWidgets,
)
