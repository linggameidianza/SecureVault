@file:OptIn(ExperimentalMaterial3Api::class)

package id.ichio.securevault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.ichio.securevault.data.CredentialForm
import id.ichio.securevault.ui.VaultViewModel
import id.ichio.securevault.ui.theme.GradientHeader
import id.ichio.securevault.util.GeneratorOptions
import id.ichio.securevault.util.PasswordGenerator

@Composable
fun CredentialEditScreen(vm: VaultViewModel, id: Long, onBack: () -> Unit) {
    var form by remember { mutableStateOf<CredentialForm?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = id <= 0L

    LaunchedEffect(id) { form = vm.loadCredential(id) }

    Scaffold(
        topBar = {
            GradientHeader(
                title = if (isNew) "Add Account" else "Account Details",
                subtitle = if (isNew) "Save a new account" else form?.title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                    IconButton(onClick = { form?.let { vm.saveCredential(it, onBack) } }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        val current = form
        if (current == null) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = current.title,
                onValueChange = { form = current.copy(title = it) },
                label = { Text("Account / service name *") },
                placeholder = { Text("e.g. Work Email, Gmail, SAP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = current.username,
                onValueChange = { form = current.copy(username = it) },
                label = { Text("Username / Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
                trailingIcon = {
                    IconButton(onClick = { vm.copyText("Username", current.username) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy username")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            SecretField(
                value = current.password,
                onChange = { form = current.copy(password = it) },
                label = "Password",
                monospace = true,
                trailingExtra = {
                    IconButton(onClick = {
                        form = current.copy(password = PasswordGenerator.generate(GeneratorOptions()))
                    }) {
                        Icon(Icons.Filled.Autorenew, contentDescription = "Generate password")
                    }
                    IconButton(onClick = { vm.copyText("Password", current.password, sensitive = true) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy password")
                    }
                }
            )
            StrengthMeter(current.password)
            ExpirySection(current) { form = it }
            OutlinedTextField(
                value = current.url,
                onValueChange = { form = current.copy(url = it) },
                label = { Text("URL / App") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = current.note,
                onValueChange = { form = current.copy(note = it) },
                label = { Text("Note (encrypted)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Tap the ⟳ icon in the password field to create a random 16-character password. " +
                    "Set more options in the Generator tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { vm.saveCredential(current, onBack) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete account?",
            text = "The account \"${form?.title}\" will be permanently deleted.",
            onConfirm = { vm.deleteCredential(id, onBack) },
            onDismiss = { confirmDelete = false }
        )
    }
}
