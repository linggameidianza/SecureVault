package id.pln.securevault.security

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Status kunci aplikasi. Aplikasi otomatis terkunci bila berada di background
 * lebih dari [AUTO_LOCK_MS]. Saat membuka pemilih file (backup/restore), panggil
 * [allowExternalActivity] agar aplikasi tidak terkunci.
 */
object AppLock {
    private const val AUTO_LOCK_MS = 30_000L

    var unlocked by mutableStateOf(false)
        private set

    private var backgroundAt = 0L
    private var allowExternal = false

    fun unlock() {
        unlocked = true
    }

    fun lock() {
        unlocked = false
    }

    fun allowExternalActivity() {
        allowExternal = true
    }

    fun onBackground() {
        backgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        if (!unlocked || backgroundAt == 0L) return
        val elapsed = SystemClock.elapsedRealtime() - backgroundAt
        if (allowExternal) {
            allowExternal = false
            if (elapsed < 5 * 60_000L) return
        }
        if (elapsed > AUTO_LOCK_MS) lock()
    }
}
