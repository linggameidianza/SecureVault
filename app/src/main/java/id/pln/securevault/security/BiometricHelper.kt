package id.pln.securevault.security

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {

    /**
     * Sidik jari (kelas kuat) MAUPUN pengenalan wajah Samsung (kelas lemah) diterima.
     * Biometrik di aplikasi ini hanya sebagai gerbang pembuka; data tetap dienkripsi Keystore.
     */
    private const val AUTHENTICATORS = BIOMETRIC_STRONG or BIOMETRIC_WEAK

    fun status(context: Context): Int =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)

    fun isAvailable(context: Context): Boolean = status(context) == BiometricManager.BIOMETRIC_SUCCESS

    fun needsEnrollment(context: Context): Boolean =
        status(context) == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

    /** Penjelasan status biometrik dalam Bahasa Indonesia. */
    fun statusMessage(context: Context): String = when (val s = status(context)) {
        BiometricManager.BIOMETRIC_SUCCESS -> "Sidik jari / wajah siap digunakan"
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            "Belum ada sidik jari terdaftar. Ketuk untuk mendaftarkan di Setelan HP"
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Perangkat tidak memiliki sensor biometrik"
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Sensor biometrik sedang tidak tersedia, coba lagi nanti"
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
            "Perlu pembaruan keamanan sistem (Setelan → Pembaruan perangkat lunak)"
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "Biometrik tidak didukung di perangkat ini"
        else -> "Biometrik tidak tersedia (kode $s)"
    }

    /** Intent untuk membuka halaman pendaftaran sidik jari di Setelan HP. */
    fun enrollIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, AUTHENTICATORS
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

    /**
     * @param onError dipanggil dengan (kode, pesan). Pembatalan oleh pengguna (tombol "Gunakan PIN")
     *                dikirim lewat [onCancel], bukan [onError].
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Buka SecureVault",
        subtitle: String = "Verifikasi dengan sidik jari atau wajah",
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
                            onError(errorCode, "Terlalu banyak percobaan biometrik. Gunakan PIN.")
                        BiometricPrompt.ERROR_NO_BIOMETRICS ->
                            onError(errorCode, "Belum ada sidik jari terdaftar di HP ini.")
                        else -> onError(errorCode, errString.toString())
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Gunakan PIN")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()
        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            onError(-1, "Tidak dapat menampilkan biometrik: ${e.message}")
        }
    }
}
