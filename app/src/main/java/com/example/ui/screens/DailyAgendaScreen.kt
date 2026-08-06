package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.components.TagChip
import com.example.ui.theme.OverdueRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
    var showSortDialog by remember { mutableStateOf(false) }
    var showLookaheadMenu by remember { mutableStateOf(false) }

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
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${item.contact.phoneNumber}")
                            }
                            context.startActivity(intent)
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
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${item.contact.phoneNumber}")
                            }
                            context.startActivity(intent)
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

    // Custom Snooze Options Dialog
    selectedSnoozeContactId?.let { contactId ->
        val item = allContactsWithDetails.firstOrNull { it.contact.id == contactId }
        val defaultSnoozeDays = item?.resolvedDefaultSnoozeDays() ?: 1

        AlertDialog(
            onDismissRequest = { selectedSnoozeContactId = null },
            title = { Text("Snooze Reminder for ${item?.contact?.name ?: "Contact"}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select snooze duration:")
                    
                    ListItem(
                        headlineContent = { Text("1 Day (Quick Snooze)") },
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
                        headlineContent = { Text("Tag Default Preset (${defaultSnoozeDays}d)") },
                        leadingContent = { Icon(Icons.Default.Label, contentDescription = null) },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.snoozeContact(contactId, defaultSnoozeDays)
                                selectedSnoozeContactId = null
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
    onUnsnooze: (() -> Unit)? = null
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
                    onQuickSnooze(item.resolvedDefaultSnoozeDays())
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
                        Text("Snooze (${item.resolvedDefaultSnoozeDays()}d)", color = Color.White, fontWeight = FontWeight.Bold)
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
                                .background(
                                    if (item.isSnoozed()) WarningAmber.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.primaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.isSnoozed()) {
                                Icon(Icons.Default.Snooze, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    text = item.contact.name.take(1).uppercase(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.contact.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val lastTime = item.latestTouchpointTimestamp()
                        Text(
                            text = if (lastTime != null) "Last call: ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(lastTime))}" else "Never called",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Status Badges (Standard Due + Cumulative Snooze if snoozed)
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
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = stdBadgeText,
                                color = stdBadgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }

                        // Snooze Badge if snoozed
                        if (isSnoozed) {
                            val snoozeDays = item.addedSnoozeDays()
                            Surface(
                                color = WarningAmber.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
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

                    Text(
                        text = "Phone: ${item.contact.phoneNumber}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (item.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item.tags.forEach { tag ->
                                TagChip(tag = tag)
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
