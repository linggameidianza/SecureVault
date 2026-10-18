@file:OptIn(ExperimentalMaterial3Api::class)

package id.ichio.securevault.ui.screens

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import id.ichio.securevault.SecureVaultApp
import id.ichio.securevault.backup.BackupManager
import id.ichio.securevault.security.AppLock
import id.ichio.securevault.security.BiometricHelper
import id.ichio.securevault.ui.VaultViewModel
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.graphics.Color
import id.ichio.securevault.ui.theme.Coral
import id.ichio.securevault.ui.theme.GradientHeader
import id.ichio.securevault.ui.theme.IconTile
import id.ichio.securevault.ui.theme.Pink
import id.ichio.securevault.ui.theme.Purple
import id.ichio.securevault.ui.theme.SectionCard
import id.ichio.securevault.ui.theme.Sky
import id.ichio.securevault.ui.theme.Sun
import id.ichio.securevault.ui.theme.Teal
import id.ichio.securevault.ui.theme.Violet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MIN_BACKUP_PASSWORD = 8

@Composable
fun SettingsScreen(vm: VaultViewModel, onOpenHelp: () -> Unit, onOpenAbout: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var biometricAvailable by remember { mutableStateOf(BiometricHelper.isAvailable(context)) }
    var biometricStatus by remember { mutableStateOf(BiometricHelper.statusMessage(context)) }

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

    fun openDeviceLockSetup() {
        if (BiometricHelper.needsEnrollment(context)) {
            try {
                AppLock.allowExternalActivity()
                context.startActivity(BiometricHelper.enrollIntent())
            } catch (e: Exception) {
                vm.message("Set up a screen lock / biometrics in Phone Settings → Security")
            }
        } else {
            vm.message(biometricStatus)
        }
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
            vm.message("Notification permission denied. Enable it via Phone Settings → Apps → SecureVault → Notifications")
        }
    }

    var showBackupDialog by remember { mutableStateOf(false) }
    var pendingBackupPassword by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

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
        topBar = { GradientHeader(title = "Settings", subtitle = "Backup, security & notifications") }
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
                    title = "Back up data",
                    subtitle = "Save all accounts, passwords & notes to an encrypted file (.svbk)",
                    onClick = { if (!vm.busy) showBackupDialog = true }
                )
                SettingItem(
                    icon = Icons.Filled.Restore,
                    color = Sky,
                    title = "Restore data",
                    subtitle = "Restore from a SecureVault backup file",
                    onClick = {
                        if (!vm.busy) {
                            AppLock.allowExternalActivity()
                            openDoc.launch(arrayOf("*/*"))
                        }
                    }
                )
            }

            SectionTitle("Security")
            SectionCard {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { IconTile(Icons.Filled.Fingerprint, Pink) },
                    headlineContent = { Text("App lock", style = MaterialTheme.typography.titleSmall) },
                    supportingContent = {
                        Text(
                            if (biometricAvailable)
                                "The vault unlocks with biometrics or your device screen lock. " +
                                    "Passwords & notes are encrypted and only open after verification."
                            else biometricStatus,
                            color = if (biometricAvailable) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = if (biometricAvailable) Modifier
                    else Modifier.clickable { openDeviceLockSetup() }
                )
                SettingItem(
                    icon = Icons.Filled.Lock,
                    color = Coral,
                    title = "Lock now",
                    subtitle = "The app also locks automatically 30 seconds after going to the background",
                    onClick = { AppLock.lock() }
                )
            }

            SectionTitle("Notifications")
            SectionCard {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { IconTile(Icons.Filled.NotificationsActive, Sun) },
                    headlineContent = { Text("Expiring-password reminders", style = MaterialTheme.typography.titleSmall) },
                    supportingContent = {
                        Text(
                            if (notifOn && !notifPermission) "Notification permission not granted yet — tap the switch to allow it"
                            else "Get notified when a password is about to expire within 2 days or has already expired"
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

            SectionTitle("Information")
            SectionCard {
                SettingItem(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    color = Purple,
                    title = "Help",
                    subtitle = "A guide to using SecureVault",
                    onClick = onOpenHelp
                )
                SettingItem(
                    icon = Icons.Filled.Info,
                    color = Teal,
                    title = "About",
                    subtitle = "App version & creator — built by $APP_AUTHOR",
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
        title = { Text("Backup password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The backup file is encrypted with this password. Keep it safe — " +
                        "without this password the backup CANNOT be restored.",
                    style = MaterialTheme.typography.bodySmall
                )
                SecretField(pwd, { pwd = it }, "Password (min. $MIN_BACKUP_PASSWORD characters)")
                SecretField(confirm, { confirm = it }, "Repeat password")
                StrengthMeter(pwd)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    pwd.length < MIN_BACKUP_PASSWORD -> "At least $MIN_BACKUP_PASSWORD characters"
                    pwd != confirm -> "Passwords do not match"
                    else -> null
                }
                if (error == null) onConfirm(pwd)
            }) { Text("Choose file location") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
                SecretField(pwd, { pwd = it }, "Backup password")
                Text("Restore mode:", style = MaterialTheme.typography.titleSmall)
                ModeOption("Merge with existing data (duplicates are skipped)", !replace) { replace = false }
                ModeOption("Replace all data with the backup contents", replace) { replace = true }
                if (replace) {
                    Text(
                        "Warning: all current accounts & notes will be deleted.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = pwd.isNotEmpty(), onClick = { onConfirm(pwd, replace) }) { Text("Restore") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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

