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
        BiometricManager.BIOMETRIC_SUCCESS -> "Biometrics / device screen lock ready"
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            "No biometrics / device lock yet. Tap to set it up in phone Settings"
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This device has no biometric sensor"
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "The biometric sensor is unavailable right now, try again later"
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
            "A system security update is required (Settings → Software update)"
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "This authentication method is not supported on this device"
        else -> "Authentication unavailable (code $s)"
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
        title: String = "Unlock SecureVault",
        subtitle: String = "Verify with biometrics or your device screen lock",
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
                            onError(errorCode, "Too many attempts. Use your device screen lock.")
                        BiometricPrompt.ERROR_NO_BIOMETRICS ->
                            onError(errorCode, "No biometrics / device lock enrolled on this phone.")
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
            onError(-1, "Can't show authentication: ${e.message}")
        }
    }
}
