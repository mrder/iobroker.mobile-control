package com.mobilecontrol.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mobilecontrol.app.data.local.entity.CatalogObjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_objects ORDER BY name")
    fun observeAll(): Flow<List<CatalogObjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CatalogObjectEntity>)

    @Query("DELETE FROM catalog_objects")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<CatalogObjectEntity>) {
        clear()
        insertAll(items)
    }
}
