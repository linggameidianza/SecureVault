package id.pln.securevault.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Password & catatan akun disimpan terenkripsi (kolom *Enc). */
@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val username: String,
    val passwordEnc: String,
    val url: String,
    val noteEnc: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Waktu kedaluwarsa password (epoch ms), null = tidak ada masa berlaku. */
    val expiresAt: Long? = null,
    /** Interval ganti password dalam hari (0 = tanggal manual / tidak ada). */
    @ColumnInfo(defaultValue = "0") val expiryDays: Int = 0
)

/** Isi catatan disimpan terenkripsi. */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val contentEnc: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials ORDER BY title COLLATE NOCASE")
    suspend fun getAll(): List<CredentialEntity>

    @Query("SELECT * FROM credentials WHERE id = :id")
    suspend fun getById(id: Long): CredentialEntity?

    @Query("SELECT * FROM credentials WHERE expiresAt IS NOT NULL")
    suspend fun getWithExpiry(): List<CredentialEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: CredentialEntity): Long

    @Insert
    suspend fun insertAll(items: List<CredentialEntity>)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM credentials")
    suspend fun deleteAll()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: NoteEntity): Long

    @Insert
    suspend fun insertAll(items: List<NoteEntity>)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}

/** Tugas (to-do) dengan pengingat opsional. */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val reminderAt: Long? = null,
    val done: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null
)

/** Subtask / checklist milik sebuah tugas. */
@Entity(tableName = "subtasks")
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val done: Boolean = false,
    val position: Int = 0
)

@Dao
interface TaskDao {
    @Query(
        "SELECT * FROM tasks ORDER BY done ASC, " +
            "CASE WHEN reminderAt IS NULL THEN 1 ELSE 0 END, reminderAt ASC, createdAt DESC"
    )
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM subtasks ORDER BY taskId, position")
    fun observeSubtasks(): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: Long): TaskEntity?

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position")
    suspend fun getSubtasks(taskId: Long): List<SubtaskEntity>

    @Query("SELECT * FROM tasks ORDER BY createdAt")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM subtasks ORDER BY taskId, position")
    suspend fun getAllSubtasks(): List<SubtaskEntity>

    @Query("SELECT * FROM tasks WHERE done = 0 AND reminderAt IS NOT NULL AND reminderAt > :now")
    suspend fun pendingReminders(now: Long): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTask(task: TaskEntity): Long

    @Insert
    suspend fun insertSubtasks(items: List<SubtaskEntity>)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteSubtasksOf(taskId: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("UPDATE tasks SET done = :done, completedAt = :completedAt, updatedAt = :now WHERE id = :id")
    suspend fun setTaskDone(id: Long, done: Boolean, completedAt: Long?, now: Long)

    @Query("UPDATE subtasks SET done = :done WHERE id = :id")
    suspend fun setSubtaskDone(id: Long, done: Boolean)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("DELETE FROM subtasks")
    suspend fun deleteAllSubtasks()
}

@Database(
    entities = [CredentialEntity::class, NoteEntity::class, TaskEntity::class, SubtaskEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun credentialDao(): CredentialDao
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE credentials ADD COLUMN expiresAt INTEGER")
                db.execSQL("ALTER TABLE credentials ADD COLUMN expiryDays INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `notes` TEXT NOT NULL, `reminderAt` INTEGER, " +
                        "`done` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "`completedAt` INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`taskId` INTEGER NOT NULL, `title` TEXT NOT NULL, `done` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "securevault.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
