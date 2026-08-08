package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.example.data.ui.viewmodel.MainViewModel
import com.example.utils.NotificationHelper

enum class ScheduleMode(val displayName: String) {
    DAILY("Daily (Same Time)"),
    CUSTOM("Custom (Per Day Schedule)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val agendaList by viewModel.dailyAgendaList.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var callLogPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        )
    }

    var contactsPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var writeContactsPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var notificationsPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val allPermissionsGranted = callLogPermissionGranted && contactsPermissionGranted && notificationsPermissionGranted
    var isPermissionsExpanded by remember { mutableStateOf(!allPermissionsGranted) }

    LaunchedEffect(allPermissionsGranted) {
        if (allPermissionsGranted) {
            isPermissionsExpanded = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        callLogPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        contactsPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        writeContactsPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        if (contactsPermissionGranted) {
            viewModel.syncFullContactsAndCallLogs()
        }
    }

    fun openSystemAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    // Schedule State
    var scheduleMode by remember { mutableStateOf(ScheduleMode.DAILY) }
    var dailyTime by remember { mutableStateOf("10:00 AM") }

    // Custom Day Schedule State
    val daysOfWeek = remember { listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday") }
    val customScheduleMap = remember {
        mutableStateMapOf(
            "Monday" to mutableStateListOf("09:00 AM", "02:00 PM"),
            "Tuesday" to mutableStateListOf("10:00 AM"),
            "Wednesday" to mutableStateListOf("10:00 AM"),
            "Thursday" to mutableStateListOf("10:00 AM"),
            "Friday" to mutableStateListOf("05:00 PM"),
            "Saturday" to mutableStateListOf<String>(),
            "Sunday" to mutableStateListOf<String>()
        )
    }

    var showAddTimeForDay by remember { mutableStateOf<String?>(null) }
    var showCustomDailyTimePicker by remember { mutableStateOf(false) }

    val presetTimes = listOf("08:00 AM", "09:00 AM", "10:00 AM", "12:00 PM", "02:00 PM", "05:00 PM", "07:00 PM", "09:00 PM")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Sync", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isPermissionsExpanded = !isPermissionsExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "System Permissions",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (allPermissionsGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (allPermissionsGranted) "Granted" else "Needs Action",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (allPermissionsGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { openSystemAppSettings() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Settings,
                                        contentDescription = "Open System App Settings",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { isPermissionsExpanded = !isPermissionsExpanded },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPermissionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Toggle permissions details",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = isPermissionsExpanded) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = "Tap a permission to grant it or open system app settings page directly.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // Call Log Permission
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (!callLogPermissionGranted) {
                                                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG))
                                            } else {
                                                openSystemAppSettings()
                                            }
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    ListItem(
                                        headlineContent = { Text("Call Log Auto-Tracking", fontWeight = FontWeight.SemiBold) },
                                        supportingContent = { Text("READ_CALL_LOG — Detects calls & updates countdowns automatically.") },
                                        trailingContent = {
                                            if (callLogPermissionGranted) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Granted", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Button(
                                                    onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG)) },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Grant", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Contacts Permission
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (!contactsPermissionGranted) {
                                                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                                            } else {
                                                openSystemAppSettings()
                                            }
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    ListItem(
                                        headlineContent = { Text("Contacts Integration", fontWeight = FontWeight.SemiBold) },
                                        supportingContent = { Text("READ_CONTACTS — Syncs names and numbers from phonebook.") },
                                        trailingContent = {
                                            if (contactsPermissionGranted) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Granted", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Button(
                                                    onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS)) },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Grant", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    )
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                if (!notificationsPermissionGranted) {
                                                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                                } else {
                                                    openSystemAppSettings()
                                                }
                                            },
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ) {
                                        ListItem(
                                            headlineContent = { Text("Push Notifications", fontWeight = FontWeight.SemiBold) },
                                            supportingContent = { Text("POST_NOTIFICATIONS — Displays daily call agenda reminders.") },
                                            trailingContent = {
                                                if (notificationsPermissionGranted) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Granted", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) },
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("Grant", fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Write Contacts Permission (Optional)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (!writeContactsPermissionGranted) {
                                                permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_CONTACTS))
                                            } else {
                                                openSystemAppSettings()
                                            }
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    ListItem(
                                        headlineContent = { Text("Write Contacts (Optional)", fontWeight = FontWeight.SemiBold) },
                                        supportingContent = { Text("WRITE_CONTACTS — Optional. Updates preferred/default numbers back in system Contacts.") },
                                        trailingContent = {
                                            if (writeContactsPermissionGranted) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Granted", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Button(
                                                    onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_CONTACTS)) },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Grant", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Daily Agenda Notification Schedule
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Agenda Notification Schedule", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Configure when and how often you receive your daily call agenda digest notification.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Schedule Mode Selection Segmented Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = scheduleMode == ScheduleMode.DAILY,
                                onClick = { scheduleMode = ScheduleMode.DAILY },
                                label = { Text("Daily (Every Day)") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = scheduleMode == ScheduleMode.CUSTOM,
                                onClick = { scheduleMode = ScheduleMode.CUSTOM },
                                label = { Text("Custom (Per Day)") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (scheduleMode == ScheduleMode.DAILY) {
                            // Daily Mode Settings
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text("Select Daily Notification Time:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    presetTimes.forEach { time ->
                                        FilterChip(
                                            selected = dailyTime == time,
                                            onClick = { dailyTime = time },
                                            label = { Text(time) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Current setting: Notification will trigger every day at $dailyTime.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            // Custom Per-Day Mode Settings
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Configure specific times per day of week (e.g. 9 AM & 2 PM on Monday, None on weekends):",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                daysOfWeek.forEach { day ->
                                    val timesList = customScheduleMap[day] ?: remember { mutableStateListOf() }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (timesList.isNotEmpty())
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = day,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = if (timesList.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                TextButton(onClick = { showAddTimeForDay = day }) {
                                                    Icon(Icons.Default.Add, contentDescription = "Add time", modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Add Time", fontSize = 12.sp)
                                                }
                                            }

                                            if (timesList.isEmpty()) {
                                                Text(
                                                    text = "No notifications scheduled for $day",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    timesList.forEach { time ->
                                                        InputChip(
                                                            selected = true,
                                                            onClick = { },
                                                            label = { Text(time, fontSize = 12.sp) },
                                                            trailingIcon = {
                                                                Icon(
                                                                    Icons.Default.Close,
                                                                    contentDescription = "Remove $time",
                                                                    modifier = Modifier
                                                                        .size(14.dp)
                                                                        .clickable { timesList.remove(time) }
                                                                )
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Test Notification Button
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsPermissionGranted) {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                } else {
                                    val topNames = agendaList.take(5).map { it.contact.name }
                                    NotificationHelper.sendAgendaTestNotification(
                                        context = context,
                                        pendingCount = agendaList.size,
                                        contactNames = topNames
                                    )
                                    Toast.makeText(context, "Test Agenda Notification sent to device shade!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send Test Agenda Notification Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Sync Actions
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Database & Call Sync Maintenance", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.syncFullContactsAndCallLogs() },
                            enabled = !isSyncing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Syncing System Contacts & Call Logs...")
                            } else {
                                Icon(Icons.Default.PhoneInTalk, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync System Contacts & Call Logs Now")
                            }
                        }
                    }
                }
            }

            // Data Backup & Restore (Storage Access Framework Export/Import)
            item {
                val coroutineScope = rememberCoroutineScope()

                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri: Uri? ->
                    uri?.let { saveUri ->
                        coroutineScope.launch {
                            try {
                                val json = viewModel.getExportJsonString()
                                context.contentResolver.openOutputStream(saveUri)?.use { outputStream ->
                                    outputStream.write(json.toByteArray(Charsets.UTF_8))
                                }
                                Toast.makeText(context, "Backup successfully exported and saved!", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let { fileUri ->
                        try {
                            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                                val jsonString = inputStream.bufferedReader().use { it.readText() }
                                viewModel.importBackupJson(jsonString)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to read backup file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Data Backup & Recovery", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Local & Cloud Backup (Storage Access Framework)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        "Export or restore standalone backup files using Android's native file picker. Save directly to your internal storage, SD card, or Google Drive folder.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Manual JSON Backup & Restore:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Export a standalone .json backup file or restore contacts, groups & history from a file.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    exportLauncher.launch("KeepInTouch_Backup.json")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export Backup", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import Backup", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for adding custom time to a day
    showAddTimeForDay?.let { day ->
        val currentList = customScheduleMap[day] ?: remember { mutableStateListOf() }

        AlertDialog(
            onDismissRequest = { showAddTimeForDay = null },
            title = { Text("Add Notification Time for $day") },
            text = {
                Column {
                    Text("Select time slot to add:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    presetTimes.forEach { time ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (!currentList.contains(time)) {
                                        currentList.add(time)
                                    }
                                    showAddTimeForDay = null
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(time, fontWeight = FontWeight.SemiBold)
                            if (currentList.contains(time)) {
                                Icon(Icons.Default.Check, contentDescription = "Already Added", tint = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Divider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddTimeForDay = null }) {
                    Text("Done")
                }
            }
        )
    }
}
