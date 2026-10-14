package id.pln.securevault.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.pln.securevault.SecureVaultApp
import id.pln.securevault.breach.PwnedClient
import id.pln.securevault.data.CredentialEntity
import id.pln.securevault.data.CredentialForm
import id.pln.securevault.data.NoteEntity
import id.pln.securevault.data.NoteForm
import id.pln.securevault.notification.ExpiryNotifier
import id.pln.securevault.data.SubtaskEntity
import id.pln.securevault.data.TaskEntity
import id.pln.securevault.data.TaskForm
import id.pln.securevault.data.TaskWithSubtasks
import id.pln.securevault.notification.TaskReminderScheduler
import id.pln.securevault.util.TaskTime
import id.pln.securevault.util.Expiry
import id.pln.securevault.util.ClipboardHelper
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

    // ---------- Clipboard ----------

    fun copyText(label: String, text: String, sensitive: Boolean = false) {
        if (text.isEmpty()) {
            message("$label kosong")
            return
        }
        ClipboardHelper.copy(getApplication(), label, text, sensitive)
        message(
            if (sensitive) "$label disalin (dihapus otomatis dalam ${ClipboardHelper.CLEAR_SECONDS} detik)"
            else "$label disalin"
        )
    }

    fun copyPassword(id: Long) {
        viewModelScope.launch {
            val pwd = repository.decryptPassword(id)
            if (pwd != null) copyText("Password", pwd, sensitive = true)
        }
    }

    // ---------- Akun ----------

    suspend fun loadCredential(id: Long): CredentialForm =
        if (id <= 0) CredentialForm() else repository.loadCredential(id) ?: CredentialForm()

    fun saveCredential(form: CredentialForm, onDone: () -> Unit) {
        if (form.title.isBlank()) {
            message("Nama akun / layanan wajib diisi")
            return
        }
        viewModelScope.launch {
            repository.saveCredential(form)
            recheckExpiry()
            message("Akun disimpan")
            onDone()
        }
    }

    fun deleteCredential(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteCredential(id)
            recheckExpiry()
            message("Akun dihapus")
            onDone()
        }
    }

    // ---------- Catatan ----------

    suspend fun loadNote(id: Long): NoteForm =
        if (id <= 0) NoteForm() else repository.loadNote(id) ?: NoteForm()

    fun saveNote(form: NoteForm, onDone: () -> Unit) {
        if (form.title.isBlank() && form.content.isBlank()) {
            message("Catatan kosong tidak disimpan")
            onDone()
            return
        }
        val fixed = if (form.title.isBlank()) {
            form.copy(title = form.content.lineSequence().first().take(40).ifBlank { "Tanpa judul" })
        } else form
        viewModelScope.launch {
            repository.saveNote(fixed)
            message("Catatan disimpan")
            onDone()
        }
    }

    fun deleteNote(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteNote(id)
            message("Catatan dihapus")
            onDone()
        }
    }

    // ---------- Backup / Restore ----------

    fun backup(uri: Uri, password: String) {
        viewModelScope.launch {
            busy = true
            try {
                val count = backupManager.backup(uri, password.toCharArray())
                message("Backup berhasil: $count item tersimpan")
            } catch (e: Exception) {
                message("Backup gagal: ${e.message}")
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
                val skipped = if (r.skipped > 0) ", ${r.skipped} duplikat dilewati" else ""
                message("Restore berhasil: ${r.credentialsAdded} akun, ${r.notesAdded} catatan, ${r.tasksAdded} tugas$skipped")
            } catch (e: IllegalArgumentException) {
                message(e.message ?: "File backup tidak valid")
            } catch (e: Exception) {
                message("Restore gagal: ${e.message}")
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
            message("Judul tugas wajib diisi")
            return
        }
        viewModelScope.launch {
            val saved = taskRepository.save(form)
            TaskReminderScheduler.sync(getApplication(), saved)
            val at = saved.reminderAt
            message(
                when {
                    at != null && !saved.done && at <= System.currentTimeMillis() ->
                        "Tugas disimpan, tetapi waktu pengingat sudah lewat"
                    at != null && !saved.done -> "Tugas disimpan • pengingat ${TaskTime.relative(at)}"
                    else -> "Tugas disimpan"
                }
            )
            onDone()
        }
    }

    fun deleteTask(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            TaskReminderScheduler.cancel(getApplication(), id)
            taskRepository.delete(id)
            message("Tugas dihapus")
            onDone()
        }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            val updated = taskRepository.setTaskDone(task.id, !task.done) ?: return@launch
            TaskReminderScheduler.sync(getApplication(), updated)
            if (updated.done) message("✔ \"${updated.title}\" selesai")
        }
    }

    fun toggleSubtask(sub: SubtaskEntity) {
        viewModelScope.launch {
            val parent = taskRepository.setSubtaskDone(sub, !sub.done) ?: return@launch
            TaskReminderScheduler.sync(getApplication(), parent)
            if (parent.done && !sub.done) message("🎉 Semua subtask selesai — \"${parent.title}\" ditandai selesai")
        }
    }

    // ---------- Cek kebocoran (Have I Been Pwned) ----------

    data class VaultLeak(val id: Long, val title: String, val username: String, val count: Int)

    /**
     * Memeriksa semua password tersimpan ke Pwned Passwords (k-anonymity).
     * Password yang sama hanya diperiksa sekali.
     */
    suspend fun scanSavedPasswords(onProgress: (done: Int, total: Int) -> Unit): Pair<Int, List<VaultLeak>> {
        val items = repository.passwordsForAudit().filter { it.password.isNotEmpty() }
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
