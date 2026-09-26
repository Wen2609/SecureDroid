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

    @Query("SELECT COUNT(*) FROM scan_records WHERE threatName IS NOT NULL")
    suspend fun threatCount(): Int
}

@Database(entities = [ScanRecordEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scanRecordDao(): ScanRecordDao

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
