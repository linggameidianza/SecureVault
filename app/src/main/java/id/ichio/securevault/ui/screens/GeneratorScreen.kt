package id.ichio.securevault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.ichio.securevault.ui.VaultViewModel
import id.ichio.securevault.ui.theme.Coral
import id.ichio.securevault.ui.theme.GradientHeader
import id.ichio.securevault.ui.theme.IconTile
import id.ichio.securevault.ui.theme.Pink
import id.ichio.securevault.ui.theme.SectionCard
import id.ichio.securevault.ui.theme.Sky
import id.ichio.securevault.ui.theme.Sun
import id.ichio.securevault.ui.theme.Teal
import id.ichio.securevault.ui.theme.Violet
import id.ichio.securevault.util.GeneratorOptions
import id.ichio.securevault.util.PasswordGenerator

@Composable
fun GeneratorScreen(vm: VaultViewModel) {
    var length by rememberSaveable { mutableStateOf(16f) }
    var upper by rememberSaveable { mutableStateOf(true) }
    var lower by rememberSaveable { mutableStateOf(true) }
    var digits by rememberSaveable { mutableStateOf(true) }
    var symbols by rememberSaveable { mutableStateOf(true) }
    var noAmbiguous by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf("") }

    fun regenerate() {
        if (!(upper || lower || digits || symbols)) {
            password = ""
            return
        }
        password = PasswordGenerator.generate(
            GeneratorOptions(length.toInt(), upper, lower, digits, symbols, noAmbiguous)
        )
    }

    LaunchedEffect(length.toInt(), upper, lower, digits, symbols, noAmbiguous) { regenerate() }

    Scaffold(
        topBar = {
            GradientHeader(title = "Generator", subtitle = "Create & check password strength") {
                PasswordDisplay(password)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Button(
                        onClick = { regenerate() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.22f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Autorenew, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Generate")
                    }
                    Button(
                        onClick = { vm.copyText("Password", password, sensitive = true) },
                        enabled = password.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Violet
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Copy")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionCard {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Password length", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${length.toInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Slider(
                        value = length,
                        onValueChange = { length = it },
                        valueRange = 6f..64f,
                        steps = 57,
                        colors = SliderDefaults.colors(
                            thumbColor = Pink,
                            activeTrackColor = Violet,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )
                }
            }

            SectionCard {
                Column(Modifier.padding(vertical = 8.dp)) {
                    OptionSwitch(Icons.Filled.TextFields, Violet, "Uppercase", "A–Z", upper) { upper = it }
                    OptionSwitch(Icons.Filled.TextFormat, Sky, "Lowercase", "a–z", lower) { lower = it }
                    OptionSwitch(Icons.Filled.Numbers, Teal, "Digits", "0–9", digits) { digits = it }
                    OptionSwitch(Icons.Filled.Tag, Coral, "Symbols", "!@#\$%&*…", symbols) { symbols = it }
                    OptionSwitch(Icons.Filled.Visibility, Sun, "Avoid look-alike characters", "I l 1 O 0", noAmbiguous) {
                        noAmbiguous = it
                    }
                }
            }

            Text(
                "DATA BREACH CHECK",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )
            BreachCheckSection(vm, generatedPassword = password)

            Text(
                "Passwords are created with SecureRandom (cryptographic) and always include every selected character type. " +
                    "The clipboard is cleared automatically 30 seconds after copying.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun PasswordDisplay(password: String) {
    val strength = if (password.isEmpty()) null else PasswordGenerator.strength(password)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(16.dp)
    ) {
        SelectionContainer {
            Text(
                password.ifEmpty { "Select at least one character type" },
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        if (strength != null) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { strength.level },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Strength: ${strength.label}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun OptionSwitch(
    icon: ImageVector,
    color: Color,
    label: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, color, size = 36.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Violet)
        )
    }
}
