package id.pln.securevault

import android.app.Application
import id.pln.securevault.backup.BackupManager
import id.pln.securevault.breach.HibpKeyStore
import id.pln.securevault.crypto.CryptoManager
import id.pln.securevault.data.AppDatabase
import id.pln.securevault.data.TaskRepository
import id.pln.securevault.data.VaultRepository
import id.pln.securevault.notification.ExpiryNotifier
import id.pln.securevault.notification.TaskNotifier
import id.pln.securevault.security.PinManager

/** Dependency container sederhana (tanpa framework DI). */
class SecureVaultApp : Application() {
    val pinManager by lazy { PinManager(this) }
    private val database by lazy { AppDatabase.build(this) }
    private val crypto by lazy { CryptoManager() }
    val repository by lazy { VaultRepository(database, crypto) }
    val taskRepository by lazy { TaskRepository(database) }
    val backupManager by lazy { BackupManager(this, repository, taskRepository) }
    val expiryNotifier by lazy { ExpiryNotifier(this) }
    val hibpKeyStore by lazy { HibpKeyStore(this, crypto) }

    override fun onCreate() {
        super.onCreate()
        expiryNotifier.createChannel()
        TaskNotifier.createChannel(this)
        ExpiryNotifier.schedule(this)
    }
}
