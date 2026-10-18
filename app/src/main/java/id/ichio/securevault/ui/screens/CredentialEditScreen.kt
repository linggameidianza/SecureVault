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
                title = if (isNew) "Tambah Akun" else "Detail Akun",
                subtitle = if (isNew) "Simpan akun baru" else form?.title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Hapus")
                        }
                    }
                    IconButton(onClick = { form?.let { vm.saveCredential(it, onBack) } }) {
                        Icon(Icons.Filled.Check, contentDescription = "Simpan")
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
                label = { Text("Nama akun / layanan *") },
                placeholder = { Text("mis. Email Kantor, Gmail, SAP") },
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
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Salin username")
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
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Salin password")
                    }
                }
            )
            StrengthMeter(current.password)
            ExpirySection(current) { form = it }
            OutlinedTextField(
                value = current.url,
                onValueChange = { form = current.copy(url = it) },
                label = { Text("URL / Aplikasi") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = current.note,
                onValueChange = { form = current.copy(note = it) },
                label = { Text("Catatan (terenkripsi)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Tekan ikon ⟳ di kolom password untuk membuat password acak 16 karakter. " +
                    "Atur opsi lebih lengkap di tab Generator.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { vm.saveCredential(current, onBack) }, modifier = Modifier.fillMaxWidth()) {
                Text("Simpan")
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus akun?",
            text = "Akun \"${form?.title}\" akan dihapus permanen.",
            onConfirm = { vm.deleteCredential(id, onBack) },
            onDismiss = { confirmDelete = false }
        )
    }
}
