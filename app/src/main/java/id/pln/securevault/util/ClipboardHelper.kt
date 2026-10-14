package id.pln.securevault.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

object ClipboardHelper {
    const val CLEAR_SECONDS = 30
    private val handler = Handler(Looper.getMainLooper())

    /** Menyalin teks; bila [sensitive], clipboard ditandai sensitif dan dibersihkan otomatis. */
    fun copy(context: Context, label: String, text: String, sensitive: Boolean) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                // = ClipDescription.EXTRA_IS_SENSITIVE (API 33), disembunyikan dari preview keyboard
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        cm.setPrimaryClip(clip)

        if (sensitive) {
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        cm.clearPrimaryClip()
                    } else {
                        cm.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                } catch (e: Exception) {
                }
            }, CLEAR_SECONDS * 1000L)
        }
    }
}
