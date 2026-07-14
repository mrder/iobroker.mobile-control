package com.mobilecontrol.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dashboards")
data class DashboardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val revision: Long,
    /** Serialized List<DashboardLayoutDto> as JSON - a full relational widget schema is overkill for MVP. */
    val layoutsJson: String,
)
