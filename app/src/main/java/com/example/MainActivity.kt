package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.data.database.AppDatabase
import com.example.data.repository.ContactRepository
import com.example.data.sync.CallLogTracker
import com.example.data.ui.viewmodel.MainViewModel
import com.example.ui.navigation.Screen
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val callLogTracker = CallLogTracker(
            context = applicationContext,
            contactDao = database.contactDao(),
            tagDao = database.tagDao(),
            interactionLogDao = database.interactionLogDao()
        )
        val backupManager = com.example.data.sync.BackupManager(
            contactDao = database.contactDao(),
            tagDao = database.tagDao(),
            interactionLogDao = database.interactionLogDao()
        )
        val repository = ContactRepository(
            contactDao = database.contactDao(),
            tagDao = database.tagDao(),
            interactionLogDao = database.interactionLogDao(),
            callLogTracker = callLogTracker,
            backupManager = backupManager
        )
        val factory = MainViewModel.Factory(repository)

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val hasCallLogPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
                val hasContactsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                val initialDestination = if (hasCallLogPermission && hasContactsPermission) Screen.Agenda.route else Screen.Settings.route

                val viewModel: MainViewModel = viewModel(factory = factory)
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val snackbarHostState = remember { SnackbarHostState() }
                val userMessage by viewModel.userMessage.collectAsState()

                LaunchedEffect(userMessage) {
                    userMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearUserMessage()
                    }
                }

                val showBottomBar = currentRoute in listOf(
                    Screen.Agenda.route,
                    Screen.Contacts.route,
                    Screen.Tags.route,
                    Screen.Settings.route
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentRoute == Screen.Agenda.route,
                                    onClick = {
                                        navController.navigate(Screen.Agenda.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (currentRoute == Screen.Agenda.route) Icons.Filled.Call else Icons.Outlined.Call,
                                            contentDescription = "Agenda"
                                        )
                                    },
                                    label = { Text("Agenda") }
                                )

                                NavigationBarItem(
                                    selected = currentRoute == Screen.Contacts.route,
                                    onClick = {
                                        navController.navigate(Screen.Contacts.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (currentRoute == Screen.Contacts.route) Icons.Filled.FormatListBulleted else Icons.Outlined.FormatListBulleted,
                                            contentDescription = "Contacts"
                                        )
                                    },
                                    label = { Text("Contacts") }
                                )

                                NavigationBarItem(
                                    selected = currentRoute == Screen.Tags.route,
                                    onClick = {
                                        navController.navigate(Screen.Tags.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (currentRoute == Screen.Tags.route) Icons.Filled.Label else Icons.Outlined.Label,
                                            contentDescription = "Tags"
                                        )
                                    },
                                    label = { Text("Tags") }
                                )

                                NavigationBarItem(
                                    selected = currentRoute == Screen.Settings.route,
                                    onClick = {
                                        navController.navigate(Screen.Settings.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (currentRoute == Screen.Settings.route) Icons.Filled.Settings else Icons.Outlined.Settings,
                                            contentDescription = "Settings"
                                        )
                                    },
                                    label = { Text("Settings") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = initialDestination,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Agenda.route) {
                            DailyAgendaScreen(
                                viewModel = viewModel,
                                onContactClick = { contactId ->
                                    navController.navigate(Screen.ContactDetail.createRoute(contactId))
                                }
                            )
                        }

                        composable(Screen.Contacts.route) {
                            ContactsListScreen(
                                viewModel = viewModel,
                                onContactClick = { contactId ->
                                    navController.navigate(Screen.ContactDetail.createRoute(contactId))
                                }
                            )
                        }

                        composable(Screen.Tags.route) {
                            TagsManagementScreen(
                                viewModel = viewModel
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = viewModel
                            )
                        }

                        composable(
                            route = Screen.ContactDetail.route,
                            arguments = listOf(navArgument("contactId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val contactId = backStackEntry.arguments?.getLong("contactId") ?: 0L
                            ContactDetailScreen(
                                contactId = contactId,
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
