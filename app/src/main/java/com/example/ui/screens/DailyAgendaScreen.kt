package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.dto.ContactWithDetails
import com.example.data.model.InteractionType
import com.example.data.sync.SystemContactHelper
import com.example.data.ui.viewmodel.AgendaSortOption
import com.example.data.ui.viewmodel.MainViewModel
import coil.compose.AsyncImage
import com.example.ui.theme.OverdueRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DailyAgendaScreen(
    viewModel: MainViewModel,
    onContactClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val dueList by viewModel.dueAgendaList.collectAsState()
    val upcomingAndSnoozedList by viewModel.upcomingAndSnoozedAgendaList.collectAsState()
    val lookaheadDays by viewModel.lookaheadDays.collectAsState()
    val allContactsWithDetails by viewModel.allContactsWithDetails.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val currentSortOptions by viewModel.sortOptions.collectAsState()

    var selectedSnoozeContactId by remember { mutableStateOf<Long?>(null) }
    var selectedLogContactId by remember { mutableStateOf<Long?>(null) }
    var contactForMultiNumberCall by remember { mutableStateOf<ContactWithDetails?>(null) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showLookaheadMenu by remember { mutableStateOf(false) }

    var showCustomSnoozeDialog by remember { mutableStateOf(false) }
    var customSnoozeMonths by remember { mutableStateOf(0) }
    var customSnoozeDays by remember { mutableStateOf(1) }

    val lookaheadOptions = listOf(1, 3, 7, 14, 30)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Daily Call Agenda",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Lookahead N-Days Selector
                    Box {
                        FilterChip(
                            selected = true,
                            onClick = { showLookaheadMenu = true },
                            label = { Text("${lookaheadDays}d Lookahead", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(12.dp)) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        DropdownMenu(
                            expanded = showLookaheadMenu,
                            onDismissRequest = { showLookaheadMenu = false }
                        ) {
                            Text(
                                text = "Show Upcoming Days:",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            lookaheadOptions.forEach { days ->
                                DropdownMenuItem(
                                    text = { Text("Next $days Days" + if (days == lookaheadDays) " ✓" else "") },
                                    onClick = {
                                        viewModel.setLookaheadDays(days)
                                        showLookaheadMenu = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = { showSortDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Multi-Key Sort",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.syncCallLogs() },
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync Call Logs",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero Banner & Summary Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.daily_agenda_hero_1785668907668),
                                contentDescription = "Hero Banner",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f))
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = if (dueList.isEmpty()) "All Due Calls Completed!" else "${dueList.size} Call(s) Due Today",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Auto-tracking phone calls & scheduled touchpoints",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Stat Chips Summary Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("${dueList.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = OverdueRed)
                                    Text("Due Now", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("${upcomingAndSnoozedList.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                    Text("Upcoming / Snoozed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("${lookaheadDays}d", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
                                    Text("Lookahead", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 1: Due & Overdue Calls
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PhoneCallback, contentDescription = null, tint = OverdueRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Due & Overdue Calls (${dueList.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            if (dueList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("No Pending Calls Due Today!", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("You are all caught up on primary call reminders.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                items(
                    items = dueList,
                    key = { "due_${it.contact.id}" }
                ) { item ->
                    AgendaContactSwipeItem(
                        item = item,
                        modifier = Modifier.animateItemPlacement(),
                        onClick = { onContactClick(item.contact.id) },
                        onQuickSnooze = { days ->
                            viewModel.snoozeContact(item.contact.id, days)
                        },
                        onOpenSnoozeOptions = {
                            selectedSnoozeContactId = item.contact.id
                        },
                        onLogTouchpoint = {
                            selectedLogContactId = item.contact.id
                        },
                        onCallPhone = {
                            val allNumbers = item.contact.getAllPhoneNumbers()
                            val mru = item.contact.mostRecentlyUsedNumber
                            if (allNumbers.size > 1) {
                                if (mru != null && com.example.data.sync.SystemContactHelper.isPhoneMatch(item.contact.phoneNumber, mru)) {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${item.contact.phoneNumber}")
                                    }
                                    context.startActivity(intent)
                                } else {
                                    contactForMultiNumberCall = item
                                }
                            } else {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${item.contact.phoneNumber}")
                                }
                                context.startActivity(intent)
                            }
                        },
                        onUnsnooze = {
                            viewModel.unsnoozeContact(item.contact.id)
                        }
                    )
                }
            }

            // SECTION 2: Combined Upcoming & Snoozed Contacts in Next N Days
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upcoming & Snoozed (Next $lookaheadDays Days) (${upcomingAndSnoozedList.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            if (upcomingAndSnoozedList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "No upcoming or snoozed calls scheduled in the next $lookaheadDays days.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(
                    items = upcomingAndSnoozedList,
                    key = { "upcoming_${it.contact.id}" }
                ) { item ->
                    AgendaContactSwipeItem(
                        item = item,
                        modifier = Modifier.animateItemPlacement(),
                        onClick = { onContactClick(item.contact.id) },
                        onQuickSnooze = { days ->
                            viewModel.snoozeContact(item.contact.id, days)
                        },
                        onOpenSnoozeOptions = {
                            selectedSnoozeContactId = item.contact.id
                        },
                        onLogTouchpoint = {
                            selectedLogContactId = item.contact.id
                        },
                        onCallPhone = {
                            val allNumbers = item.contact.getAllPhoneNumbers()
                            val mru = item.contact.mostRecentlyUsedNumber
                            if (allNumbers.size > 1) {
                                if (mru != null && com.example.data.sync.SystemContactHelper.isPhoneMatch(item.contact.phoneNumber, mru)) {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${item.contact.phoneNumber}")
                                    }
                                    context.startActivity(intent)
                                } else {
                                    contactForMultiNumberCall = item
                                }
                            } else {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${item.contact.phoneNumber}")
                                }
                                context.startActivity(intent)
                            }
                        },
                        onUnsnooze = {
                            viewModel.unsnoozeContact(item.contact.id)
                        }
                    )
                }
            }
        }
    }

    // Multi-Key Sort Dialog
    if (showSortDialog) {
        MultiKeySortDialog(
            currentOptions = currentSortOptions,
            onDismiss = { showSortDialog = false },
            onApply = { newOptions ->
                viewModel.updateSortOptions(newOptions)
            }
        )
    }

    // Snooze Options Dialog
    selectedSnoozeContactId?.let { contactId ->
        val item = allContactsWithDetails.firstOrNull { it.contact.id == contactId }

        if (showCustomSnoozeDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCustomSnoozeDialog = false
                },
                title = { Text("Custom Snooze Duration") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Set snooze duration for ${item?.contact?.name ?: "Contact"}:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Months Selector
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Months", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = { if (customSnoozeMonths > 0) customSnoozeMonths-- },
                                        enabled = customSnoozeMonths > 0
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease months")
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.widthIn(min = 60.dp)
                                    ) {
                                        Text(
                                            text = "$customSnoozeMonths",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    IconButton(
                                        onClick = { if (customSnoozeMonths < 12) customSnoozeMonths++ },
                                        enabled = customSnoozeMonths < 12
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase months")
                                    }
                                }
                            }

                            // Days Selector
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Days", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = { if (customSnoozeDays > 0 && (customSnoozeMonths > 0 || customSnoozeDays > 1)) customSnoozeDays-- },
                                        enabled = customSnoozeDays > 0 && (customSnoozeMonths > 0 || customSnoozeDays > 1)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease days")
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.widthIn(min = 60.dp)
                                    ) {
                                        Text(
                                            text = "$customSnoozeDays",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    IconButton(
                                        onClick = { if (customSnoozeDays < 31) customSnoozeDays++ },
                                        enabled = customSnoozeDays < 31
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase days")
                                    }
                                }
                            }
                        }

                        val calculatedTotalDays = (customSnoozeMonths * 30) + customSnoozeDays
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val label = buildString {
                                append("Snooze for ")
                                if (customSnoozeMonths > 0) append("$customSnoozeMonths Month${if (customSnoozeMonths > 1) "s" else ""} ")
                                if (customSnoozeDays > 0 || customSnoozeMonths == 0) append("$customSnoozeDays Day${if (customSnoozeDays != 1) "s" else ""}")
                                if (customSnoozeMonths > 0 && customSnoozeDays > 0) append(" ($calculatedTotalDays days total)")
                            }
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val totalDays = (customSnoozeMonths * 30) + customSnoozeDays
                            if (totalDays > 0) {
                                viewModel.snoozeContact(contactId, totalDays)
                            }
                            showCustomSnoozeDialog = false
                            selectedSnoozeContactId = null
                        }
                    ) {
                        Text("Snooze Contact")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomSnoozeDialog = false }) {
                        Text("Back")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { selectedSnoozeContactId = null },
                title = { Text("Snooze Reminder for ${item?.contact?.name ?: "Contact"}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Select snooze duration:")

                        ListItem(
                            headlineContent = { Text("1 Day") },
                            leadingContent = { Icon(Icons.Default.Snooze, contentDescription = null) },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.snoozeContact(contactId, 1)
                                    selectedSnoozeContactId = null
                                }
                        )
                        ListItem(
                            headlineContent = { Text("3 Days") },
                            leadingContent = { Icon(Icons.Default.Schedule, contentDescription = null) },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.snoozeContact(contactId, 3)
                                    selectedSnoozeContactId = null
                                }
                        )
                        ListItem(
                            headlineContent = { Text("1 Week (7 Days)") },
                            leadingContent = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.snoozeContact(contactId, 7)
                                    selectedSnoozeContactId = null
                                }
                        )
                        ListItem(
                            headlineContent = { Text("Custom Duration...") },
                            leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    customSnoozeMonths = 0
                                    customSnoozeDays = 1
                                    showCustomSnoozeDialog = true
                                }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedSnoozeContactId = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // Quick Manual Log Dialog
    selectedLogContactId?.let { contactId ->
        val item = allContactsWithDetails.firstOrNull { it.contact.id == contactId }
        var selectedType by remember { mutableStateOf(InteractionType.MANUAL_LOG) }
        var noteText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { selectedLogContactId = null },
            title = { Text("Log Touchpoint for ${item?.contact?.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select communication type:", fontSize = 14.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedType == InteractionType.MANUAL_LOG,
                            onClick = { selectedType = InteractionType.MANUAL_LOG },
                            label = { Text("Call") }
                        )
                        FilterChip(
                            selected = selectedType == InteractionType.WHATSAPP_CALL,
                            onClick = { selectedType = InteractionType.WHATSAPP_CALL },
                            label = { Text("WhatsApp Call") }
                        )
                        FilterChip(
                            selected = selectedType == InteractionType.WHATSAPP_CHAT,
                            onClick = { selectedType = InteractionType.WHATSAPP_CHAT },
                            label = { Text("Message") }
                        )
                    }

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Note (optional)") },
                        placeholder = { Text("e.g. Discussed holiday plans") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.logCall(contactId, selectedType, 0, noteText.ifBlank { null })
                    selectedLogContactId = null
                }) {
                    Text("Save Touchpoint")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedLogContactId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Multi-number select dialog
    contactForMultiNumberCall?.let { contactWithDetails ->
        val phoneDetails = remember(contactWithDetails) {
            SystemContactHelper.fetchPhoneDetailsAndPhoto(
                context = context,
                systemContactId = contactWithDetails.contact.systemContactId,
                lookupKey = contactWithDetails.contact.lookupKey,
                fallbackPhoneNumber = contactWithDetails.contact.phoneNumber,
                fallbackSecondaryNumbers = contactWithDetails.contact.secondaryNumbers
            ).first
        }

        AlertDialog(
            onDismissRequest = { contactForMultiNumberCall = null },
            title = { Text("Select Phone Number") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Which number would you like to call for ${contactWithDetails.contact.name}?")
                    Spacer(modifier = Modifier.height(8.dp))
                    phoneDetails.forEach { detail ->
                        val isMru = contactWithDetails.contact.mostRecentlyUsedNumber != null &&
                                com.example.data.sync.SystemContactHelper.isPhoneMatch(detail.number, contactWithDetails.contact.mostRecentlyUsedNumber)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.updateMostRecentlyUsedNumber(contactWithDetails.contact.id, detail.number, context)
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${detail.number}")
                                    }
                                    context.startActivity(intent)
                                    contactForMultiNumberCall = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = detail.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isMru) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "• 🕒 Most Recently Used",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Text(
                                    text = detail.number,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contactForMultiNumberCall = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AgendaContactSwipeItem(
    item: ContactWithDetails,
    onClick: () -> Unit,
    onQuickSnooze: (Int) -> Unit,
    onOpenSnoozeOptions: () -> Unit,
    onLogTouchpoint: () -> Unit,
    onCallPhone: () -> Unit,
    onUnsnooze: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
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

    val coroutineScope = rememberCoroutineScope()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onQuickSnooze(1)
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onOpenSnoozeOptions()
                    false
                }
                else -> false
            }
        }
    )

    LaunchedEffect(item.contact.snoozedUntilTimestamp) {
        dismissState.reset()
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> SuccessGreen.copy(alpha = 0.8f)
                    SwipeToDismissBoxValue.EndToStart -> WarningAmber.copy(alpha = 0.8f)
                    else -> Color.Transparent
                },
                label = "SwipeBackgroundColor"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                    Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Snooze, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Quick Snooze (1d)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Snooze Options...", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar circle
                    if (!displayPhotoUri.isNullOrBlank()) {
                        AsyncImage(
                            model = displayPhotoUri,
                            contentDescription = "${item.contact.name} avatar",
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.contact.name.take(1).uppercase(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.contact.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (item.resolvedPriority() == 3) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "High Priority",
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))

                        // Status Badges (Standard Due + Cumulative Snooze if snoozed) inline under the name
                        val isSnoozed = item.isSnoozed()
                        val stdDays = item.standardDaysUntilDue()

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Standard Due Badge
                            val stdBadgeColor = if (stdDays <= 0) OverdueRed else MaterialTheme.colorScheme.primary
                            val stdBadgeText = when {
                                stdDays < 0 -> "${-stdDays}d overdue"
                                stdDays == 0 -> "due today"
                                stdDays == 1 -> "due tomorrow"
                                else -> "due in ${stdDays}d"
                            }

                            Surface(
                                color = stdBadgeColor.copy(alpha = 0.15f),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = stdBadgeText,
                                    color = stdBadgeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            // Snooze Badge if snoozed
                            if (isSnoozed) {
                                val snoozeDays = item.addedSnoozeDays()
                                Surface(
                                    color = WarningAmber.copy(alpha = 0.15f),
                                    shape = CircleShape
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Snooze,
                                            contentDescription = null,
                                            tint = WarningAmber,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "${snoozeDays}d",
                                            color = WarningAmber,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Call icon button
                    IconButton(
                        onClick = onCallPhone,
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Expanded Section
                if (isExpanded) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Phone: ${item.contact.phoneNumber}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val lastTime = item.latestTouchpointTimestamp()
                            Text(
                                text = if (lastTime != null) "Last call: ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(lastTime))}" else "Never called",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (item.group != null) {
                            val groupColor = try {
                                Color(android.graphics.Color.parseColor(item.group.colorHex))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primary
                            }
                            Box(
                                modifier = Modifier
                                    .background(groupColor.copy(alpha = 0.12f), CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = item.group.name,
                                    color = groupColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.isSnoozed() && onUnsnooze != null) {
                            OutlinedButton(
                                onClick = onUnsnooze,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Un-snooze", fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        OutlinedButton(
                            onClick = onLogTouchpoint,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Call", fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        TextButton(
                            onClick = onClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("View Profile", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiKeySortDialog(
    currentOptions: List<AgendaSortOption>,
    onDismiss: () -> Unit,
    onApply: (List<AgendaSortOption>) -> Unit
) {
    var selectedList by remember { mutableStateOf(currentOptions) }
    val allOptions = AgendaSortOption.values()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sort, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Multi-Key Sorting", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Tap criteria to add/remove in priority order (#1 = primary, #2 = secondary):",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                allOptions.forEach { option ->
                    val isSelected = selectedList.contains(option)
                    val priorityIndex = selectedList.indexOf(option)

                    Surface(
                        onClick = {
                            selectedList = if (isSelected) {
                                selectedList.filter { it != option }
                            } else {
                                selectedList + option
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.displayName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = option.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text("#${priorityIndex + 1}", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 11.sp)
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(selectedList)
                    onDismiss()
                },
                enabled = selectedList.isNotEmpty()
            ) {
                Text("Apply Sorting")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
