package id.pln.securevault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import id.pln.securevault.security.AppLock
import id.pln.securevault.ui.AppRoot
import id.pln.securevault.ui.theme.SecureVaultTheme

/** FragmentActivity diperlukan oleh BiometricPrompt. */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cegah screenshot / rekam layar & sembunyikan isi di daftar Recent Apps
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        // Ikon status bar putih karena header aplikasi bergradasi gelap
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            SecureVaultTheme {
                AppRoot()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AppLock.onBackground()
    }
}
