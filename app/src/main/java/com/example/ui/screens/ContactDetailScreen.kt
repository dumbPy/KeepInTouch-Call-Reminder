package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.dto.ContactWithDetails
import com.example.data.model.InteractionLogEntity
import com.example.data.model.InteractionType
import com.example.data.model.TagCategory
import com.example.data.ui.viewmodel.MainViewModel
import com.example.ui.components.TagChip
import com.example.ui.theme.OverdueRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contactId: Long,
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val contactDetailState by viewModel.getContactDetailsFlow(contactId).collectAsState(initial = null)
    val logsState by viewModel.getContactLogsFlow(contactId).collectAsState(initial = emptyList())
    val allTags by viewModel.allTags.collectAsState()

    var showAddLogDialog by remember { mutableStateOf(false) }
    var showEditTagsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contactDetailState?.contact?.name ?: "Contact Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    contactDetailState?.contact?.let { contact ->
                        IconButton(onClick = { viewModel.deleteContact(contact); onBackClick() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Contact", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (contactDetailState == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val item = contactDetailState!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Header Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.contact.name.take(1).uppercase(),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = item.contact.name,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = item.contact.phoneNumber,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!item.contact.notes.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.contact.notes,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Cadence Info Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Recurrence Frequency", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${item.resolvedFrequencyDays()} Days", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }

                                val days = item.daysUntilDue()
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Status", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = if (days < 0) "${-days}d Overdue" else "Due in ${days}d",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (days < 0) OverdueRed else SuccessGreen
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Primary Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:${item.contact.phoneNumber}")
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Call Now")
                                }

                                OutlinedButton(
                                    onClick = { showAddLogDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Log Call")
                                }
                            }
                        }
                    }
                }

                // Tags Section
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Assigned Tags", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            TextButton(onClick = { showEditTagsDialog = true }) {
                                Text("Edit Tags")
                            }
                        }

                        if (item.tags.isEmpty()) {
                            Text(
                                text = "No tags assigned. Assign a Frequency or Group tag to set custom reminders.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item.tags.forEach { tag ->
                                    TagChip(tag = tag)
                                }
                            }
                        }
                    }
                }

                // Timeline History Section Header
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Touchpoint Timeline History (${logsState.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (logsState.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No logged touchpoints yet", fontWeight = FontWeight.Medium)
                                Text("Calls will auto-sync here or you can log manually.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(logsState) { log ->
                        TimelineLogItem(log = log)
                    }
                }
            }
        }
    }

    // Add Manual Touchpoint Log Dialog
    if (showAddLogDialog && contactDetailState != null) {
        var selectedType by remember { mutableStateOf(InteractionType.MANUAL_LOG) }
        var noteText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddLogDialog = false },
            title = { Text("Record Communication Touchpoint") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select Interaction Type:", fontSize = 13.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        InteractionType.values().forEach { type ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selectedType == type) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                    .padding(8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedType == type,
                                    onClick = { selectedType = type }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(type.label, fontSize = 14.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Interaction Notes") },
                        placeholder = { Text("e.g. Talked about family event, birthday wishes...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.logCall(contactId, selectedType, 0, noteText.ifBlank { null })
                    showAddLogDialog = false
                }) {
                    Text("Save to History")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLogDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Tags Dialog
    if (showEditTagsDialog && contactDetailState != null) {
        val currentTagIds = remember {
            mutableStateListOf<Long>().apply {
                addAll(contactDetailState!!.tags.map { it.id })
            }
        }

        // Distinct tags
        val distinctTags = remember(allTags) {
            allTags.distinctBy { "${it.category.name}_${it.name.trim().lowercase()}" }
        }

        AlertDialog(
            onDismissRequest = { showEditTagsDialog = false },
            title = { Text("Assign Tags") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "Group tags allow multiple selections. Frequency, Snooze, and Priority allow max 1 active tag per category:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TagCategory.values().forEach { category ->
                        val catTags = distinctTags.filter { it.category == category }
                        if (catTags.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    Text(
                                        text = category.displayName + if (category != TagCategory.GROUPING) " (Max 1)" else " (Multiple)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }

                            items(catTags, key = { it.id }) { tag ->
                                val isSelected = currentTagIds.contains(tag.id)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (!isSelected) {
                                                if (tag.category != TagCategory.GROUPING) {
                                                    val sameCatIds = distinctTags.filter { it.category == tag.category }.map { it.id }
                                                    currentTagIds.removeAll(sameCatIds)
                                                }
                                                currentTagIds.add(tag.id)
                                            } else {
                                                currentTagIds.remove(tag.id)
                                            }
                                        }
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                if (tag.category != TagCategory.GROUPING) {
                                                    val sameCatIds = distinctTags.filter { it.category == tag.category }.map { it.id }
                                                    currentTagIds.removeAll(sameCatIds)
                                                }
                                                if (!currentTagIds.contains(tag.id)) {
                                                    currentTagIds.add(tag.id)
                                                }
                                            } else {
                                                currentTagIds.remove(tag.id)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    TagChip(tag = tag)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateContact(contactDetailState!!.contact, currentTagIds.toList())
                    showEditTagsDialog = false
                }) {
                    Text("Apply Tags")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTagsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TimelineLogItem(log: InteractionLogEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (log.type) {
                InteractionType.INCOMING_CALL -> Icons.Default.CallReceived
                InteractionType.OUTGOING_CALL -> Icons.Default.CallMade
                InteractionType.MANUAL_LOG -> Icons.Default.Phone
                InteractionType.WHATSAPP_CALL -> Icons.Default.PhoneCallback
                InteractionType.WHATSAPP_CHAT -> Icons.Default.Chat
                InteractionType.SNOOZE -> Icons.Default.Snooze
                InteractionType.NOTE -> Icons.Default.Note
            }

            val iconColor = when (log.type) {
                InteractionType.INCOMING_CALL, InteractionType.OUTGOING_CALL, InteractionType.MANUAL_LOG -> SuccessGreen
                InteractionType.WHATSAPP_CALL, InteractionType.WHATSAPP_CHAT -> Color(0xFF25D366)
                InteractionType.SNOOZE -> WarningAmber
                else -> MaterialTheme.colorScheme.primary
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.type.label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (!log.note.isNullOrBlank()) {
                    Text(
                        text = log.note,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = SimpleDateFormat("MMM d, yyyy - h:mm a", Locale.getDefault()).format(Date(log.timestamp)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
