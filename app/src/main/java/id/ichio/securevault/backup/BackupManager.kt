package id.ichio.securevault.backup

import android.content.Context
import android.net.Uri
import id.ichio.securevault.crypto.PasswordCrypto
import id.ichio.securevault.data.CredentialPlain
import id.ichio.securevault.data.NotePlain
import id.ichio.securevault.data.RestoreResult
import id.ichio.securevault.data.SubtaskDraft
import id.ichio.securevault.data.TaskPlain
import id.ichio.securevault.data.TaskRepository
import id.ichio.securevault.data.VaultRepository
import id.ichio.securevault.data.VaultSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup = seluruh akun (username + password) dan catatan, diserialisasi ke JSON lalu
 * dienkripsi dengan password backup (PBKDF2 210.000 iterasi + AES-256-GCM).
 * File backup bisa dipulihkan di perangkat lain asalkan password backup benar.
 */
class BackupManager(
    private val context: Context,
    private val repository: VaultRepository,
    private val taskRepository: TaskRepository
) {

    suspend fun backup(uri: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        val snapshot = repository.snapshot()
        val tasks = taskRepository.exportAll()
        val json = toJson(snapshot).put("tasks", tasksToJson(tasks)).toString()
        val envelope = PasswordCrypto.encryptBackup(json.toByteArray(Charsets.UTF_8), password)
        val out = context.contentResolver.openOutputStream(uri, "w")
            ?: throw IllegalStateException("Can't write to the destination file")
        out.use { it.write(envelope.toByteArray(Charsets.UTF_8)) }
        snapshot.credentials.size + snapshot.notes.size + tasks.size
    }

    suspend fun restore(uri: Uri, password: CharArray, replace: Boolean): RestoreResult =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Can't read the backup file")
            val text = input.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size > MAX_BACKUP_BYTES) throw IllegalArgumentException("File is too large")
                String(bytes, Charsets.UTF_8)
            }
            val plain = PasswordCrypto.decryptBackup(text, password)
            val root = JSONObject(String(plain, Charsets.UTF_8))
            plain.fill(0)
            val snapshot = fromJson(root)
            val tasks = tasksFromJson(root.optJSONArray("tasks") ?: JSONArray())
            val result = repository.restore(snapshot, replace)
            val tasksAdded = taskRepository.importAll(tasks, replace)
            result.copy(tasksAdded = tasksAdded)
        }

    private fun tasksToJson(tasks: List<TaskPlain>): JSONArray {
        val arr = JSONArray()
        tasks.forEach { t ->
            val subs = JSONArray()
            t.subtasks.forEach { subs.put(JSONObject().put("title", it.title).put("done", it.done)) }
            arr.put(
                JSONObject()
                    .put("title", t.title)
                    .put("notes", t.notes)
                    .put("reminderAt", t.reminderAt ?: JSONObject.NULL)
                    .put("done", t.done)
                    .put("createdAt", t.createdAt)
                    .put("updatedAt", t.updatedAt)
                    .put("completedAt", t.completedAt ?: JSONObject.NULL)
                    .put("subtasks", subs)
            )
        }
        return arr
    }

    private fun tasksFromJson(arr: JSONArray): List<TaskPlain> {
        val now = System.currentTimeMillis()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val subs = o.optJSONArray("subtasks") ?: JSONArray()
            TaskPlain(
                title = o.optString("title"),
                notes = o.optString("notes"),
                reminderAt = if (o.isNull("reminderAt")) null else o.optLong("reminderAt"),
                done = o.optBoolean("done", false),
                createdAt = o.optLong("createdAt", now),
                updatedAt = o.optLong("updatedAt", now),
                completedAt = if (o.isNull("completedAt")) null else o.optLong("completedAt"),
                subtasks = (0 until subs.length()).map { j ->
                    val s = subs.getJSONObject(j)
                    SubtaskDraft(s.optString("title"), s.optBoolean("done", false))
                }
            )
        }
    }

    private fun toJson(s: VaultSnapshot): JSONObject {
        val creds = JSONArray()
        s.credentials.forEach {
            creds.put(
                JSONObject()
                    .put("title", it.title)
                    .put("username", it.username)
                    .put("password", it.password)
                    .put("url", it.url)
                    .put("note", it.note)
                    .put("createdAt", it.createdAt)
                    .put("updatedAt", it.updatedAt)
                    .put("expiresAt", it.expiresAt ?: JSONObject.NULL)
                    .put("expiryDays", it.expiryDays)
            )
        }
        val notes = JSONArray()
        s.notes.forEach {
            notes.put(
                JSONObject()
                    .put("title", it.title)
                    .put("content", it.content)
                    .put("createdAt", it.createdAt)
                    .put("updatedAt", it.updatedAt)
            )
        }
        return JSONObject()
            .put("app", "SecureVault")
            .put("exportedAt", System.currentTimeMillis())
            .put("credentials", creds)
            .put("notes", notes)
    }

    private fun fromJson(o: JSONObject): VaultSnapshot {
        val now = System.currentTimeMillis()
        val creds = o.optJSONArray("credentials") ?: JSONArray()
        val notes = o.optJSONArray("notes") ?: JSONArray()
        return VaultSnapshot(
            credentials = (0 until creds.length()).map { i ->
                val c = creds.getJSONObject(i)
                CredentialPlain(
                    title = c.optString("title"),
                    username = c.optString("username"),
                    password = c.optString("password"),
                    url = c.optString("url"),
                    note = c.optString("note"),
                    createdAt = c.optLong("createdAt", now),
                    updatedAt = c.optLong("updatedAt", now),
                    expiresAt = if (c.isNull("expiresAt")) null else c.optLong("expiresAt"),
                    expiryDays = c.optInt("expiryDays", 0)
                )
            },
            notes = (0 until notes.length()).map { i ->
                val n = notes.getJSONObject(i)
                NotePlain(
                    title = n.optString("title"),
                    content = n.optString("content"),
                    createdAt = n.optLong("createdAt", now),
                    updatedAt = n.optLong("updatedAt", now)
                )
            }
        )
    }

    companion object {
        private const val MAX_BACKUP_BYTES = 50 * 1024 * 1024
        const val FILE_EXTENSION = "svbk"
    }
}
