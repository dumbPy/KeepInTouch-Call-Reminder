package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GroupEntity
import com.example.data.ui.viewmodel.MainViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsManagementScreen(
    viewModel: MainViewModel,
    onContactClick: (Long) -> Unit = {}
) {
    val groups by viewModel.allGroups.collectAsState()
    val allContacts by viewModel.allContactsWithDetails.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedGroupForEdit by remember { mutableStateOf<GroupEntity?>(null) }
    var groupForMemberManagementId by remember { mutableStateOf<Long?>(null) }
    var groupForDeleteConfirmation by remember { mutableStateOf<GroupEntity?>(null) }

    val activeManagedGroup = groups.find { it.id == groupForMemberManagementId }

    if (activeManagedGroup != null) {
        // Render Member Management Screen
        GroupMemberManagementView(
            group = activeManagedGroup,
            allContacts = allContacts,
            allGroups = groups,
            onBack = { groupForMemberManagementId = null },
            onEdit = {
                selectedGroupForEdit = activeManagedGroup
                showAddEditDialog = true
            },
            onDelete = {
                groupForDeleteConfirmation = activeManagedGroup
            },
            onUpdateMembers = { selectedContactIds ->
                viewModel.updateContactsForGroup(activeManagedGroup.id, selectedContactIds)
                groupForMemberManagementId = null
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
                            onManageMembers = { groupForMemberManagementId = group.id }
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
                if (groupForMemberManagementId == groupForDeleteConfirmation!!.id) {
                    groupForMemberManagementId = null
                }
                groupForDeleteConfirmation = null
            }
        )
    }
}

@Composable
fun GroupCard(
    group: GroupEntity,
    memberCount: Int,
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
            .testTag("group_card_${group.id}")
            .clickable { onManageMembers() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top row with Circle, Name, and Pills inline
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(themeColor)
                    )
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    // Frequency Pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "${group.defaultFrequencyDays}d",
                                style = MaterialTheme.typography.labelSmall,
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
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (group.defaultPriority == 3) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = priorityColor
                                )
                            }
                            Text(
                                text = priorityLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (group.defaultPriority == 3) MaterialTheme.colorScheme.onSurface else priorityColor
                            )
                        }
                    }
                }

                // Bottom part with member count
                Text(
                    text = "$memberCount member(s)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Manage Members",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
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
    allGroups: List<GroupEntity>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpdateMembers: (List<Long>) -> Unit
) {
    // We want to keep a local copy of members that are in this group
    val displayedMembers = remember(group.id) {
        mutableStateListOf<com.example.data.dto.ContactWithDetails>().apply {
            addAll(allContacts.filter { it.contact.groupId == group.id })
        }
    }

    // List of member contact IDs currently marked as deleted (Undo)
    val pendingDeletions = remember { mutableStateListOf<Long>() }

    // State to toggle the "Add Members" view
    var showAddMembersView by remember { mutableStateOf(false) }

    BackHandler(enabled = !showAddMembersView) {
        val finalMemberIds = displayedMembers
            .map { it.contact.id }
            .filter { !pendingDeletions.contains(it) }
        onUpdateMembers(finalMemberIds)
    }

    val themeColor = try {
        Color(android.graphics.Color.parseColor(group.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    if (showAddMembersView) {
        // Redesigned Add Members View
        AddMembersView(
            group = group,
            allContacts = allContacts,
            allGroups = allGroups,
            initialSelectedIds = displayedMembers.map { it.contact.id }.filter { !pendingDeletions.contains(it) },
            onBack = { showAddMembersView = false },
            onDone = { selectedIds ->
                // Sync displayedMembers and pendingDeletions with selectedIds
                pendingDeletions.clear()
                
                // Add any newly selected contacts to displayedMembers if they aren't already there
                selectedIds.forEach { id ->
                    if (displayedMembers.none { it.contact.id == id }) {
                        allContacts.find { it.contact.id == id }?.let {
                            displayedMembers.add(it)
                        }
                    }
                }
                
                // For any contact currently in displayedMembers but NOT in selectedIds, mark them as pendingDeletions
                displayedMembers.forEach { item ->
                    if (!selectedIds.contains(item.contact.id)) {
                        pendingDeletions.add(item.contact.id)
                    }
                }
                
                showAddMembersView = false
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(themeColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = group.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Group Details & Members",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                val finalMemberIds = displayedMembers
                                    .map { it.contact.id }
                                    .filter { !pendingDeletions.contains(it) }
                                onUpdateMembers(finalMemberIds)
                            },
                            modifier = Modifier.testTag("member_mgmt_back_button")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAddMembersView = true },
                    modifier = Modifier.testTag("add_members_fab"),
                    icon = { Icon(Icons.Default.PersonAdd, contentDescription = "Add Members") },
                    text = { Text("Add Members") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Show list of members
                val activeCount = displayedMembers.count { !pendingDeletions.contains(it.contact.id) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Members list ($activeCount active)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (displayedMembers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No members in this group yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap \"Add Members\" above to assign contacts to this group.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedMembers, key = { it.contact.id }) { item ->
                            val isDeleted = pendingDeletions.contains(item.contact.id)
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDeleted) {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                border = if (isDeleted) {
                                    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                } else {
                                    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                }
                            ) {
                                if (isDeleted) {
                                    // Deleted state with Undo button in exactly the same sized card
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = item.contact.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Removed from group",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                        
                                        Button(
                                            onClick = { pendingDeletions.remove(item.contact.id) },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Undo,
                                                contentDescription = "Undo",
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Undo", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        }
                                    }
                                } else {
                                    // Normal active member card
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // Avatar circle
                                            val displayPhotoUri = item.contact.avatarUri
                                            if (!displayPhotoUri.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = displayPhotoUri,
                                                    contentDescription = "${item.contact.name} avatar",
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = item.contact.name.take(1).uppercase(),
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = item.contact.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = item.contact.phoneNumber,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        
                                        IconButton(
                                            onClick = { pendingDeletions.add(item.contact.id) },
                                            modifier = Modifier.testTag("remove_member_button_${item.contact.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove member",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMembersView(
    group: GroupEntity,
    allContacts: List<com.example.data.dto.ContactWithDetails>,
    allGroups: List<GroupEntity>,
    initialSelectedIds: List<Long>,
    onBack: () -> Unit,
    onDone: (List<Long>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val selectedContactIds = remember {
        mutableStateListOf<Long>().apply {
            addAll(initialSelectedIds)
        }
    }

    BackHandler {
        onDone(selectedContactIds.toList())
    }

    // Filter contacts based on query
    val filteredContacts = remember(allContacts, searchQuery) {
        allContacts.filter {
            it.contact.name.contains(searchQuery, ignoreCase = true) ||
                    it.contact.phoneNumber.contains(searchQuery)
        }
    }

    // Filter groups matching search query (excluding the current group)
    val matchedGroups = remember(allGroups, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            allGroups.filter {
                it.id != group.id && it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Add Members", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Select contacts for ${group.name}", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onDone(selectedContactIds.toList()) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by name, number, or other groups...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("add_members_search_input"),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Section 1: Matching Groups/Tags
                if (matchedGroups.isNotEmpty()) {
                    item {
                        Text(
                            text = "Matching Groups (Click to select all members)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    
                    items(matchedGroups, key = { "group_${it.id}" }) { otherGroup ->
                        val groupMembers = allContacts.filter { it.contact.groupId == otherGroup.id }
                        val memberCount = groupMembers.size
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Select all members of this group
                                        groupMembers.forEach { m ->
                                            if (!selectedContactIds.contains(m.contact.id)) {
                                                selectedContactIds.add(m.contact.id)
                                            }
                                        }
                                        // Clear filter
                                        searchQuery = ""
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(otherGroup.colorHex)).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        tint = Color(android.graphics.Color.parseColor(otherGroup.colorHex))
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Select all in \"${otherGroup.name}\"",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "$memberCount member(s)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Select group members",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                // Section 2: Contacts list
                item {
                    Text(
                        text = if (searchQuery.isBlank()) "All Contacts" else "Search Results",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                if (filteredContacts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No contacts found",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    items(filteredContacts, key = { it.contact.id }) { item ->
                        val isSelected = selectedContactIds.contains(item.contact.id)
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) {
                                            selectedContactIds.remove(item.contact.id)
                                        } else {
                                            selectedContactIds.add(item.contact.id)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Avatar circle with tick overlay or replacement when selected
                                    Box(
                                        modifier = Modifier.size(42.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            // Show elegant green/primary circle with tick mark
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        } else {
                                            // Show regular avatar
                                            val displayPhotoUri = item.contact.avatarUri
                                            if (!displayPhotoUri.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = displayPhotoUri,
                                                    contentDescription = "${item.contact.name} avatar",
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = item.contact.name.take(1).uppercase(),
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Column {
                                        Text(
                                            text = item.contact.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = item.contact.phoneNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        
                                        // Show existing group info if any
                                        if (item.contact.groupId != null) {
                                            val contactGroup = allGroups.find { it.id == item.contact.groupId }
                                            if (contactGroup != null) {
                                                val cGroupColor = try {
                                                    Color(android.graphics.Color.parseColor(contactGroup.colorHex))
                                                } catch (e: Exception) {
                                                    MaterialTheme.colorScheme.primary
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(cGroupColor)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (item.contact.groupId == group.id) "Already in this group" else "In group: ${contactGroup.name}",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (item.contact.groupId == group.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
