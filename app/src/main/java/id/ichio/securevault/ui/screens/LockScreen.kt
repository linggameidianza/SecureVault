package id.ichio.securevault.ui.screens

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.withResumed
import id.ichio.securevault.crypto.CryptoManager
import id.ichio.securevault.security.BiometricHelper
import id.ichio.securevault.ui.theme.AppGradient
import id.ichio.securevault.ui.theme.SectionCard
import kotlinx.coroutines.delay

/**
 * Layar kunci baru. Autentikasi WAJIB lewat biometrik kuat / kunci layar perangkat, karena
 * hanya itu yang melepas kunci Keystore (CryptoManager auth-bound). PIN aplikasi lama TIDAK lagi
 * dipakai untuk membuka data — ia tidak bisa melepas kunci Keystore. (Bila ingin dipertahankan,
 * jadikan gerbang sekunder SETELAH blok di bawah berhasil.)
 *
 * Wiring yang diperlukan:
 *  - SecureVaultApp: ubah `private val crypto` menjadi `val crypto` (atau sediakan getter).
 *  - AppRoot: panggil `LockScreen(crypto = app.crypto, onUnlocked = { AppLock.unlock() })`.
 *  - VaultViewModel/repo: tangkap VaultLockedException saat operasi vault -> picu kembali layar ini.
 */
@Composable
fun LockScreen(crypto: CryptoManager, onUnlocked: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(AppGradient)
    ) {
        Box(
            Modifier
                .offset(x = (-60).dp, y = (-40).dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 70.dp, y = 60.dp)
                .size(260.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("SecureVault", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(
                "Keep your passwords safe",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(28.dp))
            SectionCard {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    UnlockWithDeviceAuth(crypto, onUnlocked)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun UnlockWithDeviceAuth(crypto: CryptoManager, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    fun proceedIfKeyUnlocked() {
        // onSuccess biometrik seharusnya sudah melepas kunci; verifikasi defensif.
        if (crypto.isUnlocked()) {
            onUnlocked()
        } else {
            error = "Authentication succeeded but the key is still locked. Please try again."
            working = false
        }
    }

    fun launchAuth(auto: Boolean) {
        val act = activity
        if (act == null) {
            error = "Authentication cannot be opened on this screen"
            return
        }
        if (!BiometricHelper.isAvailable(context)) {
            // Tidak ada biometrik DAN tidak ada kunci layar perangkat: data tidak bisa digerbangi kunci.
            error = BiometricHelper.statusMessage(context) +
                "\n\nSecureVault requires a device screen lock (PIN/pattern/password) to protect your data."
            return
        }
        working = true
        error = null
        BiometricHelper.authenticate(
            act,
            onSuccess = { proceedIfKeyUnlocked() },
            onError = { code, msg ->
                working = false
                // Prompt otomatis kadang dibatalkan sistem saat layar belum siap — abaikan di mode auto.
                if (!(auto && code == BiometricPrompt.ERROR_CANCELED)) error = msg
            },
            onCancel = { working = false }
        )
    }

    // Munculkan prompt otomatis saat layar siap.
    LaunchedEffect(Unit) {
        if (activity != null && BiometricHelper.isAvailable(context)) {
            activity.lifecycle.withResumed { }
            delay(600)
            launchAuth(auto = true)
        }
    }

    Text("Verify to unlock the vault", textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = { launchAuth(auto = false) },
        enabled = !working,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (working) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.Fingerprint, null)
            Spacer(Modifier.size(8.dp))
            Text("Unlock with biometrics / device screen lock")
        }
    }
    error?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        if (BiometricHelper.needsEnrollment(context)) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { context.startActivity(BiometricHelper.enrollIntent()) }) {
                Text("Set up in Phone Settings")
            }
        }
    }
}
