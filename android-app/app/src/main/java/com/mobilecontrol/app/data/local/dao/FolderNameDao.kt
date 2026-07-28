package com.mobilecontrol.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mobilecontrol.app.data.local.entity.FolderNameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderNameDao {
    @Query("SELECT * FROM folder_names")
    fun observeAll(): Flow<List<FolderNameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FolderNameEntity>)

    @Query("DELETE FROM folder_names")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<FolderNameEntity>) {
        clear()
        insertAll(items)
    }
}
