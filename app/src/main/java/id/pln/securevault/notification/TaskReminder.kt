package id.pln.securevault.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import id.pln.securevault.MainActivity
import id.pln.securevault.R
import id.pln.securevault.SecureVaultApp
import id.pln.securevault.data.SubtaskEntity
import id.pln.securevault.data.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Menjadwalkan pengingat tugas dengan AlarmManager (tetap jalan walau aplikasi ditutup). */
object TaskReminderScheduler {
    const val ACTION_REMIND = "id.pln.securevault.action.TASK_REMIND"
    const val ACTION_DONE = "id.pln.securevault.action.TASK_DONE"
    const val EXTRA_TASK_ID = "task_id"

    fun notificationId(taskId: Long): Int = 10_000 + (taskId % 1_000_000).toInt()

    private fun pending(context: Context, taskId: Long, action: String): PendingIntent {
        val intent = Intent(context, TaskReminderReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_TASK_ID, taskId)
        val requestCode = (taskId % 1_000_000).toInt() * 2 + if (action == ACTION_DONE) 1 else 0
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun doneIntent(context: Context, taskId: Long): PendingIntent = pending(context, taskId, ACTION_DONE)

    /** Jadwalkan (atau batalkan) pengingat sesuai status tugas. */
    fun sync(context: Context, task: TaskEntity) {
        val at = task.reminderAt
        if (task.done || at == null || at <= System.currentTimeMillis()) {
            cancel(context, task.id, clearNotification = task.done)
        } else {
            schedule(context, task.id, at)
        }
    }

    fun schedule(context: Context, taskId: Long, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, taskId, ACTION_REMIND)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, taskId: Long, clearNotification: Boolean = true) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, taskId, ACTION_REMIND))
        if (clearNotification) NotificationManagerCompat.from(context).cancel(notificationId(taskId))
    }

    /** Dipanggil saat HP dinyalakan ulang / aplikasi dibuka / setelah restore. */
    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext as SecureVaultApp
        app.taskRepository.pendingReminders().forEach { t ->
            t.reminderAt?.let { schedule(context, t.id, it) }
        }
    }
}

object TaskNotifier {
    const val CHANNEL_ID = "task_reminder"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Pengingat tugas", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pengingat untuk tugas yang Anda jadwalkan"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun show(context: Context, task: TaskEntity, subtasks: List<SubtaskEntity>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = when {
            subtasks.isNotEmpty() ->
                "${subtasks.count { it.done }}/${subtasks.size} subtask selesai"
            task.notes.isNotBlank() -> task.notes.lineSequence().first()
            else -> "Saatnya mengerjakan tugas ini"
        }
        val style = NotificationCompat.InboxStyle()
        subtasks.take(6).forEach { style.addLine((if (it.done) "☑ " else "☐ ") + it.title) }
        if (subtasks.size > 6) style.setSummaryText("+${subtasks.size - 6} subtask lainnya")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle("⏰ ${task.title}")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "Tandai selesai", TaskReminderScheduler.doneIntent(context, task.id))
        if (subtasks.isNotEmpty()) builder.setStyle(style)
        try {
            NotificationManagerCompat.from(context)
                .notify(TaskReminderScheduler.notificationId(task.id), builder.build())
        } catch (e: SecurityException) {
            // izin notifikasi dicabut
        }
    }
}

/** Menerima alarm pengingat & aksi "Tandai selesai" dari notifikasi. */
class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(TaskReminderScheduler.EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        val app = context.applicationContext as SecureVaultApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    TaskReminderScheduler.ACTION_REMIND -> {
                        val task = app.taskRepository.getTask(taskId)
                        val at = task?.reminderAt
                        // Abaikan alarm usang (mis. tugas sudah dihapus/diubah waktunya)
                        if (task != null && !task.done && at != null && at <= System.currentTimeMillis() + 60_000L) {
                            TaskNotifier.show(context, task, app.taskRepository.getSubtasks(taskId))
                        }
                    }
                    TaskReminderScheduler.ACTION_DONE -> {
                        app.taskRepository.setTaskDone(taskId, true)
                        TaskReminderScheduler.cancel(context, taskId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Menjadwalkan ulang semua pengingat setelah HP restart atau aplikasi diperbarui. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                TaskReminderScheduler.rescheduleAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
