package id.ichio.securevault.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.ichio.securevault.MainActivity
import id.ichio.securevault.R
import id.ichio.securevault.SecureVaultApp
import id.ichio.securevault.data.CredentialEntity
import id.ichio.securevault.util.Expiry
import java.util.concurrent.TimeUnit

/**
 * Mengecek password yang akan kedaluwarsa (≤ 2 hari) atau sudah kedaluwarsa
 * lalu menampilkan notifikasi. Setiap kondisi hanya dinotifikasi sekali per tanggal kedaluwarsa.
 */
class ExpiryNotifier(private val context: Context) {

    private val prefs = context.getSharedPreferences("securevault_notif", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            if (value) runNow(context) else NotificationManagerCompat.from(context).cancel(NOTIF_ID)
        }

    var permissionAsked: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED, value).apply()

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Password expiry",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders for passwords that are expiring or have expired"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun check(items: List<CredentialEntity>, now: Long = System.currentTimeMillis()) {
        if (!enabled) return
        val notified = prefs.getStringSet(KEY_NOTIFIED, emptySet())!!.toMutableSet()
        val validKeys = mutableSetOf<String>()
        val soon = mutableListOf<CredentialEntity>()
        val expired = mutableListOf<CredentialEntity>()

        for (c in items) {
            val exp = c.expiresAt ?: continue
            when (Expiry.status(exp, now)) {
                Expiry.Status.SOON -> {
                    val key = "${c.id}:$exp:soon"
                    validKeys += key
                    if (key !in notified) soon += c
                }
                Expiry.Status.EXPIRED -> {
                    val key = "${c.id}:$exp:expired"
                    validKeys += key
                    validKeys += "${c.id}:$exp:soon"
                    if (key !in notified) expired += c
                }
                else -> Unit
            }
        }

        if ((soon.isNotEmpty() || expired.isNotEmpty()) && post(soon, expired, now)) {
            soon.forEach { notified += "${it.id}:${it.expiresAt}:soon" }
            expired.forEach {
                notified += "${it.id}:${it.expiresAt}:expired"
                notified += "${it.id}:${it.expiresAt}:soon"
            }
        }
        // Buang penanda lama (akun dihapus / tanggal diganti)
        prefs.edit().putStringSet(KEY_NOTIFIED, notified.intersect(validKeys)).apply()
    }

    private fun post(soon: List<CredentialEntity>, expired: List<CredentialEntity>, now: Long): Boolean {
        if (!hasPermission() || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        val lines = soon.map { c ->
            val exp = c.expiresAt!!
            val left = Expiry.daysLeft(exp, now)
            val whenText = if (exp - now < Expiry.DAY_MS) "less than 1 day" else "$left days"
            "${c.title}: expires in $whenText"
        } + expired.map { c -> "${c.title}: has expired" }

        val total = soon.size + expired.size
        val title = when {
            total == 1 && soon.size == 1 -> "Password expiring soon"
            total == 1 -> "Password has expired"
            else -> "$total passwords need changing"
        }
        val text = if (total == 1) lines.first() else lines.take(2).joinToString("; ")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val style = NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach { s.addLine(it) } }
        if (lines.size > 6) style.setSummaryText("+${lines.size - 6} lainnya")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_lock)
                    .setContentTitle("SecureVault")
                    .setContentText("Some passwords need changing")
                    .build()
            )
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        const val CHANNEL_ID = "password_expiry"
        private const val NOTIF_ID = 1001
        private const val KEY_ENABLED = "expiry_notif_enabled"
        private const val KEY_ASKED = "notif_permission_asked"
        private const val KEY_NOTIFIED = "notified_keys"
        private const val WORK_PERIODIC = "expiry_check_periodic"
        private const val WORK_NOW = "expiry_check_now"

        /** Pengecekan berkala tiap 6 jam (tetap berjalan walau aplikasi ditutup). */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ExpiryCheckWorker>(6, TimeUnit.HOURS).build()
            )
        }

        /** Pengecekan segera (mis. setelah menyimpan akun atau restore). */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ExpiryCheckWorker>().build()
            )
        }
    }
}

class ExpiryCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SecureVaultApp
        return try {
            app.expiryNotifier.check(app.repository.credentialsWithExpiry())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
