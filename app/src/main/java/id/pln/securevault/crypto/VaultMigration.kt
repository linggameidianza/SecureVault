package id.pln.securevault.crypto

import android.content.Context
import androidx.room.withTransaction
import id.pln.securevault.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Migrasi satu kali: re-enkripsi semua data dari kunci lama (tanpa ikatan auth) ke kunci v2
 * (auth-bound) milik [CryptoManager].
 *
 * URUTAN PENTING:
 *  1. `legacy.decrypt(...)` tidak butuh prompt (kunci lama tidak auth-bound).
 *  2. `v2.encrypt(...)` BUTUH kunci v2 yang terbuka → jalankan SETELAH pengguna lolos autentikasi
 *     pertama (saat jendela validitas kunci v2 masih aktif). Bila jendela keburu habis,
 *     `VaultLockedException` dilempar; pemanggil menangkapnya dan mencoba lagi pada unlock berikutnya
 *     (flag selesai belum di-set, jadi migrasi aman diulang / idempoten untuk sisa data).
 *
 * Idempoten: bila sebagian data sudah v2 dan sebagian masih lama, re-enkripsi ulang baris yang
 * berhasil didekripsi legacy; baris yang sudah v2 akan GAGAL legacy.decrypt (AEAD tag) dan dilewati.
 */
object VaultMigration {

    private const val PREFS = "securevault_migration"
    private const val FLAG_DONE = "v2_done"
    private const val HIBP_PREFS = "securevault_hibp"
    private const val HIBP_KEY = "hibp_api_key"

    fun isNeeded(context: Context, legacy: LegacyCryptoManager): Boolean {
        val done = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(FLAG_DONE, false)
        return !done && legacy.hasLegacyKey()
    }

    /**
     * @return jumlah record yang berhasil dienkripsi ulang.
     * @throws VaultLockedException bila kunci v2 belum/berhenti terbuka (ditangani pemanggil).
     */
    suspend fun run(
        context: Context,
        db: AppDatabase,
        v2: CryptoManager,
        legacy: LegacyCryptoManager
    ): Int = withContext(Dispatchers.IO) {
        val credDao = db.credentialDao()
        val noteDao = db.noteDao()
        var migrated = 0

        db.withTransaction {
            credDao.getAll().forEach { c ->
                // Baris yang sudah v2 gagal legacy.decrypt → lewati tanpa menggagalkan migrasi.
                val pwd = runCatching { legacy.decrypt(c.passwordEnc) }.getOrNull()
                val note = runCatching { legacy.decrypt(c.noteEnc) }.getOrNull()
                if (pwd != null && note != null) {
                    credDao.save(c.copy(passwordEnc = v2.encrypt(pwd), noteEnc = v2.encrypt(note)))
                    migrated++
                }
            }
            noteDao.getAll().forEach { n ->
                val content = runCatching { legacy.decrypt(n.contentEnc) }.getOrNull()
                if (content != null) {
                    noteDao.save(n.copy(contentEnc = v2.encrypt(content)))
                    migrated++
                }
            }
        }

        // API key HIBP: baca raw, dekripsi dengan kunci lama, enkripsi ulang dengan v2.
        val hibpPrefs = context.getSharedPreferences(HIBP_PREFS, Context.MODE_PRIVATE)
        hibpPrefs.getString(HIBP_KEY, null)?.let { enc ->
            val plain = runCatching { legacy.decrypt(enc) }.getOrNull()
            if (!plain.isNullOrBlank()) {
                hibpPrefs.edit().putString(HIBP_KEY, v2.encrypt(plain)).apply()
            }
        }

        // Selesai: hapus kunci lama & tandai, agar tidak dijalankan lagi.
        legacy.deleteLegacyKey()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(FLAG_DONE, true).apply()

        migrated
    }
}
