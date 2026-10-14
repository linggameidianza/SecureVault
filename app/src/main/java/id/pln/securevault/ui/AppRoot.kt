package id.pln.securevault.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.pln.securevault.SecureVaultApp
import id.pln.securevault.security.AppLock
import id.pln.securevault.ui.screens.AboutScreen
import id.pln.securevault.ui.screens.AccountsScreen
import id.pln.securevault.ui.screens.CredentialEditScreen
import id.pln.securevault.ui.screens.GeneratorScreen
import id.pln.securevault.ui.screens.HelpScreen
import id.pln.securevault.ui.screens.LockScreen
import id.pln.securevault.ui.screens.NoteEditScreen
import id.pln.securevault.ui.screens.NotesScreen
import id.pln.securevault.ui.screens.SettingsScreen
import id.pln.securevault.ui.screens.TaskEditScreen
import id.pln.securevault.ui.screens.TasksScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("accounts", "Akun", Icons.Filled.Key),
    Tab("tasks", "Tugas", Icons.Filled.TaskAlt),
    Tab("generator", "Generator", Icons.Filled.Password),
    Tab("notes", "Catatan", Icons.Filled.Description),
    Tab("settings", "Pengaturan", Icons.Filled.Settings)
)

@Composable
fun AppRoot() {
    val app = LocalContext.current.applicationContext as SecureVaultApp
    if (!AppLock.unlocked) {
        LockScreen(pinManager = app.pinManager, onUnlocked = { AppLock.unlock() })
    } else {
        MainScaffold()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainScaffold(vm: VaultViewModel = viewModel()) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val notifier = (LocalContext.current.applicationContext as SecureVaultApp).expiryNotifier
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) notifier.enabled = true }
    LaunchedEffect(Unit) {
        // Minta izin notifikasi (Android 13+) satu kali saja
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notifier.permissionAsked && !notifier.hasPermission()
        ) {
            notifier.permissionAsked = true
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = tabs.any { it.route == route }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 8.dp
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "accounts",
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            composable("accounts") {
                AccountsScreen(vm, onOpen = { id -> nav.navigate("credential/$id") })
            }
            composable(
                "credential/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                CredentialEditScreen(
                    vm = vm,
                    id = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() }
                )
            }
            composable("tasks") {
                TasksScreen(vm, onOpen = { id -> nav.navigate("task/$id") })
            }
            composable(
                "task/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                TaskEditScreen(
                    vm = vm,
                    id = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() }
                )
            }
            composable("generator") { GeneratorScreen(vm) }
            composable("notes") {
                NotesScreen(vm, onOpen = { id -> nav.navigate("note/$id") })
            }
            composable(
                "note/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                NoteEditScreen(
                    vm = vm,
                    id = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    vm = vm,
                    onOpenHelp = { nav.navigate("help") },
                    onOpenAbout = { nav.navigate("about") }
                )
            }
            composable("help") { HelpScreen(onBack = { nav.popBackStack() }) }
            composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
