package id.ichio.securevault.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.ichio.securevault.SecureVaultApp
import id.ichio.securevault.breach.PwnedClient
import id.ichio.securevault.crypto.VaultKeyInvalidatedException
import id.ichio.securevault.crypto.VaultLockedException
import id.ichio.securevault.security.AppLock
import id.ichio.securevault.data.CredentialEntity
import id.ichio.securevault.data.CredentialForm
import id.ichio.securevault.data.NoteEntity
import id.ichio.securevault.data.NoteForm
import id.ichio.securevault.notification.ExpiryNotifier
import id.ichio.securevault.data.SubtaskEntity
import id.ichio.securevault.data.TaskEntity
import id.ichio.securevault.data.TaskForm
import id.ichio.securevault.data.TaskWithSubtasks
import id.ichio.securevault.notification.TaskReminderScheduler
import id.ichio.securevault.util.TaskTime
import id.ichio.securevault.util.Expiry
import id.ichio.securevault.util.ClipboardHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app as SecureVaultApp
    private val repository = container.repository
    private val backupManager = container.backupManager
    private val taskRepository = container.taskRepository

    // ---------- Tugas ----------
    /** false = tampilkan tugas aktif, true = tampilkan tugas selesai. */
    val showDoneTasks = MutableStateFlow(false)

    private val allTasks: StateFlow<List<TaskWithSubtasks>> =
        taskRepository.tasks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tasks: StateFlow<List<TaskWithSubtasks>> =
        combine(allTasks, showDoneTasks) { list, done -> list.filter { it.task.done == done } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeTaskCount: StateFlow<Int> = allTasks.map { l -> l.count { !it.task.done } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val doneTaskCount: StateFlow<Int> = allTasks.map { l -> l.count { it.task.done } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val credentialQuery = MutableStateFlow("")
    /** true = hanya tampilkan password yang kedaluwarsa / akan kedaluwarsa (≤ 2 hari). */
    val expiringOnly = MutableStateFlow(false)
    val noteQuery = MutableStateFlow("")

    val credentials: StateFlow<List<CredentialEntity>> =
        combine(repository.credentials, credentialQuery, expiringOnly) { list, q, onlyExpiring ->
            list.filter { !onlyExpiring || Expiry.needsAttention(it.expiresAt) }
                .filter {
                    q.isBlank() ||
                        it.title.contains(q, ignoreCase = true) ||
                        it.username.contains(q, ignoreCase = true) ||
                        it.url.contains(q, ignoreCase = true)
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val credentialCount: StateFlow<Int> =
        repository.credentials.map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val noteCount: StateFlow<Int> =
        repository.notes.map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Jumlah password yang kedaluwarsa / akan kedaluwarsa dalam 2 hari. */
    val attentionCount: StateFlow<Int> =
        repository.credentials.map { list -> list.count { Expiry.needsAttention(it.expiresAt) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val notes: StateFlow<List<NoteEntity>> =
        combine(repository.notes, noteQuery) { list, q ->
            if (q.isBlank()) list else list.filter { it.title.contains(q, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    var busy by mutableStateOf(false)
        private set

    init {
        ExpiryNotifier.runNow(app)
        viewModelScope.launch { TaskReminderScheduler.rescheduleAll(app) }
    }

    private fun recheckExpiry() = ExpiryNotifier.runNow(getApplication())

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    // ---------- Penanganan kunci vault (auth-bound) ----------

    /** Jendela autentikasi kunci habis saat sesi berjalan -> kunci ulang agar pengguna verifikasi lagi. */
    private fun onVaultLocked() {
        AppLock.lock()
        message("Vault session ended. Verify again to continue.")
    }

    /** Kunci dibatalkan permanen (biometrik/kunci layar berubah). */
    private fun onVaultInvalidated() {
        message("The vault key is no longer valid (biometrics/screen lock changed). Restore from a backup.")
    }

    // ---------- Clipboard ----------

    fun copyText(label: String, text: String, sensitive: Boolean = false) {
        if (text.isEmpty()) {
            message("$label is empty")
            return
        }
        ClipboardHelper.copy(getApplication(), label, text, sensitive)
        message(
            if (sensitive) "$label copied (auto-cleared in ${ClipboardHelper.CLEAR_SECONDS} seconds)"
            else "$label copied"
        )
    }

    fun copyPassword(id: Long) {
        viewModelScope.launch {
            try {
                val pwd = repository.decryptPassword(id)
                if (pwd != null) copyText("Password", pwd, sensitive = true)
            } catch (e: VaultLockedException) {
                onVaultLocked()
            } catch (e: VaultKeyInvalidatedException) {
                onVaultInvalidated()
            }
        }
    }

    // ---------- Akun ----------

    suspend fun loadCredential(id: Long): CredentialForm =
        if (id <= 0) CredentialForm()
        else try {
            repository.loadCredential(id) ?: CredentialForm()
        } catch (e: VaultLockedException) {
            onVaultLocked(); CredentialForm()
        } catch (e: VaultKeyInvalidatedException) {
            onVaultInvalidated(); CredentialForm()
        }

    fun saveCredential(form: CredentialForm, onDone: () -> Unit) {
        if (form.title.isBlank()) {
            message("Account / service name is required")
            return
        }
        viewModelScope.launch {
            try {
                repository.saveCredential(form)
                recheckExpiry()
                message("Account saved")
                onDone()
            } catch (e: VaultLockedException) {
                onVaultLocked()
            } catch (e: VaultKeyInvalidatedException) {
                onVaultInvalidated()
            }
        }
    }

    fun deleteCredential(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteCredential(id)
            recheckExpiry()
            message("Account deleted")
            onDone()
        }
    }

    // ---------- Catatan ----------

    suspend fun loadNote(id: Long): NoteForm =
        if (id <= 0) NoteForm()
        else try {
            repository.loadNote(id) ?: NoteForm()
        } catch (e: VaultLockedException) {
            onVaultLocked(); NoteForm()
        } catch (e: VaultKeyInvalidatedException) {
            onVaultInvalidated(); NoteForm()
        }

    fun saveNote(form: NoteForm, onDone: () -> Unit) {
        if (form.title.isBlank() && form.content.isBlank()) {
            message("Empty note not saved")
            onDone()
            return
        }
        val fixed = if (form.title.isBlank()) {
            form.copy(title = form.content.lineSequence().first().take(40).ifBlank { "Untitled" })
        } else form
        viewModelScope.launch {
            try {
                repository.saveNote(fixed)
                message("Note saved")
                onDone()
            } catch (e: VaultLockedException) {
                onVaultLocked()
            } catch (e: VaultKeyInvalidatedException) {
                onVaultInvalidated()
            }
        }
    }

    fun deleteNote(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteNote(id)
            message("Note deleted")
            onDone()
        }
    }

    // ---------- Backup / Restore ----------

    fun backup(uri: Uri, password: String) {
        viewModelScope.launch {
            busy = true
            try {
                val count = backupManager.backup(uri, password.toCharArray())
                message("Backup complete: $count items saved")
            } catch (e: VaultLockedException) {
                onVaultLocked()
            } catch (e: VaultKeyInvalidatedException) {
                onVaultInvalidated()
            } catch (e: Exception) {
                message("Backup failed: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    fun restore(uri: Uri, password: String, replace: Boolean) {
        viewModelScope.launch {
            busy = true
            try {
                val r = backupManager.restore(uri, password.toCharArray(), replace)
                recheckExpiry()
                TaskReminderScheduler.rescheduleAll(getApplication())
                val skipped = if (r.skipped > 0) ", ${r.skipped} duplicates skipped" else ""
                message("Restore complete: ${r.credentialsAdded} accounts, ${r.notesAdded} notes, ${r.tasksAdded} tasks$skipped")
            } catch (e: VaultLockedException) {
                onVaultLocked()
            } catch (e: VaultKeyInvalidatedException) {
                onVaultInvalidated()
            } catch (e: IllegalArgumentException) {
                message(e.message ?: "Invalid backup file")
            } catch (e: Exception) {
                message("Restore failed: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    // ---------- Tugas: aksi ----------

    suspend fun loadTask(id: Long): TaskForm =
        if (id <= 0) TaskForm() else taskRepository.load(id) ?: TaskForm()

    fun saveTask(form: TaskForm, onDone: () -> Unit) {
        if (form.title.isBlank()) {
            message("Task title is required")
            return
        }
        viewModelScope.launch {
            val saved = taskRepository.save(form)
            TaskReminderScheduler.sync(getApplication(), saved)
            val at = saved.reminderAt
            message(
                when {
                    at != null && !saved.done && at <= System.currentTimeMillis() ->
                        "Task saved, but the reminder time has already passed"
                    at != null && !saved.done -> "Task saved • reminder ${TaskTime.relative(at)}"
                    else -> "Task saved"
                }
            )
            onDone()
        }
    }

    fun deleteTask(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            TaskReminderScheduler.cancel(getApplication(), id)
            taskRepository.delete(id)
            message("Task deleted")
            onDone()
        }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            val updated = taskRepository.setTaskDone(task.id, !task.done) ?: return@launch
            TaskReminderScheduler.sync(getApplication(), updated)
            if (updated.done) message("✔ \"${updated.title}\" completed")
        }
    }

    fun toggleSubtask(sub: SubtaskEntity) {
        viewModelScope.launch {
            val parent = taskRepository.setSubtaskDone(sub, !sub.done) ?: return@launch
            TaskReminderScheduler.sync(getApplication(), parent)
            if (parent.done && !sub.done) message("🎉 All subtasks completed — \"${parent.title}\" marked as done")
        }
    }

    // ---------- Cek kebocoran (Have I Been Pwned) ----------

    data class VaultLeak(val id: Long, val title: String, val username: String, val count: Int)

    /**
     * Memeriksa semua password tersimpan ke Pwned Passwords (k-anonymity).
     * Password yang sama hanya diperiksa sekali.
     */
    suspend fun scanSavedPasswords(onProgress: (done: Int, total: Int) -> Unit): Pair<Int, List<VaultLeak>> {
        val items = try {
            repository.passwordsForAudit().filter { it.password.isNotEmpty() }
        } catch (e: VaultLockedException) {
            onVaultLocked(); return 0 to emptyList()
        } catch (e: VaultKeyInvalidatedException) {
            onVaultInvalidated(); return 0 to emptyList()
        }
        val cache = HashMap<String, Int>()
        val leaks = mutableListOf<VaultLeak>()
        items.forEachIndexed { i, item ->
            val count = cache.getOrPut(item.password) { PwnedClient.passwordPwnCount(item.password) }
            if (count > 0) leaks += VaultLeak(item.id, item.title, item.username, count)
            onProgress(i + 1, items.size)
        }
        return items.size to leaks.sortedByDescending { it.count }
    }
}
