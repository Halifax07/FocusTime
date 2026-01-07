package com.example.delayme.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import com.example.delayme.data.model.AppConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM app_configs")
    fun getAllConfigs(): Flow<List<AppConfig>>

    @Query("SELECT * FROM app_configs WHERE packageName = :packageName")
    suspend fun getConfig(packageName: String): AppConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: AppConfig)

    @Query("DELETE FROM app_configs WHERE packageName = :packageName")
    suspend fun deleteConfig(packageName: String)
}

@Database(entities = [AppConfig::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
}
