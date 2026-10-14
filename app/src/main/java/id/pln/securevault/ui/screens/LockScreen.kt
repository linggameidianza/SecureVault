package id.pln.securevault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import id.pln.securevault.ui.theme.AppGradient
import id.pln.securevault.ui.theme.SectionCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.pln.securevault.security.BiometricHelper
import id.pln.securevault.security.PinManager
import id.pln.securevault.security.PinResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.withResumed
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LockScreen(pinManager: PinManager, onUnlocked: () -> Unit) {
    var setupMode by remember { mutableStateOf(!pinManager.isPinSet()) }
    Box(
        Modifier
            .fillMaxSize()
            .background(AppGradient)
    ) {
        // Lingkaran dekoratif
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
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("SecureVault", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(
                "Simpan password dengan aman",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(28.dp))
            SectionCard {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (setupMode) {
                        SetupPin(pinManager, onDone = {
                            setupMode = false
                            onUnlocked()
                        })
                    } else {
                        UnlockPin(pinManager, onUnlocked)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SetupPin(pinManager: PinManager, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var askBiometric by remember { mutableStateOf(false) }

    Text(
        "Buat PIN ${PinManager.MIN_PIN}–${PinManager.MAX_PIN} digit untuk mengunci aplikasi.",
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))
    SecretField(pin, { if (it.length <= PinManager.MAX_PIN) pin = it.filter(Char::isDigit) }, "PIN baru", numeric = true)
    Spacer(Modifier.height(8.dp))
    SecretField(confirm, { if (it.length <= PinManager.MAX_PIN) confirm = it.filter(Char::isDigit) }, "Ulangi PIN", numeric = true)
    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(20.dp))
    Button(
        enabled = !working,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            error = when {
                !PinManager.isValidFormat(pin) -> "PIN harus ${PinManager.MIN_PIN}–${PinManager.MAX_PIN} digit angka"
                pin != confirm -> "PIN tidak sama"
                else -> null
            }
            if (error == null) {
                working = true
                scope.launch {
                    withContext(Dispatchers.Default) { pinManager.setPin(pin) }
                    working = false
                    if (BiometricHelper.isAvailable(context)) askBiometric = true else onDone()
                }
            }
        }
    ) {
        if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Text("Simpan PIN")
    }

    if (askBiometric) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Filled.Fingerprint, null) },
            title = { Text("Aktifkan biometrik?") },
            text = { Text("Buka aplikasi dengan sidik jari / biometrik. PIN tetap bisa digunakan.") },
            confirmButton = {
                TextButton(onClick = {
                    pinManager.biometricEnabled = true
                    askBiometric = false
                    onDone()
                }) { Text("Aktifkan") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pinManager.biometricEnabled = false
                    askBiometric = false
                    onDone()
                }) { Text("Nanti") }
            }
        )
    }
}

@Composable
private fun UnlockPin(pinManager: PinManager, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val biometricEnabled = remember { pinManager.biometricEnabled }
    val biometricUsable = remember { biometricEnabled && BiometricHelper.isAvailable(context) }

    fun launchBiometric(auto: Boolean) {
        if (!BiometricHelper.isAvailable(context)) {
            error = BiometricHelper.statusMessage(context)
            return
        }
        val act = activity
        if (act == null) {
            error = "Biometrik tidak dapat dibuka di layar ini"
            return
        }
        BiometricHelper.authenticate(
            act,
            onSuccess = onUnlocked,
            onError = { code, msg ->
                // Prompt otomatis kadang dibatalkan sistem (mis. Samsung) saat layar belum siap — abaikan
                if (!(auto && code == BiometricPrompt.ERROR_CANCELED)) error = msg
            }
        )
    }

    LaunchedEffect(Unit) {
        if (biometricUsable && activity != null) {
            // Tunggu sampai activity benar-benar aktif & jendela fokus sebelum memanggil prompt
            activity.lifecycle.withResumed { }
            delay(600)
            launchBiometric(auto = true)
        }
    }

    fun submit() {
        if (pin.isEmpty() || working) return
        working = true
        scope.launch {
            val result = withContext(Dispatchers.Default) { pinManager.verify(pin) }
            working = false
            when (result) {
                PinResult.Success -> onUnlocked()
                is PinResult.Wrong -> {
                    pin = ""
                    error = "PIN salah. Sisa percobaan: ${result.attemptsLeft}"
                }
                is PinResult.LockedOut -> {
                    pin = ""
                    error = "Terlalu banyak percobaan. Coba lagi dalam ${result.seconds} detik"
                }
            }
        }
    }

    Text("Masukkan PIN untuk membuka", textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    SecretField(
        value = pin,
        onChange = { if (it.length <= PinManager.MAX_PIN) pin = it.filter(Char::isDigit) },
        label = "PIN",
        numeric = true
    )
    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(20.dp))
    Button(onClick = { submit() }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
        if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Text("Buka")
    }
    if (biometricEnabled) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { launchBiometric(auto = false) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Fingerprint, null)
            Spacer(Modifier.size(8.dp))
            Text("Gunakan biometrik")
        }
    }
}
