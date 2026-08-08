package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.data.dto.ContactWithDetails
import com.example.data.model.GroupEntity
import com.example.data.sync.SystemContactHelper
import com.example.data.ui.viewmodel.ContactSortOption
import com.example.data.ui.viewmodel.MainViewModel
import com.example.ui.theme.OverdueRed
import com.example.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsListScreen(
    viewModel: MainViewModel,
    onContactClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedGroupFilter by viewModel.selectedGroupFilter.collectAsState()
    val currentSortOption by viewModel.contactSortOption.collectAsState()
    val contactsList by viewModel.filteredContactsWithDetails.collectAsState()
    val allGroups by viewModel.allGroups.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    // Permission launcher for sync
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasContacts = permissions[Manifest.permission.READ_CONTACTS] == true
        if (hasContacts) {
            viewModel.syncFullContactsAndCallLogs()
        } else {
            viewModel.syncCallLogs()
        }
    }

    // Launcher for System Contacts ACTION_INSERT
    val addContactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // When user returns from system contacts editor:
        // Automatically switch sort option to RECENTLY_ADDED so the new contact is at the top
        viewModel.setContactSortOption(ContactSortOption.RECENTLY_ADDED)
        // Automatically sync system contacts into Room database
        val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (hasContacts) {
            viewModel.syncFullContactsAndCallLogs()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG))
        }
    }

    val triggerSync = {
        val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasCallLog = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        if (!hasContacts || !hasCallLog) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_CALL_LOG
                )
            )
        } else {
            viewModel.syncFullContactsAndCallLogs()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("All Contacts", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { triggerSync() }) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = "Sync Contacts & Call Logs")
                            }
                        }
                    }
                )

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search by name or number...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Filter & Sort Chips Row
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sort options
                    item {
                        FilterChip(
                            selected = currentSortOption == ContactSortOption.NAME_ASC,
                            onClick = { viewModel.setContactSortOption(ContactSortOption.NAME_ASC) },
                            label = { Text("Sort: A-Z") },
                            leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = currentSortOption == ContactSortOption.RECENTLY_ADDED,
                            onClick = { viewModel.setContactSortOption(ContactSortOption.RECENTLY_ADDED) },
                            label = { Text("Sort: Recently Added") },
                            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    // Divider separator text
                    item {
                        Text("|", color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    // Group filters
                    item {
                        FilterChip(
                            selected = selectedGroupFilter == null,
                            onClick = { viewModel.setSelectedGroupFilter(null) },
                            label = { Text("All Groups") }
                        )
                    }
                    items(allGroups) { group ->
                        FilterChip(
                            selected = selectedGroupFilter?.id == group.id,
                            onClick = {
                                if (selectedGroupFilter?.id == group.id) {
                                    viewModel.setSelectedGroupFilter(null)
                                } else {
                                    viewModel.setSelectedGroupFilter(group)
                                }
                            },
                            label = { Text(group.name) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            type = ContactsContract.Contacts.CONTENT_TYPE
                        }
                        addContactLauncher.launch(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = "Add Contact") },
                text = { Text("Add Contact") }
            )
        }
    ) { innerPadding ->
        @OptIn(ExperimentalMaterial3Api::class)
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { triggerSync() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (contactsList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.PersonSearch,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No contacts found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Pull down or tap below to sync contacts from your device phonebook.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = { triggerSync() }) {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Device Contacts")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(
                        items = contactsList,
                        key = { it.contact.id }
                    ) { item ->
                        ContactListItemCard(
                            item = item,
                            onClick = { onContactClick(item.contact.id) },
                            onCallPhone = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${item.contact.phoneNumber}")
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactListItemCard(
    item: ContactWithDetails,
    onClick: () -> Unit,
    onCallPhone: () -> Unit
) {
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!displayPhotoUri.isNullOrBlank()) {
                AsyncImage(
                    model = displayPhotoUri,
                    contentDescription = "${item.contact.name} avatar",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.contact.name.take(1).uppercase(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.contact.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
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
                
                val primaryDetail = phoneDetails.firstOrNull()
                val phoneDisplayText = if (phoneDetails.size > 1 && primaryDetail != null) {
                    "${primaryDetail.label}: ${primaryDetail.number}  •  +${phoneDetails.size - 1} more"
                } else if (primaryDetail != null) {
                    "${primaryDetail.label}: ${primaryDetail.number}"
                } else {
                    item.contact.phoneNumber
                }

                Text(
                    text = phoneDisplayText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (item.group != null) {
                    val groupColor = try {
                        Color(android.graphics.Color.parseColor(item.group.colorHex))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.primary
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .background(groupColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.group.name,
                            color = groupColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (item.hasFrequencyTracked()) {
                    val days = item.daysUntilDue()
                    val statusText = when {
                        item.contact.snoozedUntilTimestamp != null && item.contact.snoozedUntilTimestamp > System.currentTimeMillis() -> "Snoozed"
                        days < 0 -> "${-days}d Overdue"
                        days == 0 -> "Due Today"
                        else -> "Due in ${days}d"
                    }
                    val statusColor = when {
                        days < 0 -> OverdueRed
                        days == 0 -> MaterialTheme.colorScheme.primary
                        else -> SuccessGreen
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }

            IconButton(onClick = onCallPhone) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
