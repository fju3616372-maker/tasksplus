package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ScreenTimeLimitEntity
import com.example.data.model.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
  @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY maxCompletionMillis ASC, createdAtMillis DESC")
  fun observeActiveTasks(): Flow<List<TaskEntity>>

  @Query("SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY completedAtMillis DESC, updatedAtMillis DESC")
  fun observeCompletedTasks(): Flow<List<TaskEntity>>

  @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY maxCompletionMillis ASC, createdAtMillis DESC")
  suspend fun getActiveTasksOnce(): List<TaskEntity>

  @Query("SELECT * FROM tasks")
  suspend fun getAllTasksOnce(): List<TaskEntity>

  @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
  suspend fun getTaskById(taskId: String): TaskEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertTask(task: TaskEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertTasks(tasks: List<TaskEntity>)

  @Update
  suspend fun updateTask(task: TaskEntity)

  @Query("DELETE FROM tasks WHERE id = :taskId")
  suspend fun deleteTaskById(taskId: String)

  @Query("DELETE FROM tasks WHERE isCompleted = 1")
  suspend fun clearCompletedTasks()
}

@Dao
interface ScreenTimeDao {
  @Query("SELECT * FROM screen_time_limits ORDER BY isEnabled DESC, updatedAtMillis DESC")
  fun observeAllLimits(): Flow<List<ScreenTimeLimitEntity>>

  @Query("SELECT * FROM screen_time_limits")
  suspend fun getAllLimitsOnce(): List<ScreenTimeLimitEntity>

  @Query("SELECT * FROM screen_time_limits WHERE packageName = :packageName LIMIT 1")
  suspend fun getLimitByPackage(packageName: String): ScreenTimeLimitEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertLimit(limit: ScreenTimeLimitEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertLimits(limits: List<ScreenTimeLimitEntity>)

  @Query("DELETE FROM screen_time_limits WHERE packageName = :packageName")
  suspend fun deleteLimitByPackage(packageName: String)
}

@Database(
  entities = [TaskEntity::class, ScreenTimeLimitEntity::class],
  version = 9,
  exportSchema = false
)
abstract class TaskDatabase : RoomDatabase() {
  abstract fun taskDao(): TaskDao
  abstract fun screenTimeDao(): ScreenTimeDao

  companion object {
    @Volatile
    private var INSTANCE: TaskDatabase? = null

    val MIGRATION_5_6 = object : Migration(5, 6) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN creditsAwarded INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN creditSecondsDelta INTEGER NOT NULL DEFAULT 0")
      }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN urgencyLevel INTEGER NOT NULL DEFAULT 0")
      }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN categoryId TEXT NOT NULL DEFAULT ''")
      }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN voiceContractPath TEXT NOT NULL DEFAULT ''")
      }
    }

    fun getInstance(context: Context): TaskDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          TaskDatabase::class.java,
          "tasks_plus.db"
        )
          .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
          .fallbackToDestructiveMigration(true)
          .build()
          .also { INSTANCE = it }
      }
    }
  }
}
