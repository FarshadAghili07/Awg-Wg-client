package com.network.awg.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "configs")
data class ConfigEntity(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    val name: String,
    val rawUri: String,
    val isSelected: Boolean = false
)
