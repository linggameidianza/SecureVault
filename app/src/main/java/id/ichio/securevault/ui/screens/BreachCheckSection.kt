package id.ichio.securevault.ui.screens

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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import id.ichio.securevault.breach.PwnedClient
import id.ichio.securevault.breach.XposedClient
import id.ichio.securevault.security.AppLock
import id.ichio.securevault.ui.VaultViewModel
import id.ichio.securevault.ui.theme.Coral
import id.ichio.securevault.ui.theme.IconTile
import id.ichio.securevault.ui.theme.SectionCard
import id.ichio.securevault.ui.theme.Sky
import id.ichio.securevault.ui.theme.Violet
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Safe = Color(0xFF12A150)
private val idLocale = Locale.US

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
                title = "Password breach check",
                subtitle = "Free • your password is never sent"
            )
            Spacer(Modifier.height(12.dp))
            SecretField(
                value = input,
                onChange = {
                    input = it
                    result = null
                    if (state is CheckState.Error) state = CheckState.Idle
                },
                label = "Password to check",
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
                ) { Text("Use generator result", maxLines = 1) }
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
                        Text("Check")
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
                    "This password was found ${formatNumber(count.toLong())} times in data breaches. " +
                        "Don't use it — create a new password!"
                )
                count == 0 -> ResultBanner(
                    Icons.Filled.CheckCircle,
                    Safe,
                    "Safe — this password was not found in any breach database."
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text("Audit all saved passwords", style = MaterialTheme.typography.titleSmall)
            Text(
                "Check whether any of your account passwords have been leaked.",
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
                Text("Check all saved passwords")
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
                    "Checking $done of $total passwords…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else if (ss is CheckState.Error) {
                ResultBanner(Icons.Filled.ErrorOutline, MaterialTheme.colorScheme.error, ss.message)
            } else if (sr != null) {
                val (total, leaks) = sr
                if (total == 0) {
                    ResultBanner(Icons.Filled.Info, Sky, "No saved passwords to check yet.")
                } else if (leaks.isEmpty()) {
                    ResultBanner(Icons.Filled.CheckCircle, Safe, "All $total saved passwords are safe ✓")
                } else {
                    ResultBanner(
                        Icons.Filled.Warning,
                        MaterialTheme.colorScheme.error,
                        "${leaks.size} of $total passwords have been leaked. Change the passwords for these accounts right away:"
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
    id.ichio.securevault.ui.theme.GradientAvatar(title, size = 34.dp)
}

// ---------------------------------------------------------------------
// Email
// ---------------------------------------------------------------------

@Composable
private fun EmailBreachCard() {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    var breaches by remember { mutableStateOf<List<PwnedClient.Breach>?>(null) }

    fun check() {
        val target = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches()) {
            state = CheckState.Error("Invalid email format")
            return
        }
        state = CheckState.Loading
        breaches = null
        scope.launch {
            state = try {
                breaches = XposedClient.breachedAccount(target)
                CheckState.Idle
            } catch (e: Exception) {
                CheckState.Error(XposedClient.friendlyError(e))
            }
        }
    }

    SectionCard {
        Column(Modifier.padding(16.dp)) {
            CardHeader(
                icon = Icons.Filled.AlternateEmail,
                color = Violet,
                title = "Email breach check",
                subtitle = "Free • via XposedOrNot (no API key)"
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    breaches = null
                    if (state is CheckState.Error) state = CheckState.Idle
                },
                label = { Text("Email address") },
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
                    Text("Check email")
                }
            }

            val s = state
            val list = breaches
            when {
                s is CheckState.Error -> ResultBanner(Icons.Filled.ErrorOutline, MaterialTheme.colorScheme.error, s.message)
                list != null && list.isEmpty() -> ResultBanner(
                    Icons.Filled.CheckCircle, Safe, "Good news — this email was not found in any data breach."
                )
                list != null -> {
                    ResultBanner(
                        Icons.Filled.Warning,
                        MaterialTheme.colorScheme.error,
                        "This email was found in ${list.size} data breaches. " +
                            "Change your password on the following services & enable two-step verification."
                    )
                    list.forEach { BreachItem(it) }
                }
            }
        }
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
            "${formatNumber(b.pwnCount)} accounts affected" + if (!b.isVerified) " • unverified" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (b.dataClasses.isNotEmpty()) {
            Text(
                "Leaked data: " + b.dataClasses.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
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
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(
            "Password check: Have I Been Pwned (haveibeenpwned.com) — CC BY 4.0",
            style = MaterialTheme.typography.bodySmall,
            color = Violet,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable {
                AppLock.allowExternalActivity()
                runCatching { uri.openUri("https://haveibeenpwned.com") }
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Email check: XposedOrNot (xposedornot.com)",
            style = MaterialTheme.typography.bodySmall,
            color = Violet,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable {
                AppLock.allowExternalActivity()
                runCatching { uri.openUri("https://xposedornot.com") }
            }
        )
    }
}

private fun formatNumber(n: Long): String = NumberFormat.getIntegerInstance(idLocale).format(n)

private fun formatBreachDate(raw: String): String =
    runCatching {
        LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("d MMM yyyy", idLocale))
    }.getOrDefault(raw)
