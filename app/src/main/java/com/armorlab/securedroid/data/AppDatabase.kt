package com.armorlab.securedroid.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scan_records")
data class ScanRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val sha256: String,
    val threatName: String?,
    val riskScore: Int,
    val scannedAt: Long
)

@Dao
interface ScanRecordDao {

    @Insert
    suspend fun insert(record: ScanRecordEntity)

    @Query("DELETE FROM scan_records")
    suspend fun clear()

    @Query("SELECT * FROM scan_records WHERE packageName = :pkg ORDER BY scannedAt DESC LIMIT 1")
    suspend fun latestFor(pkg: String): ScanRecordEntity?

    @Query("SELECT * FROM scan_records ORDER BY scannedAt DESC")
    fun observeAll(): Flow<List<ScanRecordEntity>>

    @Query("SELECT * FROM scan_records ORDER BY scannedAt DESC")
    suspend fun getAll(): List<ScanRecordEntity>

    @Query("SELECT COUNT(*) FROM scan_records WHERE threatName IS NOT NULL")
    suspend fun threatCount(): Int
}

@Entity(tableName = "auto_actions")
data class AutoActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,
    val target: String,
    val reason: String,
    val success: Boolean,
    val actedAt: Long
)

@Dao
interface AutoActionDao {

    @Insert
    suspend fun insert(action: AutoActionEntity)

    @Query("SELECT * FROM auto_actions ORDER BY actedAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<AutoActionEntity>>

    @Query("SELECT COUNT(*) FROM auto_actions WHERE success = 1")
    suspend fun successCount(): Int
}

@Database(entities = [ScanRecordEntity::class, AutoActionEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scanRecordDao(): ScanRecordDao

    abstract fun autoActionDao(): AutoActionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "securedroid.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
