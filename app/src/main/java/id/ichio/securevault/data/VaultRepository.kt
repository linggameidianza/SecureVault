package id.ichio.securevault.data

import androidx.room.withTransaction
import id.ichio.securevault.crypto.CryptoManager
import id.ichio.securevault.util.Expiry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Model form (sudah didekripsi) untuk ditampilkan/diedit di UI. */
data class CredentialForm(
    val id: Long = 0,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val note: String = "",
    val createdAt: Long = 0,
    val expiresAt: Long? = null,
    val expiryDays: Int = 0
)

data class NoteForm(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val createdAt: Long = 0
)

/** Data plaintext untuk backup. */
data class VaultSnapshot(
    val credentials: List<CredentialPlain>,
    val notes: List<NotePlain>
)

data class CredentialPlain(
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long? = null,
    val expiryDays: Int = 0
)

data class NotePlain(
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)

/** Data untuk audit kebocoran password tersimpan. */
data class AuditItem(val id: Long, val title: String, val username: String, val password: String)

data class RestoreResult(
    val credentialsAdded: Int,
    val notesAdded: Int,
    val skipped: Int,
    val tasksAdded: Int = 0
)

class VaultRepository(
    private val db: AppDatabase,
    private val crypto: CryptoManager
) {
    private val credentialDao = db.credentialDao()
    private val noteDao = db.noteDao()

    val credentials: Flow<List<CredentialEntity>> = credentialDao.observeAll()
    val notes: Flow<List<NoteEntity>> = noteDao.observeAll()

    // ---------- Akun ----------

    suspend fun loadCredential(id: Long): CredentialForm? = withContext(Dispatchers.IO) {
        credentialDao.getById(id)?.let {
            CredentialForm(
                id = it.id,
                title = it.title,
                username = it.username,
                password = crypto.decrypt(it.passwordEnc),
                url = it.url,
                note = crypto.decrypt(it.noteEnc),
                createdAt = it.createdAt,
                expiresAt = it.expiresAt,
                expiryDays = it.expiryDays
            )
        }
    }

    suspend fun decryptPassword(id: Long): String? = withContext(Dispatchers.IO) {
        credentialDao.getById(id)?.let { crypto.decrypt(it.passwordEnc) }
    }

    suspend fun saveCredential(form: CredentialForm): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = if (form.id != 0L) credentialDao.getById(form.id) else null
        val passwordChanged = existing != null && crypto.decrypt(existing.passwordEnc) != form.password
        // Interval otomatis: bila password diganti, masa berlaku dihitung ulang dari sekarang
        val expiresAt = if (form.expiryDays > 0 && (passwordChanged || form.expiresAt == null)) {
            now + form.expiryDays * Expiry.DAY_MS
        } else {
            form.expiresAt
        }
        credentialDao.save(
            CredentialEntity(
                id = form.id,
                title = form.title.trim(),
                username = form.username.trim(),
                passwordEnc = crypto.encrypt(form.password),
                url = form.url.trim(),
                noteEnc = crypto.encrypt(form.note),
                createdAt = if (form.id == 0L || form.createdAt == 0L) now else form.createdAt,
                updatedAt = now,
                expiresAt = expiresAt,
                expiryDays = if (expiresAt == null) 0 else form.expiryDays
            )
        )
    }

    suspend fun passwordsForAudit(): List<AuditItem> = withContext(Dispatchers.IO) {
        credentialDao.getAll().map { AuditItem(it.id, it.title, it.username, crypto.decrypt(it.passwordEnc)) }
    }

    /** Untuk pengecekan notifikasi (tanpa dekripsi). */
    suspend fun credentialsWithExpiry(): List<CredentialEntity> = withContext(Dispatchers.IO) {
        credentialDao.getWithExpiry()
    }

    suspend fun deleteCredential(id: Long) = withContext(Dispatchers.IO) {
        credentialDao.deleteById(id)
    }

    // ---------- Catatan ----------

    suspend fun loadNote(id: Long): NoteForm? = withContext(Dispatchers.IO) {
        noteDao.getById(id)?.let {
            NoteForm(it.id, it.title, crypto.decrypt(it.contentEnc), it.createdAt)
        }
    }

    suspend fun saveNote(form: NoteForm): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        noteDao.save(
            NoteEntity(
                id = form.id,
                title = form.title.trim(),
                contentEnc = crypto.encrypt(form.content),
                createdAt = if (form.id == 0L || form.createdAt == 0L) now else form.createdAt,
                updatedAt = now
            )
        )
    }

    suspend fun deleteNote(id: Long) = withContext(Dispatchers.IO) {
        noteDao.deleteById(id)
    }

    // ---------- Backup / Restore ----------

    suspend fun snapshot(): VaultSnapshot = withContext(Dispatchers.IO) {
        VaultSnapshot(
            credentials = credentialDao.getAll().map {
                CredentialPlain(
                    it.title, it.username, crypto.decrypt(it.passwordEnc),
                    it.url, crypto.decrypt(it.noteEnc), it.createdAt, it.updatedAt,
                    it.expiresAt, it.expiryDays
                )
            },
            notes = noteDao.getAll().map {
                NotePlain(it.title, crypto.decrypt(it.contentEnc), it.createdAt, it.updatedAt)
            }
        )
    }

    /**
     * @param replace true = hapus semua data lama lalu isi dengan backup;
     *                false = gabungkan (lewati entri yang sama persis).
     */
    suspend fun restore(snapshot: VaultSnapshot, replace: Boolean): RestoreResult =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val existingCredKeys = HashSet<String>()
                val existingNoteKeys = HashSet<String>()
                if (replace) {
                    credentialDao.deleteAll()
                    noteDao.deleteAll()
                } else {
                    credentialDao.getAll().forEach {
                        existingCredKeys += credKey(it.title, it.username, crypto.decrypt(it.passwordEnc))
                    }
                    noteDao.getAll().forEach {
                        existingNoteKeys += noteKey(it.title, crypto.decrypt(it.contentEnc))
                    }
                }

                var skipped = 0
                val newCreds = snapshot.credentials.filter {
                    val ok = existingCredKeys.add(credKey(it.title, it.username, it.password))
                    if (!ok) skipped++
                    ok
                }.map {
                    CredentialEntity(
                        title = it.title,
                        username = it.username,
                        passwordEnc = crypto.encrypt(it.password),
                        url = it.url,
                        noteEnc = crypto.encrypt(it.note),
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        expiresAt = it.expiresAt,
                        expiryDays = it.expiryDays
                    )
                }
                val newNotes = snapshot.notes.filter {
                    val ok = existingNoteKeys.add(noteKey(it.title, it.content))
                    if (!ok) skipped++
                    ok
                }.map {
                    NoteEntity(
                        title = it.title,
                        contentEnc = crypto.encrypt(it.content),
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt
                    )
                }
                credentialDao.insertAll(newCreds)
                noteDao.insertAll(newNotes)
                RestoreResult(newCreds.size, newNotes.size, skipped)
            }
        }

    private fun credKey(title: String, username: String, password: String) =
        "${title.trim().lowercase()}\u0000${username.trim().lowercase()}\u0000$password"

    private fun noteKey(title: String, content: String) = "${title.trim()}\u0000$content"
}
