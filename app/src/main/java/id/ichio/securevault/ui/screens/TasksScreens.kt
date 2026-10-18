@file:OptIn(ExperimentalMaterial3Api::class)

package id.ichio.securevault.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import id.ichio.securevault.data.SubtaskDraft
import id.ichio.securevault.data.TaskForm
import id.ichio.securevault.data.TaskWithSubtasks
import id.ichio.securevault.ui.VaultViewModel
import id.ichio.securevault.ui.theme.GradientFab
import id.ichio.securevault.ui.theme.GradientHeader
import id.ichio.securevault.ui.theme.IconTile
import id.ichio.securevault.ui.theme.SectionCard
import id.ichio.securevault.ui.theme.StatPill
import id.ichio.securevault.ui.theme.Sun
import id.ichio.securevault.ui.theme.Teal
import id.ichio.securevault.ui.theme.Violet
import id.ichio.securevault.ui.theme.accentFor
import id.ichio.securevault.util.TaskTime

// =====================================================================
// Daftar tugas
// =====================================================================

@Composable
fun TasksScreen(vm: VaultViewModel, onOpen: (Long) -> Unit) {
    val tasks by vm.tasks.collectAsState()
    val showDone by vm.showDoneTasks.collectAsState()
    val active by vm.activeTaskCount.collectAsState()
    val done by vm.doneTaskCount.collectAsState()

    Scaffold(
        topBar = {
            GradientHeader(title = "Tasks", subtitle = "Checklist & reminders") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    StatPill(
                        value = "$active",
                        label = "Active",
                        selected = !showDone,
                        onClick = { vm.showDoneTasks.value = false },
                        modifier = Modifier.weight(1f)
                    )
                    StatPill(
                        value = "$done",
                        label = "Done",
                        selected = showDone,
                        onClick = { vm.showDoneTasks.value = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        floatingActionButton = { GradientFab("New task", Icons.Filled.AddTask) { onOpen(0L) } }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (tasks.isEmpty()) {
                EmptyState(
                    if (showDone) "No completed tasks yet."
                    else "No active tasks 🎉\nTap \"New task\" to add one."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tasks, key = { it.task.id }) { item ->
                        TaskCard(
                            item = item,
                            onToggle = { vm.toggleTask(item.task) },
                            onToggleSub = { vm.toggleSubtask(it) },
                            onOpen = { onOpen(item.task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    item: TaskWithSubtasks,
    onToggle: () -> Unit,
    onToggleSub: (id.ichio.securevault.data.SubtaskEntity) -> Unit,
    onOpen: () -> Unit
) {
    val task = item.task
    val accent = accentFor(task.title)
    val faded = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundCheck(checked = task.done, color = accent, onClick = onToggle)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (task.done) faded else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (task.done) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = task.reminderAt != null || item.subtasks.isNotEmpty()
                    if (meta) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            task.reminderAt?.let { at ->
                                val overdue = !task.done && at <= System.currentTimeMillis()
                                MetaChip(
                                    icon = Icons.Filled.Alarm,
                                    text = TaskTime.relative(at),
                                    color = if (overdue) MaterialTheme.colorScheme.error else Violet
                                )
                            }
                            if (item.subtasks.isNotEmpty()) {
                                MetaChip(
                                    icon = Icons.Filled.Checklist,
                                    text = "${item.doneCount}/${item.subtasks.size}",
                                    color = Teal
                                )
                            }
                        }
                    }
                }
            }

            if (item.subtasks.isNotEmpty() && !task.done) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { item.doneCount.toFloat() / item.subtasks.size },
                    color = accent,
                    trackColor = accent.copy(alpha = 0.18f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.height(6.dp))
                item.subtasks.forEach { sub ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleSub(sub) }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                        RoundCheck(checked = sub.done, color = accent, size = 20.dp, onClick = { onToggleSub(sub) })
                        Text(
                            sub.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (sub.done) faded else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (sub.done) TextDecoration.LineThrough else null,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** Checkbox bulat berwarna. */
@Composable
fun RoundCheck(checked: Boolean, color: Color, size: Dp = 26.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (checked) color else Color.Transparent)
            .border(BorderStroke(2.dp, color), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Done",
                tint = Color.White,
                modifier = Modifier.size(size * 0.7f)
            )
        }
    }
}

// =====================================================================
// Tambah / edit tugas
// =====================================================================

@Composable
fun TaskEditScreen(vm: VaultViewModel, id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    var form by remember { mutableStateOf<TaskForm?>(null) }
    var newSub by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val isNew = id <= 0L

    LaunchedEffect(id) { form = vm.loadTask(id) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.message("Notification permission denied — reminders won't appear")
    }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun addSubtask() {
        val f = form ?: return
        val t = newSub.trim()
        if (t.isEmpty()) return
        form = f.copy(subtasks = f.subtasks + SubtaskDraft(t))
        newSub = ""
    }

    Scaffold(
        topBar = {
            GradientHeader(
                title = if (isNew) "New Task" else "Edit Task",
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
                    IconButton(onClick = { form?.let { vm.saveTask(it.withPendingSub(newSub), onBack) } }) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- Judul & catatan ----
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = current.title,
                        onValueChange = { form = current.copy(title = it) },
                        label = { Text("Task title *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = current.notes,
                        onValueChange = { form = current.copy(notes = it) },
                        label = { Text("Notes (optional)") },
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { form = current.copy(done = !current.done) }
                            .padding(vertical = 6.dp)
                    ) {
                        RoundCheck(current.done, Teal, onClick = { form = current.copy(done = !current.done) })
                        Text(
                            if (current.done) "Task completed" else "Mark task done",
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }

            // ---- Subtask ----
            SectionCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Filled.Checklist, Teal, size = 36.dp)
                        Text(
                            "Subtask",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        )
                        if (current.subtasks.isNotEmpty()) {
                            Text(
                                "${current.subtasks.count { it.done }}/${current.subtasks.size} done",
                                style = MaterialTheme.typography.labelLarge,
                                color = Teal
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    current.subtasks.forEachIndexed { index, sub ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            RoundCheck(sub.done, Teal, size = 22.dp, onClick = {
                                form = current.copy(
                                    subtasks = current.subtasks.toMutableList().also {
                                        it[index] = sub.copy(done = !sub.done)
                                    }
                                )
                            })
                            BasicTextField(
                                value = sub.title,
                                onValueChange = { v ->
                                    form = current.copy(
                                        subtasks = current.subtasks.toMutableList().also {
                                            it[index] = sub.copy(title = v)
                                        }
                                    )
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (sub.done) TextDecoration.LineThrough else null
                                ),
                                cursorBrush = SolidColor(Violet),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            )
                            IconButton(onClick = {
                                form = current.copy(
                                    subtasks = current.subtasks.toMutableList().also { it.removeAt(index) }
                                )
                            }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Delete subtask",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newSub,
                        onValueChange = { newSub = it },
                        placeholder = { Text("Add subtask…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { addSubtask() }),
                        trailingIcon = {
                            IconButton(onClick = { addSubtask() }, enabled = newSub.isNotBlank()) {
                                Icon(Icons.Filled.Add, contentDescription = "Add subtask")
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
            }

            // ---- Pengingat ----
            SectionCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Filled.NotificationsActive, Sun, size = 36.dp)
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text("Reminder", style = MaterialTheme.typography.titleMedium)
                            Text(
                                current.reminderAt?.let { TaskTime.relative(it) } ?: "No reminder",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = current.reminderAt != null,
                            onCheckedChange = { on ->
                                form = current.copy(reminderAt = if (on) TaskTime.nextHour() else null)
                                if (on) ensureNotificationPermission()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Violet)
                        )
                    }
                    val at = current.reminderAt
                    if (at != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            SuggestionChip(
                                onClick = { form = current.copy(reminderAt = TaskTime.inOneHour()) },
                                label = { Text("In 1 hour") }
                            )
                            SuggestionChip(
                                onClick = { form = current.copy(reminderAt = TaskTime.todayOrTomorrowAt(19)) },
                                label = { Text("Tonight 7:00 PM") }
                            )
                            SuggestionChip(
                                onClick = { form = current.copy(reminderAt = TaskTime.tomorrowAt(8)) },
                                label = { Text("Tomorrow 8:00 AM") }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1.6f)) {
                                Icon(Icons.Filled.Event, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(TaskTime.formatDate(at), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Schedule, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(TaskTime.formatTime(at))
                            }
                        }
                        if (at <= System.currentTimeMillis() && !current.done) {
                            Text(
                                "This time has already passed — choose a future time",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            Text(
                                "The notification will appear at that time, even if the app is closed.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { vm.saveTask(current.withPendingSub(newSub), onBack) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) { Text("Save task") }
            Spacer(Modifier.height(16.dp))
        }

        // ---- Dialog tanggal ----
        val at = current.reminderAt
        if (showDate && at != null) {
            val today = TaskTime.todayPickerMillis()
            val state = rememberDatePickerState(
                initialSelectedDateMillis = maxOf(TaskTime.toPickerMillis(at), today),
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= today
                }
            )
            DatePickerDialog(
                onDismissRequest = { showDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let {
                            form = current.copy(reminderAt = TaskTime.withPickedDate(at, it))
                        }
                        showDate = false
                    }) { Text("Select") }
                },
                dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
            ) { DatePicker(state = state) }
        }

        // ---- Dialog jam ----
        if (showTime && at != null) {
            val state = rememberTimePickerState(
                initialHour = TaskTime.hour(at),
                initialMinute = TaskTime.minute(at),
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { showTime = false },
                title = { Text("Select time") },
                text = { TimePicker(state = state) },
                confirmButton = {
                    TextButton(onClick = {
                        form = current.copy(reminderAt = TaskTime.withTime(at, state.hour, state.minute))
                        showTime = false
                    }) { Text("Select") }
                },
                dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } }
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete task?",
            text = "Task \"${form?.title}\" and its subtasks will be permanently deleted.",
            onConfirm = { vm.deleteTask(id, onBack) },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** Subtask yang masih diketik tapi belum ditambahkan ikut tersimpan. */
private fun TaskForm.withPendingSub(pending: String): TaskForm =
    if (pending.isBlank()) this else copy(subtasks = subtasks + SubtaskDraft(pending.trim()))
