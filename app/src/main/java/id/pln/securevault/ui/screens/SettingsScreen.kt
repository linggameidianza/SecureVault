@file:OptIn(ExperimentalMaterial3Api::class)

package id.pln.securevault.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import id.pln.securevault.SecureVaultApp
import id.pln.securevault.backup.BackupManager
import id.pln.securevault.security.AppLock
import id.pln.securevault.security.BiometricHelper
import id.pln.securevault.security.PinManager
import id.pln.securevault.security.PinResult
import id.pln.securevault.ui.VaultViewModel
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.graphics.Color
import id.pln.securevault.ui.theme.Coral
import id.pln.securevault.ui.theme.GradientHeader
import id.pln.securevault.ui.theme.IconTile
import id.pln.securevault.ui.theme.Pink
import id.pln.securevault.ui.theme.Purple
import id.pln.securevault.ui.theme.SectionCard
import id.pln.securevault.ui.theme.Sky
import id.pln.securevault.ui.theme.Sun
import id.pln.securevault.ui.theme.Teal
import id.pln.securevault.ui.theme.Violet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MIN_BACKUP_PASSWORD = 8

@Composable
fun SettingsScreen(vm: VaultViewModel, onOpenHelp: () -> Unit, onOpenAbout: () -> Unit) {
    val context = LocalContext.current
    val pinManager = (context.applicationContext as SecureVaultApp).pinManager
    val activity = remember(context) { context.findActivity() }
    var biometricAvailable by remember { mutableStateOf(BiometricHelper.isAvailable(context)) }
    var biometricStatus by remember { mutableStateOf(BiometricHelper.statusMessage(context)) }
    var biometricOn by remember { mutableStateOf(pinManager.biometricEnabled) }

    // Perbarui status biometrik setiap kembali ke layar ini (mis. setelah mendaftarkan sidik jari)
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                biometricAvailable = BiometricHelper.isAvailable(context)
                biometricStatus = BiometricHelper.statusMessage(context)
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    fun onBiometricToggle(on: Boolean) {
        if (!on) {
            pinManager.biometricEnabled = false
            biometricOn = false
            return
        }
        if (!BiometricHelper.isAvailable(context)) {
            if (BiometricHelper.needsEnrollment(context)) {
                try {
                    AppLock.allowExternalActivity()
                    context.startActivity(BiometricHelper.enrollIntent())
                } catch (e: Exception) {
                    vm.message("Daftarkan sidik jari di Setelan HP → Keamanan dan privasi → Biometrik")
                }
            } else {
                vm.message(BiometricHelper.statusMessage(context))
            }
            return
        }
        val act = activity ?: return
        // Uji dulu agar pasti berfungsi sebelum diaktifkan
        BiometricHelper.authenticate(
            act,
            title = "Aktifkan biometrik",
            subtitle = "Sentuh sensor sidik jari untuk konfirmasi",
            onSuccess = {
                pinManager.biometricEnabled = true
                biometricOn = true
                vm.message("Buka dengan biometrik aktif")
            },
            onError = { _, msg -> vm.message("Biometrik gagal: $msg") }
        )
    }
    val notifier = (context.applicationContext as SecureVaultApp).expiryNotifier
    var notifOn by remember { mutableStateOf(notifier.enabled) }
    var notifPermission by remember { mutableStateOf(notifier.hasPermission()) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifPermission = granted
        if (granted) {
            notifier.enabled = true
            notifOn = true
        } else {
            vm.message("Izin notifikasi ditolak. Aktifkan lewat Setelan HP → Aplikasi → SecureVault → Notifikasi")
        }
    }

    var showBackupDialog by remember { mutableStateOf(false) }
    var pendingBackupPassword by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var showChangePin by remember { mutableStateOf(false) }

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pwd = pendingBackupPassword
        pendingBackupPassword = null
        if (uri != null && pwd != null) vm.backup(uri, pwd)
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    Scaffold(
        topBar = { GradientHeader(title = "Pengaturan", subtitle = "Backup, keamanan & notifikasi") }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (vm.busy) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            SectionTitle("Backup & Restore")
            SectionCard {
                SettingItem(
                    icon = Icons.Filled.Backup,
                    color = Teal,
                    title = "Backup data",
                    subtitle = "Simpan semua akun, password & catatan ke file terenkripsi (.svbk)",
                    onClick = { if (!vm.busy) showBackupDialog = true }
                )
                SettingItem(
                    icon = Icons.Filled.Restore,
                    color = Sky,
                    title = "Restore data",
                    subtitle = "Pulihkan dari file backup SecureVault",
                    onClick = {
                        if (!vm.busy) {
                            AppLock.allowExternalActivity()
                            openDoc.launch(arrayOf("*/*"))
                        }
                    }
                )
            }

            SectionTitle("Keamanan")
            SectionCard {
                SettingItem(
                    icon = Icons.Filled.Dialpad,
                    color = Violet,
                    title = "Ganti PIN",
                    subtitle = "Ubah PIN untuk membuka aplikasi",
                    onClick = { showChangePin = true }
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { IconTile(Icons.Filled.Fingerprint, Pink) },
                    headlineContent = { Text("Buka dengan biometrik", style = MaterialTheme.typography.titleSmall) },
                    supportingContent = {
                        Text(
                            biometricStatus,
                            color = if (biometricAvailable) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable { onBiometricToggle(!(biometricOn && biometricAvailable)) },
                    trailingContent = {
                        Switch(
                            checked = biometricOn && biometricAvailable,
                            onCheckedChange = { onBiometricToggle(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Violet)
                        )
                    }
                )
                SettingItem(
                    icon = Icons.Filled.Lock,
                    color = Coral,
                    title = "Kunci sekarang",
                    subtitle = "Aplikasi juga terkunci otomatis 30 detik setelah di-background",
                    onClick = { AppLock.lock() }
                )
            }

            SectionTitle("Notifikasi")
            SectionCard {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { IconTile(Icons.Filled.NotificationsActive, Sun) },
                    headlineContent = { Text("Pengingat password kedaluwarsa", style = MaterialTheme.typography.titleSmall) },
                    supportingContent = {
                        Text(
                            if (notifOn && !notifPermission) "Izin notifikasi belum diberikan — ketuk sakelar untuk mengizinkan"
                            else "Notifikasi saat password akan kedaluwarsa dalam 2 hari atau sudah kedaluwarsa"
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = notifOn && notifPermission,
                            onCheckedChange = { on ->
                                if (on && !notifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    notifier.enabled = on
                                    notifOn = on
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Violet)
                        )
                    }
                )
            }

            SectionTitle("Informasi")
            SectionCard {
                SettingItem(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    color = Purple,
                    title = "Bantuan",
                    subtitle = "Panduan pemakaian SecureVault",
                    onClick = onOpenHelp
                )
                SettingItem(
                    icon = Icons.Filled.Info,
                    color = Teal,
                    title = "Tentang aplikasi",
                    subtitle = "Versi & pembuat aplikasi — dibuat oleh $APP_AUTHOR",
                    onClick = onOpenAbout
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showBackupDialog) {
        BackupPasswordDialog(
            onDismiss = { showBackupDialog = false },
            onConfirm = { pwd ->
                showBackupDialog = false
                pendingBackupPassword = pwd
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                AppLock.allowExternalActivity()
                createDoc.launch("securevault-backup-$stamp.${BackupManager.FILE_EXTENSION}")
            }
        )
    }

    restoreUri?.let { uri ->
        RestoreDialog(
            onDismiss = { restoreUri = null },
            onConfirm = { pwd, replace ->
                restoreUri = null
                vm.restore(uri, pwd, replace)
            }
        )
    }

    if (showChangePin) {
        ChangePinDialog(
            pinManager = pinManager,
            onDismiss = { showChangePin = false },
            onChanged = {
                showChangePin = false
                vm.message("PIN berhasil diganti")
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingItem(icon: ImageVector, color: Color, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { IconTile(icon, color) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    )
}

@Composable
private fun BackupPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Backup, null) },
        title = { Text("Password backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "File backup dienkripsi dengan password ini. Simpan baik-baik — " +
                        "tanpa password ini backup TIDAK dapat dipulihkan.",
                    style = MaterialTheme.typography.bodySmall
                )
                SecretField(pwd, { pwd = it }, "Password (min. $MIN_BACKUP_PASSWORD karakter)")
                SecretField(confirm, { confirm = it }, "Ulangi password")
                StrengthMeter(pwd)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    pwd.length < MIN_BACKUP_PASSWORD -> "Minimal $MIN_BACKUP_PASSWORD karakter"
                    pwd != confirm -> "Password tidak sama"
                    else -> null
                }
                if (error == null) onConfirm(pwd)
            }) { Text("Pilih lokasi file") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun RestoreDialog(onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var pwd by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Restore, null) },
        title = { Text("Restore backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecretField(pwd, { pwd = it }, "Password backup")
                Text("Mode restore:", style = MaterialTheme.typography.titleSmall)
                ModeOption("Gabungkan dengan data yang ada (duplikat dilewati)", !replace) { replace = false }
                ModeOption("Ganti semua data dengan isi backup", replace) { replace = true }
                if (replace) {
                    Text(
                        "Peringatan: semua akun & catatan saat ini akan dihapus.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = pwd.isNotEmpty(), onClick = { onConfirm(pwd, replace) }) { Text("Restore") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun ModeOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChangePinDialog(pinManager: PinManager, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val digitsOnly: (String) -> String = { it.filter(Char::isDigit).take(PinManager.MAX_PIN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Dialpad, null) },
        title = { Text("Ganti PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecretField(oldPin, { oldPin = digitsOnly(it) }, "PIN lama", numeric = true)
                SecretField(newPin, { newPin = digitsOnly(it) }, "PIN baru", numeric = true)
                SecretField(confirm, { confirm = digitsOnly(it) }, "Ulangi PIN baru", numeric = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !working, onClick = {
                error = when {
                    !PinManager.isValidFormat(newPin) -> "PIN baru harus ${PinManager.MIN_PIN}–${PinManager.MAX_PIN} digit"
                    newPin != confirm -> "PIN baru tidak sama"
                    else -> null
                }
                if (error != null) return@TextButton
                working = true
                scope.launch {
                    val result = withContext(Dispatchers.Default) { pinManager.verify(oldPin) }
                    when (result) {
                        PinResult.Success -> {
                            withContext(Dispatchers.Default) { pinManager.setPin(newPin) }
                            onChanged()
                        }
                        is PinResult.Wrong -> error = "PIN lama salah (sisa ${result.attemptsLeft} percobaan)"
                        is PinResult.LockedOut -> error = "Terkunci, coba lagi dalam ${result.seconds} detik"
                    }
                    working = false
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
