package com.mobilecontrol.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mobilecontrol.app.data.local.entity.StateCacheEntity

@Dao
interface StateCacheDao {
    @Query("SELECT * FROM state_cache WHERE objectId IN (:objectIds)")
    suspend fun getFor(objectIds: List<String>): List<StateCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<StateCacheEntity>)

    @Query("DELETE FROM state_cache")
    suspend fun clear()

    /** Evicts entries whose value hasn't changed since before [cutoffMillis] (simple time-based sweep, not LRU). */
    @Query("DELETE FROM state_cache WHERE lastChange < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
