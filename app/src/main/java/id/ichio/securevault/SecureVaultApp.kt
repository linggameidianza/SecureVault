package id.ichio.securevault

import android.app.Application
import id.ichio.securevault.backup.BackupManager
import id.ichio.securevault.crypto.CryptoManager
import id.ichio.securevault.crypto.LegacyCryptoManager
import id.ichio.securevault.crypto.VaultLockedException
import id.ichio.securevault.crypto.VaultMigration
import id.ichio.securevault.data.AppDatabase
import id.ichio.securevault.data.TaskRepository
import id.ichio.securevault.data.VaultRepository
import id.ichio.securevault.notification.ExpiryNotifier
import id.ichio.securevault.notification.TaskNotifier

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
