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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.data.dto.ContactWithDetails
import com.example.data.model.InteractionLogEntity
import com.example.data.model.InteractionType
import com.example.data.sync.SystemContactHelper
import com.example.data.ui.viewmodel.MainViewModel
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
    val allGroups by viewModel.allGroups.collectAsState()

    var showAddLogDialog by remember { mutableStateOf(false) }
    var showEditConfigDialog by remember { mutableStateOf(false) }

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
                        IconButton(
                            onClick = { viewModel.deleteContact(contact); onBackClick() },
                            modifier = Modifier.testTag("delete_contact_button")
                        ) {
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
                    val (phoneDetails, fetchedPhotoUri) = remember(item) {
                        SystemContactHelper.fetchPhoneDetailsAndPhoto(
                            context = context,
                            systemContactId = item.contact.systemContactId,
                            lookupKey = item.contact.lookupKey,
                            fallbackPhoneNumber = item.contact.phoneNumber,
                            fallbackSecondaryNumbers = item.contact.secondaryNumbers
                        )
                    }

                    val displayPhotoUri = item.contact.avatarUri ?: fetchedPhotoUri

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
                            if (!displayPhotoUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = displayPhotoUri,
                                    contentDescription = "${item.contact.name} photo",
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
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
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = item.contact.name,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                phoneDetails.forEach { detail ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.updateMostRecentlyUsedNumber(item.contact.id, detail.number, context)
                                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                                    data = Uri.parse("tel:${detail.number}")
                                                }
                                                context.startActivity(intent)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${detail.label}: ${detail.number}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.Call,
                                            contentDescription = "Call ${detail.label} ${detail.number}",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            if (item.contact.systemContactId != null && item.contact.systemContactId > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = {
                                        try {
                                            val uri = android.content.ContentUris.withAppendedId(
                                                android.provider.ContactsContract.Contacts.CONTENT_URI,
                                                item.contact.systemContactId
                                            )
                                            val intent = Intent(Intent.ACTION_EDIT).apply {
                                                data = uri
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Edit,
                                        contentDescription = "Edit in System Contacts",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Edit in System Contacts",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (!item.contact.notes.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.contact.notes,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Frequency / Agenda Status Info Row
                            if (item.hasFrequencyTracked()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Reminder frequency", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${item.resolvedFrequencyDays() ?: 0} Days", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }

                                    val days = item.daysUntilDue()
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Status", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = if (days < 0) "${-days}d Overdue" else if (days == 0) "Due Today" else "Due in ${days}d",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (days < 0) OverdueRed else SuccessGreen
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "No frequency assigned (Not scheduled in call agenda)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(8.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

                // Group & Overrides Section
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Group & Config Overrides", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            TextButton(
                                onClick = { showEditConfigDialog = true },
                                modifier = Modifier.testTag("edit_config_button")
                            ) {
                                Text("Configure")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Group Information
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Associated Group: ", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    if (item.group != null) {
                                        val groupColor = try {
                                            Color(android.graphics.Color.parseColor(item.group.colorHex))
                                        } catch (e: Exception) {
                                            MaterialTheme.colorScheme.primary
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(groupColor.copy(alpha = 0.15f), CircleShape)
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(item.group.name, color = groupColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    } else {
                                        Text("None (Unassigned)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                    }
                                }

                                // Frequency details (with override status)
                                Column {
                                    Text("Reminder frequency", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        if (item.contact.customFrequencyDays != null) {
                                            Text("Custom Override: every ${item.contact.customFrequencyDays} days", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            item.group?.let { g ->
                                                Text("(Group default is ${g.defaultFrequencyDays}d)", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp)
                                            }
                                        } else {
                                            val freqVal = item.group?.defaultFrequencyDays
                                            if (freqVal != null) {
                                                Text("Group Default: every $freqVal days", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                            } else {
                                                Text("Not Tracked (No group default or custom override)", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }

                                // Priority Level details (with override status)
                                Column {
                                    Text("Priority Level", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val resolvedVal = item.resolvedPriority()
                                        val priorityText = when (resolvedVal) {
                                            1 -> "Low"
                                            3 -> "High"
                                            else -> "Normal"
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(priorityText, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                            if (resolvedVal == 3) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(Icons.Default.Star, contentDescription = "High Priority", tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        if (item.contact.customPriority != null) {
                                            val gPriorityText = when (item.group?.defaultPriority) {
                                                1 -> "Low"
                                                3 -> "High"
                                                else -> "Normal"
                                            }
                                            Text("Custom Override (Group was $gPriorityText)", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        } else {
                                            Text("Group Default", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp)
                                        }
                                    }
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
                    items(logsState, key = { it.id }) { log ->
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

    // Edit Group & Override Configuration Dialog
    if (showEditConfigDialog && contactDetailState != null) {
        val currentContact = contactDetailState!!.contact
        var selectedGroupId by remember { mutableStateOf(currentContact.groupId) }
        var customFreqDays by remember { mutableStateOf<Int?>(currentContact.customFrequencyDays) }
        var customPriority by remember { mutableStateOf(currentContact.customPriority) } // null means group default

        AlertDialog(
            onDismissRequest = { showEditConfigDialog = false },
            title = { Text("Configure Reminders") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Group selector
                    item {
                        Text("Associated Group", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // No Group Option
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    selectedGroupId = null 
                                    customFreqDays = null
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedGroupId == null,
                                onClick = { 
                                    selectedGroupId = null 
                                    customFreqDays = null
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("No Group (Not Scheduled)", fontSize = 14.sp)
                        }

                        // Listed Groups
                        allGroups.forEach { group ->
                            val gColor = try {
                                Color(android.graphics.Color.parseColor(group.colorHex))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primary
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        selectedGroupId = group.id 
                                        customFreqDays = null
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = selectedGroupId == group.id,
                                        onClick = { 
                                            selectedGroupId = group.id 
                                            customFreqDays = null
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(gColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(group.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                                
                                // Group level frequency & priority pills shown under each group name
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 48.dp, bottom = 4.dp)
                                ) {
                                    // Frequency Pill
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(10.dp),
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                            Text(
                                                text = "${group.defaultFrequencyDays}d",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
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
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, priorityColor.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
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
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = if (group.defaultPriority == 3) MaterialTheme.colorScheme.onSurface else priorityColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Frequency override input
                    item {
                        Text("Custom Frequency Override", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val selectedGroup = allGroups.find { it.id == selectedGroupId }
                            val groupFreq = selectedGroup?.defaultFrequencyDays ?: 14
                            
                            // Decrease Button as a clean Surface
                            val canDecrease = customFreqDays != null
                            Surface(
                                onClick = {
                                    val current = customFreqDays
                                    if (current != null) {
                                        if (current <= 1) {
                                            customFreqDays = null
                                        } else {
                                            customFreqDays = current - 1
                                        }
                                    }
                                },
                                enabled = canDecrease,
                                shape = RoundedCornerShape(12.dp),
                                color = if (canDecrease) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Decrease Frequency",
                                        tint = if (canDecrease) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (customFreqDays != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = if (customFreqDays != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = if (customFreqDays != null) "$customFreqDays Days" else "Use Group Default",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (customFreqDays != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (customFreqDays != null) "Custom override active" else "Currently: $groupFreq days",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (customFreqDays != null) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            
                            // Increase Button as a clean Surface
                            Surface(
                                onClick = {
                                    val current = customFreqDays
                                    if (current == null) {
                                        customFreqDays = groupFreq
                                    } else {
                                        customFreqDays = current + 1
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase Frequency",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Priority override input
                    item {
                        Text("Custom Priority Override", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                null to "Default",
                                1 to "Low",
                                2 to "Normal",
                                3 to "High"
                            ).forEach { (valLevel, valLabel) ->
                                val selected = customPriority == valLevel
                                Surface(
                                    onClick = { customPriority = valLevel },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = valLabel,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (valLevel == 3) {
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFC107),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
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
                        val updated = currentContact.copy(
                            groupId = selectedGroupId,
                            customFrequencyDays = customFreqDays,
                            customPriority = customPriority
                        )
                        viewModel.updateContact(updated)
                        showEditConfigDialog = false
                    },
                    modifier = Modifier.testTag("save_config_button")
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditConfigDialog = false }) {
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (log.type) {
                        InteractionType.INCOMING_CALL -> Icons.Default.CallReceived
                        InteractionType.OUTGOING_CALL -> Icons.Default.CallMade
                        InteractionType.WHATSAPP_CALL -> Icons.Default.Call
                        InteractionType.SNOOZE -> Icons.Default.Snooze
                        else -> Icons.Default.History
                    }
                    val iconColor = when (log.type) {
                        InteractionType.SNOOZE -> WarningAmber
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = log.type.label,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = log.type.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Text(
                    text = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(log.timestamp)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (log.durationSeconds > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                val minutes = log.durationSeconds / 60
                val seconds = log.durationSeconds % 60
                val durationText = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
                Text(
                    text = "Duration: $durationText",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 26.dp)
                )
            }

            if (!log.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.note,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 26.dp)
                )
            }
        }
    }
}
