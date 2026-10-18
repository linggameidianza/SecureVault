package id.ichio.securevault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.ichio.securevault.data.CredentialEntity
import id.ichio.securevault.ui.VaultViewModel
import id.ichio.securevault.ui.theme.GradientAvatar
import id.ichio.securevault.ui.theme.GradientFab
import id.ichio.securevault.ui.theme.GradientHeader
import id.ichio.securevault.ui.theme.HeaderSearchField
import id.ichio.securevault.ui.theme.StatPill
import id.ichio.securevault.ui.theme.Violet
import id.ichio.securevault.util.Expiry

@Composable
fun AccountsScreen(vm: VaultViewModel, onOpen: (Long) -> Unit) {
    val items by vm.credentials.collectAsState()
    val query by vm.credentialQuery.collectAsState()
    val expiringOnly by vm.expiringOnly.collectAsState()
    val attention by vm.attentionCount.collectAsState()
    val total by vm.credentialCount.collectAsState()

    Scaffold(
        topBar = {
            GradientHeader(title = "SecureVault", subtitle = "Your personal password vault") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                ) {
                    StatPill(
                        value = "$total",
                        label = "Saved accounts",
                        selected = !expiringOnly,
                        onClick = { vm.expiringOnly.value = false },
                        modifier = Modifier.weight(1f)
                    )
                    StatPill(
                        value = "$attention",
                        label = if (attention > 0) "⚠ Needs changing" else "Needs changing",
                        selected = expiringOnly,
                        onClick = { vm.expiringOnly.value = !expiringOnly },
                        modifier = Modifier.weight(1f)
                    )
                }
                HeaderSearchField(
                    value = query,
                    onChange = { vm.credentialQuery.value = it },
                    placeholder = "Search accounts, username, URL…"
                )
            }
        },
        floatingActionButton = { GradientFab("Add account", Icons.Filled.Add) { onOpen(0L) } }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (items.isEmpty()) {
                EmptyState(
                    when {
                        expiringOnly -> "Great! No expired passwords 🎉"
                        query.isBlank() -> "No saved accounts yet.\nTap \"Add account\" to get started."
                        else -> "No results for \"$query\""
                    }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (expiringOnly) {
                        item {
                            Text(
                                "Showing expired / expiring passwords",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    items(items, key = { it.id }) { item ->
                        CredentialRow(
                            item = item,
                            onClick = { onOpen(item.id) },
                            onCopyUser = { vm.copyText("Username", item.username) },
                            onCopyPassword = { vm.copyPassword(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialRow(
    item: CredentialEntity,
    onClick: () -> Unit,
    onCopyUser: () -> Unit,
    onCopyPassword: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradientAvatar(item.title)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.username.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ExpiryBadge(item.expiresAt)
            }
            FilledTonalIconButton(
                onClick = onCopyUser,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Icon(Icons.Filled.Person, contentDescription = "Copy username")
            }
            Spacer(Modifier.size(6.dp))
            FilledTonalIconButton(
                onClick = onCopyPassword,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Filled.Key, contentDescription = "Copy password")
            }
        }
    }
}

@Composable
private fun ExpiryBadge(expiresAt: Long?) {
    val status = Expiry.status(expiresAt)
    if (status != Expiry.Status.SOON && status != Expiry.Status.EXPIRED) return
    val color = if (status == Expiry.Status.EXPIRED) MaterialTheme.colorScheme.error else Color(0xFFF57C00)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 4.dp)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(
            Expiry.describe(expiresAt).substringBefore(" ("),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun EmptyState(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(88.dp)
                    .background(Violet.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Inbox,
                    contentDescription = null,
                    tint = Violet,
                    modifier = Modifier.size(44.dp)
                )
            }
            Text(
                text,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
