package com.culinary.spike

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "spike_entity")
data class SpikeEntity(
    @PrimaryKey val id: String,
)

@Dao
interface SpikeDao {
    @Query("SELECT * FROM spike_entity")
    suspend fun all(): List<SpikeEntity>
}

@Database(entities = [SpikeEntity::class], version = 1, exportSchema = true)
abstract class SpikeDatabase : RoomDatabase() {
    abstract fun spikeDao(): SpikeDao
}
