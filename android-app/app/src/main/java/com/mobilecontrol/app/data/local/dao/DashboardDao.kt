package com.mobilecontrol.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mobilecontrol.app.data.local.entity.DashboardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DashboardDao {
    @Query("SELECT * FROM dashboards ORDER BY name")
    fun observeAll(): Flow<List<DashboardEntity>>

    @Query("SELECT * FROM dashboards WHERE id = :id")
    suspend fun getById(id: String): DashboardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DashboardEntity)

    @Query("DELETE FROM dashboards WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM dashboards")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(entities: List<DashboardEntity>) {
        clear()
        entities.forEach { upsert(it) }
    }
}
