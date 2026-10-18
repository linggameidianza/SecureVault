@file:OptIn(ExperimentalMaterial3Api::class)

package id.ichio.securevault.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import id.ichio.securevault.ui.theme.AppGradient
import id.ichio.securevault.ui.theme.Dot
import id.ichio.securevault.ui.theme.SectionCard
import id.ichio.securevault.ui.theme.accentFor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.ichio.securevault.ui.theme.GradientHeader

const val APP_AUTHOR = "Ichio"
private const val FALLBACK_VERSION = "1.0"

private data class HelpTopic(val title: String, val body: String)

private val helpTopics = listOf(
    HelpTopic(
        "1. Getting started",
        "• On first launch, unlock SecureVault with a strong biometric (fingerprint/face) or your device screen lock (PIN, pattern, or password).\n" +
            "• That verification is what unlocks the vault's encryption key, so your data can only be read after you authenticate.\n" +
            "• Every time you open the app, verify to unlock."
    ),
    HelpTopic(
        "2. Saving accounts & passwords",
        "• Open the Accounts tab and tap the + button at the bottom right.\n" +
            "• Fill in the Account/service name (required), Username/Email, Password, URL, and Notes (optional).\n" +
            "• Tap the ⟳ icon in the password field to generate a strong random password.\n" +
            "• Tap ✓ at the top right, or the Save button."
    ),
    HelpTopic(
        "3. Viewing, copying & editing",
        "• In the account list, tap the person icon to copy the username and the key icon to copy the password.\n" +
            "• Tap an account to open its details; the eye icon shows/hides the password.\n" +
            "• A copied password is automatically cleared from the clipboard after 30 seconds.\n" +
            "• To delete an account, open its details and tap the trash icon.\n" +
            "• Use the search field to find accounts by name, username, or URL."
    ),
    HelpTopic(
        "4. Password generator",
        "• Open the Generator tab.\n" +
            "• Set the length (6–64 characters) and character types: uppercase, lowercase, digits, symbols.\n" +
            "• Turn on \"Avoid look-alike characters\" to exclude confusing I, l, 1, O, 0.\n" +
            "• Tap Generate to make a new one, then Copy. The indicator shows the password strength.\n" +
            "• Recommendation: at least 16 characters with every character type enabled."
    ),
    HelpTopic(
        "5. Notes",
        "• Open the Notes tab and tap + to create a new note.\n" +
            "• Enter a title and content, then tap ✓. Note content is stored encrypted.\n" +
            "• Tap a note to edit it, or the trash icon to delete it."
    ),
    HelpTopic(
        "6. Backing up your data",
        "• Open Settings → Back up data.\n" +
            "• Create a backup password (at least 8 characters) and repeat it.\n" +
            "• Choose where to save the .svbk file (phone storage, Google Drive, etc.).\n" +
            "• The backup contains ALL accounts, passwords, notes, and tasks, encrypted.\n" +
            "• IMPORTANT: store the backup password somewhere safe. Without it, the backup cannot be opened.\n" +
            "• Back up regularly and keep a copy off your phone."
    ),
    HelpTopic(
        "7. Restoring your data",
        "• Open Settings → Restore data, then pick a .svbk file.\n" +
            "• Enter the backup password.\n" +
            "• Choose a mode:\n" +
            "   – Merge: backup entries are added; exact duplicates are skipped.\n" +
            "   – Replace all: current data is deleted and replaced with the backup.\n" +
            "• You can restore on another phone (for example when switching devices)."
    ),
    HelpTopic(
        "8. Security & auto-lock",
        "• The app locks automatically if it stays in the background for more than 30 seconds.\n" +
            "• Tap Settings → Lock now to lock it manually.\n" +
            "• Unlocking requires a strong biometric or your device screen lock; the vault's encryption key is bound to that authentication, so it is a cryptographic lock — not just a screen.\n" +
            "• The entire database is encrypted (SQLCipher); passwords and notes get an extra layer (AES-256-GCM in the Android Keystore).\n" +
            "• Screenshots and screen recording are blocked for your protection.\n" +
            "• All data stays on your phone and is never sent to the internet."
    ),
    HelpTopic(
        "9. Password expiry & notifications",
        "• In the account form, under \"Password expiry\", choose:\n" +
            "   – Change every 30/60/90/180/365 days: the expiry date is counted from today and is refreshed automatically each time the password changes.\n" +
            "   – Pick a date…: set your own expiry date.\n" +
            "   – No expiry: the password never expires.\n" +
            "• Accounts expiring soon (≤ 2 days) are marked orange; already-expired ones are marked red.\n" +
            "• On the Accounts tab, tap the \"… passwords need changing\" button to show only those accounts.\n" +
            "• A notification appears automatically 2 days before a password expires, and once more when it has expired. " +
            "The check runs in the background even when the app is closed.\n" +
            "• On Android 13 and up, allow notifications when prompted. Use Settings → Notifications to turn them on/off.\n" +
            "• Notifications show only the account name, never the password."
    ),
    HelpTopic(
        "10. Tasks, subtasks & reminders",
        "• Open the Tasks tab and tap \"New task\".\n" +
            "• Enter a title and notes (optional).\n" +
            "• Add subtasks one at a time in the Subtasks section, then tap + or Enter.\n" +
            "• Turn on Reminder to get a notification: pick a quick option (In 1 hour, Tonight 7:00 PM, Tomorrow 8:00 AM) " +
            "or set your own date & time.\n" +
            "• Check the circle in the list to mark a task or subtask done. " +
            "When all subtasks are checked, the task is completed automatically.\n" +
            "• From the notification, tap \"Mark done\" without opening the app.\n" +
            "• Tap the Active / Done box at the top to switch views.\n" +
            "• Reminders keep working after the phone restarts. Tasks are included in the backup file."
    ),
    HelpTopic(
        "11. Checking for breaches (password & email)",
        "• Open the Generator tab and scroll to the \"Breach check\" section.\n" +
            "• Password: type a password (or tap \"Use generator result\"), then Check. " +
            "Free and safe — only the first 5 characters of its SHA-1 hash are sent (k-anonymity); the real password never leaves your phone.\n" +
            "• Audit: tap \"Check all saved passwords\" to find which accounts use a password that has been breached.\n" +
            "• Email: type an email address and Check. Powered by XposedOrNot — free, with no API key required. " +
            "Results show which services were breached, the year, and what kind of data was exposed.\n" +
            "• If a breach is found: change your password on that service right away, never reuse the same password, " +
            "and turn on 2-step verification.\n" +
            "• The breach check is the only feature that uses the internet, and only when you tap Check."
    ),
    HelpTopic(
        "12. Frequently asked questions (FAQ)",
        "• Can't unlock the app?\n" +
            "  Unlocking uses your device biometric / screen lock. If you removed or changed your device lock and the key " +
            "was invalidated, clear the app data (Phone Settings → Apps → SecureVault → Storage → Clear data), then restore from a backup.\n\n" +
            "• Forgot your backup password?\n" +
            "  That backup file cannot be opened. Create a new backup with a password you'll remember.\n\n" +
            "• Switching phones / factory reset?\n" +
            "  Back up on the old phone, move the .svbk file over, install SecureVault on the new phone, then Restore.\n\n" +
            "• Why is the screen black in a screenshot?\n" +
            "  That's intentional, to protect your passwords from screen capture."
    )
)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            GradientHeader(
                title = "Help",
                subtitle = "How to use SecureVault",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "How to use SecureVault. Tap a topic to expand its explanation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(helpTopics, key = { it.title }) { topic -> HelpCard(topic) }
        }
    }
}

@Composable
private fun HelpCard(topic: HelpTopic) {
    var expanded by rememberSaveable(topic.title) { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(accentFor(topic.title), size = 10.dp)
                Spacer(Modifier.size(10.dp))
                Text(
                    topic.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(topic.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: FALLBACK_VERSION
        } catch (e: Exception) {
            FALLBACK_VERSION
        }
    }

    Scaffold(
        topBar = {
            GradientHeader(
                title = "About",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(AppGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("SecureVault", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Version $version",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Password manager, password generator, and encrypted notes.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))

            SectionCard {
                InfoRow("Built by", APP_AUTHOR)
                HorizontalDivider()
                InfoRow("Version", version)
                HorizontalDivider()
                InfoRow("Platform", "Android 8.0 and up")
                HorizontalDivider()
                InfoRow("Encryption", "AES-256-GCM (Android Keystore)")
                HorizontalDivider()
                InfoRow("Backup", "PBKDF2-SHA256 + AES-256-GCM")
                HorizontalDivider()
                InfoRow("Internet", "Breach check only")
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "© 2026 $APP_AUTHOR. All data is stored only on your device.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        trailingContent = { Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
    )
}
