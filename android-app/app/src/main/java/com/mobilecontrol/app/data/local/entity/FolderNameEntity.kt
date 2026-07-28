package com.mobilecontrol.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folder_names")
data class FolderNameEntity(
    @PrimaryKey val id: String,
    val name: String,
)
