package id.ichio.securevault.security

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Gerbang autentikasi untuk membuka vault.
 *
 * PERUBAHAN KEAMANAN:
 *  - Hanya menerima BIOMETRIC_STRONG (kelas 3). BIOMETRIC_WEAK dibuang karena mudah dipalsukan
 *    dan tidak dapat melepas kunci Keystore yang terikat autentikasi kuat.
 *  - DEVICE_CREDENTIAL (PIN/pola/sandi layar perangkat) dipakai sebagai cadangan. Berbeda dari
 *    PIN aplikasi, kredensial perangkat BISA melepas kunci vault (CryptoManager auth-bound),
 *    sehingga pengguna tanpa biometrik tetap punya jalur masuk yang benar-benar menggerbangi data.
 *  - Autentikasi sukses di sini MEMBUKA kunci Keystore (jendela waktu CryptoManager.AUTH_VALIDITY_SECONDS),
 *    jadi onSuccess menandakan vault benar-benar dapat didekripsi — bukan sekadar lolos UI.
 */
object BiometricHelper {

    private const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    fun status(context: Context): Int =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)

    fun isAvailable(context: Context): Boolean = status(context) == BiometricManager.BIOMETRIC_SUCCESS

    fun needsEnrollment(context: Context): Boolean =
        status(context) == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

    fun statusMessage(context: Context): String = when (val s = status(context)) {
        BiometricManager.BIOMETRIC_SUCCESS -> "Biometrik / kunci layar siap digunakan"
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            "Belum ada biometrik / kunci layar. Ketuk untuk mengatur di Setelan HP"
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Perangkat tidak memiliki sensor biometrik"
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Sensor biometrik sedang tidak tersedia, coba lagi nanti"
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
            "Perlu pembaruan keamanan sistem (Setelan → Pembaruan perangkat lunak)"
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "Metode autentikasi ini tidak didukung di perangkat ini"
        else -> "Autentikasi tidak tersedia (kode $s)"
    }

    /** Intent untuk mendaftarkan biometrik / kunci layar di Setelan HP. */
    fun enrollIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, AUTHENTICATORS
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

    /**
     * Memunculkan prompt biometrik/kredensial. Sukses => kunci vault terbuka untuk jendela waktu
     * yang ditetapkan CryptoManager.
     *
     * Catatan: saat DEVICE_CREDENTIAL diizinkan, tombol negatif TIDAK boleh diset (dilarang API) —
     * jalur "pakai kunci layar" disediakan sistem di dalam prompt. [onCancel] dipanggil bila pengguna
     * membatalkan.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Buka SecureVault",
        subtitle: String = "Verifikasi dengan biometrik atau kunci layar",
        onSuccess: () -> Unit,
        onError: (Int, String) -> Unit = { _, _ -> },
        onCancel: () -> Unit = {}
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED -> onCancel()
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                            onError(errorCode, "Terlalu banyak percobaan. Gunakan kunci layar perangkat.")
                        BiometricPrompt.ERROR_NO_BIOMETRICS ->
                            onError(errorCode, "Belum ada biometrik / kunci layar terdaftar di HP ini.")
                        else -> onError(errorCode, errString.toString())
                    }
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(false)
            // JANGAN panggil setNegativeButtonText(...) ketika DEVICE_CREDENTIAL diizinkan.
            .build()

        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            onError(-1, "Tidak dapat menampilkan autentikasi: ${e.message}")
        }
    }
}
