package id.pln.securevault.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

data class TaskWithSubtasks(val task: TaskEntity, val subtasks: List<SubtaskEntity>) {
    val doneCount: Int get() = subtasks.count { it.done }
}

data class SubtaskDraft(val title: String, val done: Boolean = false)

data class TaskForm(
    val id: Long = 0,
    val title: String = "",
    val notes: String = "",
    val reminderAt: Long? = null,
    val done: Boolean = false,
    val createdAt: Long = 0,
    val completedAt: Long? = null,
    val subtasks: List<SubtaskDraft> = emptyList()
)

/** Representasi tugas untuk backup. */
data class TaskPlain(
    val title: String,
    val notes: String,
    val reminderAt: Long?,
    val done: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val subtasks: List<SubtaskDraft>
)

class TaskRepository(private val db: AppDatabase) {
    private val dao = db.taskDao()

    val tasks: Flow<List<TaskWithSubtasks>> =
        combine(dao.observeTasks(), dao.observeSubtasks()) { tasks, subs ->
            val grouped = subs.groupBy { it.taskId }
            tasks.map { TaskWithSubtasks(it, grouped[it.id].orEmpty()) }
        }

    suspend fun load(id: Long): TaskForm? = withContext(Dispatchers.IO) {
        val t = dao.getTask(id) ?: return@withContext null
        TaskForm(
            id = t.id,
            title = t.title,
            notes = t.notes,
            reminderAt = t.reminderAt,
            done = t.done,
            createdAt = t.createdAt,
            completedAt = t.completedAt,
            subtasks = dao.getSubtasks(id).map { SubtaskDraft(it.title, it.done) }
        )
    }

    suspend fun getTask(id: Long): TaskEntity? = withContext(Dispatchers.IO) { dao.getTask(id) }

    suspend fun getSubtasks(taskId: Long): List<SubtaskEntity> =
        withContext(Dispatchers.IO) { dao.getSubtasks(taskId) }

    suspend fun pendingReminders(now: Long = System.currentTimeMillis()): List<TaskEntity> =
        withContext(Dispatchers.IO) { dao.pendingReminders(now) }

    /** Simpan tugas beserta subtask-nya. Mengembalikan entitas tersimpan. */
    suspend fun save(form: TaskForm): TaskEntity = withContext(Dispatchers.IO) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val subs = form.subtasks.filter { it.title.isNotBlank() }
            val entity = TaskEntity(
                id = form.id,
                title = form.title.trim(),
                notes = form.notes.trim(),
                reminderAt = form.reminderAt,
                done = form.done,
                createdAt = if (form.id == 0L || form.createdAt == 0L) now else form.createdAt,
                updatedAt = now,
                completedAt = if (form.done) (form.completedAt ?: now) else null
            )
            val id = dao.saveTask(entity)
            dao.deleteSubtasksOf(id)
            dao.insertSubtasks(
                subs.mapIndexed { i, s -> SubtaskEntity(taskId = id, title = s.title.trim(), done = s.done, position = i) }
            )
            entity.copy(id = id)
        }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            dao.deleteSubtasksOf(id)
            dao.deleteTask(id)
        }
    }

    suspend fun setTaskDone(id: Long, done: Boolean): TaskEntity? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.setTaskDone(id, done, if (done) now else null, now)
        dao.getTask(id)
    }

    /**
     * Centang/batal centang subtask. Bila semua subtask selesai, tugas otomatis ditandai selesai;
     * bila ada yang dibatalkan, tugas dibuka kembali. Mengembalikan tugas induk terbaru.
     */
    suspend fun setSubtaskDone(sub: SubtaskEntity, done: Boolean): TaskEntity? = withContext(Dispatchers.IO) {
        db.withTransaction {
            dao.setSubtaskDone(sub.id, done)
            val parent = dao.getTask(sub.taskId) ?: return@withTransaction null
            val all = dao.getSubtasks(sub.taskId)
            val allDone = all.isNotEmpty() && all.all { it.done }
            val now = System.currentTimeMillis()
            if (allDone && !parent.done) dao.setTaskDone(parent.id, true, now, now)
            if (!done && parent.done) dao.setTaskDone(parent.id, false, null, now)
            dao.getTask(sub.taskId)
        }
    }

    // ---------- Backup / Restore ----------

    suspend fun exportAll(): List<TaskPlain> = withContext(Dispatchers.IO) {
        val subs = dao.getAllSubtasks().groupBy { it.taskId }
        dao.getAllTasks().map { t ->
            TaskPlain(
                t.title, t.notes, t.reminderAt, t.done, t.createdAt, t.updatedAt, t.completedAt,
                subs[t.id].orEmpty().map { SubtaskDraft(it.title, it.done) }
            )
        }
    }

    /** @return jumlah tugas yang ditambahkan. */
    suspend fun importAll(items: List<TaskPlain>, replace: Boolean): Int = withContext(Dispatchers.IO) {
        db.withTransaction {
            val existing = HashSet<String>()
            if (replace) {
                dao.deleteAllSubtasks()
                dao.deleteAllTasks()
            } else {
                dao.getAllTasks().forEach { existing += key(it.title, it.createdAt) }
            }
            var added = 0
            for (p in items) {
                if (!existing.add(key(p.title, p.createdAt))) continue
                val id = dao.saveTask(
                    TaskEntity(
                        title = p.title, notes = p.notes, reminderAt = p.reminderAt, done = p.done,
                        createdAt = p.createdAt, updatedAt = p.updatedAt, completedAt = p.completedAt
                    )
                )
                dao.insertSubtasks(
                    p.subtasks.mapIndexed { i, s -> SubtaskEntity(taskId = id, title = s.title, done = s.done, position = i) }
                )
                added++
            }
            added
        }
    }

    private fun key(title: String, createdAt: Long) = "${title.trim().lowercase()}\u0000$createdAt"
}
