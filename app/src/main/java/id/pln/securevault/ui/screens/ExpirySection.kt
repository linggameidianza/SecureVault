@file:OptIn(ExperimentalMaterial3Api::class)

package id.pln.securevault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.pln.securevault.data.CredentialForm
import id.pln.securevault.util.Expiry

/** Pengaturan masa berlaku password di form akun. */
@Composable
fun ExpirySection(form: CredentialForm, onChange: (CredentialForm) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val selectionLabel = when {
        form.expiresAt == null -> "Tidak ada masa berlaku"
        form.expiryDays > 0 -> "Ganti setiap ${form.expiryDays} hari"
        else -> "Tanggal tertentu: ${form.expiresAt?.let { Expiry.formatDate(it) }}"
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Masa berlaku password", style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(selectionLabel, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Tidak ada masa berlaku") },
                    onClick = {
                        menuOpen = false
                        onChange(form.copy(expiresAt = null, expiryDays = 0))
                    }
                )
                Expiry.PRESET_DAYS.forEach { days ->
                    DropdownMenuItem(
                        text = { Text("Ganti setiap $days hari") },
                        onClick = {
                            menuOpen = false
                            onChange(
                                form.copy(
                                    expiresAt = System.currentTimeMillis() + days * Expiry.DAY_MS,
                                    expiryDays = days
                                )
                            )
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Pilih tanggal…") },
                    onClick = {
                        menuOpen = false
                        showDatePicker = true
                    }
                )
            }
        }

        if (form.expiresAt != null) {
            val status = Expiry.status(form.expiresAt)
            val color = when (status) {
                Expiry.Status.EXPIRED -> MaterialTheme.colorScheme.error
                Expiry.Status.SOON -> Color(0xFFE65100)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status == Expiry.Status.EXPIRED || status == Expiry.Status.SOON) {
                    Icon(Icons.Filled.Warning, null, tint = color, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                }
                Text(Expiry.describe(form.expiresAt), style = MaterialTheme.typography.bodySmall, color = color)
            }
            Text(
                if (form.expiryDays > 0)
                    "Saat password diganti, masa berlaku otomatis dihitung ulang ${form.expiryDays} hari dari hari itu. " +
                        "Notifikasi muncul ${Expiry.WARN_DAYS} hari sebelum kedaluwarsa."
                else
                    "Notifikasi muncul ${Expiry.WARN_DAYS} hari sebelum kedaluwarsa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDatePicker) {
        val todayPicker = Expiry.toPickerMillis(System.currentTimeMillis())
        val state = rememberDatePickerState(
            initialSelectedDateMillis = form.expiresAt?.let { maxOf(Expiry.toPickerMillis(it), todayPicker) }
                ?: (todayPicker + 90 * Expiry.DAY_MS),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayPicker
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let {
                            onChange(form.copy(expiresAt = Expiry.endOfLocalDayFromPicker(it), expiryDays = 0))
                        }
                        showDatePicker = false
                    }
                ) { Text("Pilih") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Batal") } }
        ) {
            DatePicker(state = state)
        }
    }
}
