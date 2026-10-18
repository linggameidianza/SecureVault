package id.pln.securevault

import android.app.Application
import id.pln.securevault.backup.BackupManager
import id.pln.securevault.crypto.CryptoManager
import id.pln.securevault.crypto.LegacyCryptoManager
import id.pln.securevault.crypto.VaultLockedException
import id.pln.securevault.crypto.VaultMigration
import id.pln.securevault.data.AppDatabase
import id.pln.securevault.data.TaskRepository
import id.pln.securevault.data.VaultRepository
import id.pln.securevault.notification.ExpiryNotifier
import id.pln.securevault.notification.TaskNotifier

/** Dependency container sederhana (tanpa framework DI). */
class SecureVaultApp : Application() {
    private val database by lazy { AppDatabase.build(this) }
    val crypto by lazy { CryptoManager() }
    private val legacyCrypto by lazy { LegacyCryptoManager() }
    val repository by lazy { VaultRepository(database, crypto) }
    val taskRepository by lazy { TaskRepository(database) }
    val backupManager by lazy { BackupManager(this, repository, taskRepository) }
    val expiryNotifier by lazy { ExpiryNotifier(this) }

    override fun onCreate() {
        super.onCreate()
        expiryNotifier.createChannel()
        TaskNotifier.createChannel(this)
        ExpiryNotifier.schedule(this)
    }

    /**
     * Re-enkripsi data lama (kunci tanpa ikatan auth) ke kunci v2 (auth-bound).
     * Jalankan sekali setelah unlock pertama; aman dipanggil berulang (idempoten).
     */
    suspend fun runMigrationIfNeeded() {
        if (!VaultMigration.isNeeded(this, legacyCrypto)) return
        try {
            VaultMigration.run(this, database, crypto, legacyCrypto)
        } catch (e: VaultLockedException) {
            // Jendela kunci v2 habis sebelum migrasi selesai; dicoba lagi pada unlock berikutnya.
        }
    }
}
