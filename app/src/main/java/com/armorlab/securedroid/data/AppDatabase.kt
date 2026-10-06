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

    @Insert
    suspend fun insertAll(records: List<ScanRecordEntity>)

    @Query("DELETE FROM scan_records")
    suspend fun clear()

    @Query("SELECT * FROM scan_records WHERE packageName = :pkg ORDER BY scannedAt DESC LIMIT 1")
    suspend fun latestFor(pkg: String): ScanRecordEntity?

    @Query("SELECT * FROM scan_records ORDER BY scannedAt DESC")
    suspend fun getAll(): List<ScanRecordEntity>

    /** 首页概览只要总数:聚合查询,不再把最多 2000 行实体整表读进内存 */
    @Query("SELECT COUNT(*) FROM scan_records")
    suspend fun countAll(): Int

    /** 首页概览只要最近一次扫描时间:MAX 聚合,空表返回 null */
    @Query("SELECT MAX(scannedAt) FROM scan_records")
    suspend fun lastScannedAt(): Long?

    @Query("DELETE FROM scan_records WHERE id NOT IN " +
        "(SELECT id FROM scan_records ORDER BY scannedAt DESC LIMIT 2000)")
    suspend fun trim()

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

        fun get(context: Context): AppDatabase {
            // 缓存实例可能已经被关闭(进程内重建 Application、外部显式 close):
            // 已关闭的 RoomDatabase 再做任何查询都会抛
            // IllegalStateException: Illegal connection pointer,因此必须先判 isOpen。
            instance?.takeIf { it.isOpen }?.let { return it }
            return synchronized(this) {
                val current = instance
                if (current != null && current.isOpen) {
                    current
                } else {
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "securedroid.db"
                    ).fallbackToDestructiveMigration().build().also { instance = it }
                }
            }
        }

        /**
         * 仅供测试:关闭并丢弃进程内实例。
         *
         * Robolectric 每个用例都会重建运行环境,SQLite 连接不能跨用例复用 ——
         * 否则会抛 "IllegalStateException: Illegal connection pointer"
         * (单例仍报告 isOpen,但它的连接属于上一个运行环境)。
         */
        fun resetForTest() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
