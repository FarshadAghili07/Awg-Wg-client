package com.network.awg.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
    @Query("SELECT * FROM configs ORDER BY id DESC")
    fun getAllConfigs(): Flow<List<ConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ConfigEntity)

    @Transaction
    suspend fun selectConfig(targetId: Long) {
        clearSelection()
        setSelected(targetId)
    }

    @Query("UPDATE configs SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE configs SET isSelected = 1 WHERE id = :targetId")
    suspend fun setSelected(targetId: Long)

    @Query("SELECT * FROM configs WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedConfig(): ConfigEntity?

    @Delete
    suspend fun delete(config: ConfigEntity)
}

