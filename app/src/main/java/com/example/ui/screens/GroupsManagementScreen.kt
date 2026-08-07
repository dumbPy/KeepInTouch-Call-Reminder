package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.GroupEntity
import com.example.data.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsManagementScreen(viewModel: MainViewModel) {
    val groups by viewModel.allGroups.collectAsState()
    val allContacts by viewModel.allContactsWithDetails.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedGroupForEdit by remember { mutableStateOf<GroupEntity?>(null) }
    var groupForMemberManagement by remember { mutableStateOf<GroupEntity?>(null) }
    var groupForDeleteConfirmation by remember { mutableStateOf<GroupEntity?>(null) }

    if (groupForMemberManagement != null) {
        // Render Member Management Screen
        GroupMemberManagementView(
            group = groupForMemberManagement!!,
            allContacts = allContacts,
            onBack = { groupForMemberManagement = null },
            onUpdateMembers = { selectedContactIds ->
                viewModel.updateContactsForGroup(groupForMemberManagement!!.id, selectedContactIds)
                groupForMemberManagement = null
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Groups Manager", fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        selectedGroupForEdit = null
                        showAddEditDialog = true
                    },
                    modifier = Modifier.testTag("add_group_fab"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Group")
                }
            }
        ) { padding ->
            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No groups created yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tap the + button to create your first group",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(groups, key = { it.id }) { group ->
                        GroupCard(
                            group = group,
                            memberCount = allContacts.count { it.contact.groupId == group.id },
                            onEdit = {
                                selectedGroupForEdit = group
                                showAddEditDialog = true
                            },
                            onDelete = { groupForDeleteConfirmation = group },
                            onManageMembers = { groupForMemberManagement = group }
                        )
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditGroupDialog(
            group = selectedGroupForEdit,
            onDismiss = { showAddEditDialog = false },
            onSave = { name, freq, priority, color ->
                if (selectedGroupForEdit == null) {
                    viewModel.addGroup(name, freq, priority, color)
                } else {
                    viewModel.updateGroup(
                        selectedGroupForEdit!!.copy(
                            name = name,
                            defaultFrequencyDays = freq,
                            defaultPriority = priority,
                            colorHex = color
                        )
                    )
                }
                showAddEditDialog = false
            }
        )
    }

    if (groupForDeleteConfirmation != null) {
        DeleteGroupConfirmationDialog(
            group = groupForDeleteConfirmation!!,
            onDismiss = { groupForDeleteConfirmation = null },
            onConfirm = {
                viewModel.deleteGroup(groupForDeleteConfirmation!!)
                groupForDeleteConfirmation = null
            }
        )
    }
}

@Composable
fun GroupCard(
    group: GroupEntity,
    memberCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageMembers: () -> Unit
) {
    val themeColor = try {
        Color(android.graphics.Color.parseColor(group.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("group_card_${group.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(themeColor)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (group.defaultPriority == 3) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "High Priority Group",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_group_button_${group.id}")
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Group",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_group_button_${group.id}")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Group",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        // Frequency Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "${group.defaultFrequencyDays}d",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        // Priority Pill
                        val priorityColor = when (group.defaultPriority) {
                            1 -> MaterialTheme.colorScheme.outline
                            3 -> Color(0xFFFFC107)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val priorityLabel = when (group.defaultPriority) {
                            1 -> "Low"
                            3 -> "High"
                            else -> "Normal"
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = priorityColor.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, priorityColor.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (group.defaultPriority == 3) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = priorityColor
                                    )
                                }
                                Text(
                                    text = priorityLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (group.defaultPriority == 3) MaterialTheme.colorScheme.onSurface else priorityColor
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$memberCount member(s)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onManageMembers,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    modifier = Modifier.testTag("manage_members_${group.id}")
                ) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Members")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditGroupDialog(
    group: GroupEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, defaultFrequency: Int, defaultPriority: Int, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf(group?.name ?: "") }
    var frequencyDaysStr by remember { mutableStateOf(group?.defaultFrequencyDays?.toString() ?: "14") }
    var priority by remember { mutableStateOf(group?.defaultPriority ?: 2) } // default Normal
    var selectedColor by remember { mutableStateOf(group?.colorHex ?: "#2196F3") }

    val colorsList = listOf(
        "#2196F3", // Blue
        "#4CAF50", // Green
        "#FF9800", // Orange
        "#E91E63", // Pink/Red
        "#9C27B0", // Purple
        "#009688", // Teal
        "#795548", // Brown
        "#607D8B"  // Grey
    )

    var nameError by remember { mutableStateOf(false) }
    var frequencyError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "Add Group" else "Edit Group") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError = it.isBlank()
                        },
                        label = { Text("Group Name") },
                        isError = nameError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_name_input"),
                        singleLine = true
                    )
                    if (nameError) {
                        Text(
                            "Name cannot be empty",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = frequencyDaysStr,
                        onValueChange = {
                            frequencyDaysStr = it
                            val parsed = it.toIntOrNull()
                            frequencyError = parsed == null || parsed <= 0
                        },
                        label = { Text("Reminder frequency (Days)") },
                        isError = frequencyError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_freq_input"),
                        singleLine = true
                    )
                    if (frequencyError) {
                        Text(
                            "Please enter a valid positive number",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Common presets:", style = MaterialTheme.typography.bodySmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        listOf("7" to "Weekly", "14" to "Bi-weekly", "30" to "Monthly").forEach { (valStr, label) ->
                            FilterChip(
                                selected = frequencyDaysStr == valStr,
                                onClick = {
                                    frequencyDaysStr = valStr
                                    frequencyError = false
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                item {
                    Text("Default Priority Level", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(1 to "Low", 2 to "Normal", 3 to "High").forEach { (level, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { priority = level }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = priority == level,
                                    onClick = { priority = level }
                                )
                                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                item {
                    Text("Theme Color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        colorsList.forEach { colorHex ->
                            val colorVal = Color(android.graphics.Color.parseColor(colorHex))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(colorVal)
                                    .clickable { selectedColor = colorHex }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == colorHex) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedFreq = frequencyDaysStr.toIntOrNull()
                    if (name.isBlank()) {
                        nameError = true
                    }
                    if (parsedFreq == null || parsedFreq <= 0) {
                        frequencyError = true
                    }
                    if (name.isNotBlank() && parsedFreq != null && parsedFreq > 0) {
                        onSave(name.trim(), parsedFreq, priority, selectedColor)
                    }
                },
                modifier = Modifier.testTag("save_group_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMemberManagementView(
    group: GroupEntity,
    allContacts: List<com.example.data.dto.ContactWithDetails>,
    onBack: () -> Unit,
    onUpdateMembers: (List<Long>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedContactIds = remember {
        mutableStateListOf<Long>().apply {
            addAll(allContacts.filter { it.contact.groupId == group.id }.map { it.contact.id })
        }
    }

    val filteredContacts = remember(allContacts, searchQuery) {
        allContacts.filter {
            it.contact.name.contains(searchQuery, ignoreCase = true) ||
                    it.contact.phoneNumber.contains(searchQuery)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(group.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Manage Members", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { onUpdateMembers(selectedContactIds.toList()) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_members_button")
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search contacts...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("members_search_input"),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            if (filteredContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No contacts found", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredContacts, key = { it.contact.id }) { item ->
                        val isChecked = selectedContactIds.contains(item.contact.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) {
                                        selectedContactIds.remove(item.contact.id)
                                    } else {
                                        selectedContactIds.add(item.contact.id)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = item.contact.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = item.contact.phoneNumber,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.contact.groupId != null && item.contact.groupId != group.id) {
                                    Text(
                                        text = "In another group",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked == true) {
                                        selectedContactIds.add(item.contact.id)
                                    } else {
                                        selectedContactIds.remove(item.contact.id)
                                    }
                                }
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteGroupConfirmationDialog(
    group: GroupEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    // 5 seconds delay
    val totalTimeMs = 5000f
    var timeLeftMs by remember { mutableStateOf(totalTimeMs) }
    
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (timeLeftMs > 0) {
            val elapsed = System.currentTimeMillis() - startTime
            timeLeftMs = (totalTimeMs - elapsed).coerceAtLeast(0f)
            kotlinx.coroutines.delay(16) // ~60fps
        }
    }
    
    val progress = ((totalTimeMs - timeLeftMs) / totalTimeMs).coerceIn(0f, 1f)
    val secondsRemaining = (timeLeftMs / 1000f).coerceAtLeast(0f)
    val isButtonEnabled = timeLeftMs <= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Deletion", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Are you sure you want to delete the group \"${group.name}\"?",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "This action is irreversible. Contacts in this group will not be deleted but will become unassigned.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Progress Indicator
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.errorContainer
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isButtonEnabled) "Ready to delete" else "Verifying request...",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isButtonEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isButtonEnabled) {
                            Text(
                                text = "%.1fs".format(secondsRemaining),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = isButtonEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                    disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.5f)
                ),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text(if (isButtonEnabled) "Delete Group" else "Hold on...")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
