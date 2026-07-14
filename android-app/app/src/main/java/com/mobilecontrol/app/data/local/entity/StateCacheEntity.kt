package com.mobilecontrol.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "state_cache")
data class StateCacheEntity(
    @PrimaryKey val objectId: String,
    /** Raw JSON scalar (number/bool/string) as text, decoded lazily by the repository. */
    val valueJson: String?,
    val timestamp: Long,
    val lastChange: Long,
)
