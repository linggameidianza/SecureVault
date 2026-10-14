@file:OptIn(ExperimentalMaterial3Api::class)

package id.pln.securevault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import id.pln.securevault.ui.theme.GradientFab
import id.pln.securevault.ui.theme.HeaderSearchField
import id.pln.securevault.ui.theme.Ink
import id.pln.securevault.ui.theme.accentFor
import id.pln.securevault.ui.theme.pastelFor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.pln.securevault.data.NoteForm
import id.pln.securevault.ui.VaultViewModel
import id.pln.securevault.ui.theme.GradientHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))

@Composable
fun NotesScreen(vm: VaultViewModel, onOpen: (Long) -> Unit) {
    val notes by vm.notes.collectAsState()
    val query by vm.noteQuery.collectAsState()
    val total by vm.noteCount.collectAsState()

    Scaffold(
        topBar = {
            GradientHeader(title = "Catatan", subtitle = "$total catatan terenkripsi") {
                HeaderSearchField(
                    value = query,
                    onChange = { vm.noteQuery.value = it },
                    placeholder = "Cari judul catatan…"
                )
            }
        },
        floatingActionButton = { GradientFab("Catatan baru", Icons.Filled.Add) { onOpen(0L) } }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (notes.isEmpty()) {
                EmptyState(
                    if (query.isBlank()) "Belum ada catatan.\nIsi catatan disimpan terenkripsi."
                    else "Tidak ada hasil untuk \"$query\""
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteTile(note.title, note.updatedAt) { onOpen(note.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteTile(title: String, updatedAt: Long, onClick: () -> Unit) {
    val accent = accentFor(title)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(pastelFor(title))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Box(
            Modifier
                .size(width = 28.dp, height = 6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(accent)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Text(
            dateFormat.format(Date(updatedAt)),
            style = MaterialTheme.typography.labelSmall,
            color = Ink.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun NoteEditScreen(vm: VaultViewModel, id: Long, onBack: () -> Unit) {
    var form by remember { mutableStateOf<NoteForm?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = id <= 0L

    LaunchedEffect(id) { form = vm.loadNote(id) }

    Scaffold(
        topBar = {
            GradientHeader(
                title = if (isNew) "Catatan Baru" else "Edit Catatan",
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
                    IconButton(onClick = { form?.let { vm.saveNote(it, onBack) } }) {
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
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = current.title,
                onValueChange = { form = current.copy(title = it) },
                label = { Text("Judul") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = current.content,
                onValueChange = { form = current.copy(content = it) },
                label = { Text("Isi catatan") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus catatan?",
            text = "Catatan \"${form?.title}\" akan dihapus permanen.",
            onConfirm = { vm.deleteNote(id, onBack) },
            onDismiss = { confirmDelete = false }
        )
    }
}
