package id.pln.securevault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import id.pln.securevault.SecureVaultApp
import id.pln.securevault.breach.PwnedClient
import id.pln.securevault.security.AppLock
import id.pln.securevault.ui.VaultViewModel
import id.pln.securevault.ui.theme.Coral
import id.pln.securevault.ui.theme.IconTile
import id.pln.securevault.ui.theme.SectionCard
import id.pln.securevault.ui.theme.Sky
import id.pln.securevault.ui.theme.Violet
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Safe = Color(0xFF12A150)
private val idLocale = Locale("id", "ID")

private sealed interface CheckState {
    data object Idle : CheckState
    data object Loading : CheckState
    data class Error(val message: String) : CheckState
}

/** Bagian "Cek Kebocoran Data" di layar Generator (Have I Been Pwned). */
@Composable
fun BreachCheckSection(vm: VaultViewModel, generatedPassword: String) {
    PasswordBreachCard(vm, generatedPassword)
    EmailBreachCard()
    HibpAttribution()
}

// ---------------------------------------------------------------------
// Password
// ---------------------------------------------------------------------

@Composable
private fun PasswordBreachCard(vm: VaultViewModel, generatedPassword: String) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    var result by remember { mutableStateOf<Int?>(null) }

    var scanState by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    var scanProgress by remember { mutableStateOf(0 to 0) }
    var scanResult by remember { mutableStateOf<Pair<Int, List<VaultViewModel.VaultLeak>>?>(null) }

    fun check() {
        if (input.isEmpty() || state == CheckState.Loading) return
        val pwd = input
        state = CheckState.Loading
        result = null
        scope.launch {
            state = try {
                result = PwnedClient.passwordPwnCount(pwd)
                CheckState.Idle
            } catch (e: Exception) {
                CheckState.Error(PwnedClient.friendlyError(e))
            }
        }
    }

    SectionCard {
        Column(Modifier.padding(16.dp)) {
            CardHeader(
                icon = Icons.Filled.GppMaybe,
                color = Coral,
                title = "Cek kebocoran password",
                subtitle = "Gratis • password tidak pernah dikirim"
            )
            Spacer(Modifier.height(12.dp))
            SecretField(
                value = input,
                onChange = {
                    input = it
                    result = null
                    if (state is CheckState.Error) state = CheckState.Idle
                },
                label = "Password yang ingin diperiksa",
                monospace = true
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        input = generatedPassword
                        result = null
                    },
                    enabled = generatedPassword.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Pakai hasil generator", maxLines = 1) }
                Button(
                    onClick = { check() },
                    enabled = input.isNotEmpty() && state != CheckState.Loading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state == CheckState.Loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Periksa")
                    }
                }
            }

            val s = state
            val count = result
            when {
                s is CheckState.Error -> ResultBanner(Icons.Filled.ErrorOutline, MaterialTheme.colorScheme.error, s.message)
                count != null && count > 0 -> ResultBanner(
                    Icons.Filled.Warning,
                    MaterialTheme.colorScheme.error,
                    "Password ini ditemukan ${formatNumber(count.toLong())} kali dalam kebocoran data. " +
                        "Jangan digunakan — buat password baru!"
                )
                count == 0 -> ResultBanner(
                    Icons.Filled.CheckCircle,
                    Safe,
                    "Aman — password ini tidak ditemukan dalam database kebocoran."
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text("Audit semua password tersimpan", style = MaterialTheme.typography.titleSmall)
            Text(
                "Periksa apakah ada password akun Anda yang pernah bocor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scanState = CheckState.Loading
                    scanResult = null
                    scanProgress = 0 to 0
                    scope.launch {
                        scanState = try {
                            scanResult = vm.scanSavedPasswords { done, total -> scanProgress = done to total }
                            CheckState.Idle
                        } catch (e: Exception) {
                            CheckState.Error(PwnedClient.friendlyError(e))
                        }
                    }
                },
                enabled = scanState != CheckState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.GppMaybe, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Periksa semua password tersimpan")
            }

            val ss = scanState
            val sr = scanResult
            if (ss == CheckState.Loading) {
                Spacer(Modifier.height(10.dp))
                val (done, total) = scanProgress
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                )
                Text(
                    "Memeriksa $done dari $total password…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else if (ss is CheckState.Error) {
                ResultBanner(Icons.Filled.ErrorOutline, MaterialTheme.colorScheme.error, ss.message)
            } else if (sr != null) {
                val (total, leaks) = sr
                if (total == 0) {
                    ResultBanner(Icons.Filled.Info, Sky, "Belum ada password tersimpan untuk diperiksa.")
                } else if (leaks.isEmpty()) {
                    ResultBanner(Icons.Filled.CheckCircle, Safe, "Semua $total password tersimpan aman ✓")
                } else {
                    ResultBanner(
                        Icons.Filled.Warning,
                        MaterialTheme.colorScheme.error,
                        "${leaks.size} dari $total password pernah bocor. Segera ganti password akun berikut:"
                    )
                    leaks.forEach { leak ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            GradientAvatarSmall(leak.title)
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp)
                            ) {
                                Text(leak.title, style = MaterialTheme.typography.titleSmall)
                                if (leak.username.isNotBlank()) {
                                    Text(
                                        leak.username,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                "${formatNumber(leak.count.toLong())}×",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradientAvatarSmall(title: String) {
    id.pln.securevault.ui.theme.GradientAvatar(title, size = 34.dp)
}

// ---------------------------------------------------------------------
// Email
// ---------------------------------------------------------------------

@Composable
private fun EmailBreachCard() {
    val context = LocalContext.current
    val keyStore = remember { (context.applicationContext as SecureVaultApp).hibpKeyStore }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(keyStore.hasKey()) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    var breaches by remember { mutableStateOf<List<PwnedClient.Breach>?>(null) }

    fun check() {
        val key = keyStore.get()
        if (key == null) {
            showKeyDialog = true
            return
        }
        val target = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches()) {
            state = CheckState.Error("Format email tidak valid")
            return
        }
        state = CheckState.Loading
        breaches = null
        scope.launch {
            state = try {
                breaches = PwnedClient.breachedAccount(target, key)
                CheckState.Idle
            } catch (e: Exception) {
                CheckState.Error(PwnedClient.friendlyError(e))
            }
        }
    }

    SectionCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    CardHeader(
                        icon = Icons.Filled.AlternateEmail,
                        color = Violet,
                        title = "Cek kebocoran email",
                        subtitle = if (hasKey) "API key HIBP tersimpan ✓" else "Butuh API key HIBP (berlangganan)"
                    )
                }
                IconButton(onClick = { showKeyDialog = true }) {
                    Icon(Icons.Filled.VpnKey, contentDescription = "Atur API key", tint = Violet)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    breaches = null
                    if (state is CheckState.Error) state = CheckState.Idle
                },
                label = { Text("Alamat email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { check() },
                enabled = email.isNotBlank() && state != CheckState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state == CheckState.Loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (hasKey) "Periksa email" else "Atur API key & periksa")
                }
            }

            val s = state
            val list = breaches
            when {
                s is CheckState.Error -> ResultBanner(Icons.Filled.ErrorOutline, MaterialTheme.colorScheme.error, s.message)
                list != null && list.isEmpty() -> ResultBanner(
                    Icons.Filled.CheckCircle, Safe, "Kabar baik — email ini tidak ditemukan dalam kebocoran data."
                )
                list != null -> {
                    ResultBanner(
                        Icons.Filled.Warning,
                        MaterialTheme.colorScheme.error,
                        "Email ini ditemukan di ${list.size} kebocoran data. " +
                            "Ganti password di layanan berikut & aktifkan verifikasi 2 langkah."
                    )
                    list.forEach { BreachItem(it) }
                }
            }
        }
    }

    if (showKeyDialog) {
        ApiKeyDialog(
            current = keyStore.get().orEmpty(),
            onDismiss = { showKeyDialog = false },
            onSave = {
                keyStore.set(it)
                hasKey = keyStore.hasKey()
                showKeyDialog = false
            }
        )
    }
}

@Composable
private fun BreachItem(b: PwnedClient.Breach) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                b.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(formatBreachDate(b.breachDate), style = MaterialTheme.typography.labelMedium, color = Coral)
        }
        if (b.domain.isNotBlank()) {
            Text(b.domain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "${formatNumber(b.pwnCount)} akun terdampak" + if (!b.isVerified) " • belum terverifikasi" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (b.dataClasses.isNotEmpty()) {
            Text(
                "Data bocor: " + b.dataClasses.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ApiKeyDialog(current: String, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    val uri = LocalUriHandler.current
    var key by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.VpnKey, null) },
        title = { Text("API key Have I Been Pwned") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Pemeriksaan email memerlukan API key berlangganan dari haveibeenpwned.com. " +
                        "Key disimpan terenkripsi di perangkat ini dan hanya dikirim ke HIBP.",
                    style = MaterialTheme.typography.bodySmall
                )
                SecretField(key, { key = it.trim() }, "API key (32 karakter)", monospace = true)
                Text(
                    "Dapatkan API key →",
                    color = Violet,
                    style = MaterialTheme.typography.labelLarge,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        AppLock.allowExternalActivity()
                        runCatching { uri.openUri("https://haveibeenpwned.com/API/Key") }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key) }, enabled = key.isNotBlank()) { Text("Simpan") }
        },
        dismissButton = {
            Row {
                if (current.isNotEmpty()) {
                    TextButton(onClick = { onSave(null) }) {
                        Text("Hapus", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        }
    )
}

// ---------------------------------------------------------------------
// Komponen kecil
// ---------------------------------------------------------------------

@Composable
private fun CardHeader(icon: ImageVector, color: Color, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, color, size = 36.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResultBanner(icon: ImageVector, color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun HibpAttribution() {
    val uri = LocalUriHandler.current
    Text(
        "Data kebocoran dari Have I Been Pwned (haveibeenpwned.com) — CC BY 4.0",
        style = MaterialTheme.typography.bodySmall,
        color = Violet,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clickable {
                AppLock.allowExternalActivity()
                runCatching { uri.openUri("https://haveibeenpwned.com") }
            }
    )
}

private fun formatNumber(n: Long): String = NumberFormat.getIntegerInstance(idLocale).format(n)

private fun formatBreachDate(raw: String): String =
    runCatching {
        LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("d MMM yyyy", idLocale))
    }.getOrDefault(raw)
