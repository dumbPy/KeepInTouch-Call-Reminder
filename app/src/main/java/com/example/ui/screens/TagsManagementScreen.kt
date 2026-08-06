package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.example.data.dto.ContactWithDetails
import com.example.data.model.TagCategory
import com.example.data.model.TagEntity
import com.example.data.sync.SystemContactHelper
import com.example.data.ui.viewmodel.MainViewModel
import com.example.ui.components.TagChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsManagementScreen(
    viewModel: MainViewModel
) {
    val rawTags by viewModel.allTags.collectAsState()
    val allContacts by viewModel.allContactsWithDetails.collectAsState()
    var showAddTagDialog by remember { mutableStateOf(false) }
    var selectedTagForContacts by remember { mutableStateOf<TagEntity?>(null) }

    // Deduplicate tags in UI by category + name
    val allTags = remember(rawTags) {
        rawTags.distinctBy { "${it.category.name}_${it.name.trim().lowercase()}" }
    }

    val presetColors = listOf(
        "#2196F3", "#4CAF50", "#FF9800", "#9C27B0", "#E91E63",
        "#009688", "#3F51B5", "#673AB7", "#00BCD4", "#FF5722"
    )

    val activeSelectedTag = selectedTagForContacts
    if (activeSelectedTag != null) {
        TagContactsSelectionPage(
            tag = activeSelectedTag,
            allTags = allTags,
            allContacts = allContacts,
            onBack = { selectedTagForContacts = null },
            onSave = { selectedIds ->
                viewModel.updateContactsForTag(activeSelectedTag.id, selectedIds)
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tags Manager", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddTagDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Tag") },
                text = { Text("New Tag") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Single-Setting Tag Model",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Each tag controls exactly ONE attribute. Click on any tag to view & assign contacts!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            TagCategory.values().forEach { category ->
                val categoryTags = allTags.filter { it.category == category }

                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val categoryColor = try {
                                Color(android.graphics.Color.parseColor(category.defaultColorHex))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primary
                            }

                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(categoryColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = category.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Text(
                            text = category.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (categoryTags.isEmpty()) {
                    item {
                        Text(
                            text = "No tags created in this category.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    items(categoryTags, key = { it.id }) { tag ->
                        val assignedCount = allContacts.count { contact ->
                            contact.tags.any { it.id == tag.id }
                        }

                        TagManagementRowItem(
                            tag = tag,
                            assignedCount = assignedCount,
                            onClick = { selectedTagForContacts = tag },
                            onDelete = { viewModel.deleteTag(tag) }
                        )
                    }
                }
            }
        }
    }

    // Add Tag Dialog
    if (showAddTagDialog) {
        var name by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf(TagCategory.GROUPING) }
        var singleValue by remember { mutableStateOf("7") }
        var selectedColorHex by remember { mutableStateOf(TagCategory.GROUPING.defaultColorHex) }

        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Create Single-Setting Tag") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Tag Name *") },
                        placeholder = { Text("e.g. Weekly Call, Family, Snooze 3d") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Select Category:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(TagCategory.values()) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = {
                                    selectedCategory = category
                                    selectedColorHex = category.defaultColorHex
                                    singleValue = when (category) {
                                        TagCategory.GROUPING -> category.displayName
                                        TagCategory.FREQUENCY -> "7"
                                        TagCategory.SNOOZE_DEFAULT -> "1"
                                        TagCategory.PRIORITY -> "10"
                                    }
                                },
                                label = { Text(category.displayName) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = singleValue,
                        onValueChange = { singleValue = it },
                        label = { Text(selectedCategory.singleSettingLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Custom Tag Color:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presetColors) { hex ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (selectedColorHex == hex) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                                    )
                                    .clickable { selectedColorHex = hex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.addTag(name, selectedCategory, singleValue, selectedColorHex)
                            showAddTagDialog = false
                        }
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Save Tag")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TagManagementRowItem(
    tag: TagEntity,
    assignedCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val color = try {
        Color(android.graphics.Color.parseColor(tag.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(tag.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    text = "${tag.category.singleSettingLabel}: ${tag.singleValue} • $assignedCount Contact(s)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = "Manage Assigned Contacts",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Tag",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagContactsSelectionPage(
    tag: TagEntity,
    allTags: List<TagEntity>,
    allContacts: List<ContactWithDetails>,
    onBack: () -> Unit,
    onSave: (List<Long>) -> Unit
) {
    var showAddContactsPage by remember { mutableStateOf(false) }
    
    // Track assigned contact IDs for this tag
    val currentAssignedIds = remember(tag, allContacts) {
        allContacts.filter { c -> c.tags.any { it.id == tag.id } }.map { it.contact.id }.toMutableStateList()
    }

    if (showAddContactsPage) {
        AddContactsToTagSelectionView(
            tag = tag,
            allTags = allTags,
            allContacts = allContacts,
            initialSelectedIds = currentAssignedIds.toList(),
            onBack = { showAddContactsPage = false },
            onConfirmSelection = { selectedIds ->
                currentAssignedIds.clear()
                currentAssignedIds.addAll(selectedIds)
                onSave(currentAssignedIds.toList())
                showAddContactsPage = false
            }
        )
    } else {
        TagMembersDetailView(
            tag = tag,
            allContacts = allContacts,
            assignedContactIds = currentAssignedIds.toList(),
            onBack = onBack,
            onAddContactsClick = { showAddContactsPage = true },
            onRemoveContact = { contactId ->
                currentAssignedIds.remove(contactId)
                onSave(currentAssignedIds.toList())
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagMembersDetailView(
    tag: TagEntity,
    allContacts: List<ContactWithDetails>,
    assignedContactIds: List<Long>,
    onBack: () -> Unit,
    onAddContactsClick: () -> Unit,
    onRemoveContact: (Long) -> Unit
) {
    val assignedContacts = remember(allContacts, assignedContactIds) {
        allContacts.filter { assignedContactIds.contains(it.contact.id) }
    }

    val tagColor = try {
        Color(android.graphics.Color.parseColor(tag.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "'${tag.name}' Members",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${tag.category.displayName} • ${assignedContacts.size} member(s)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Tags Manager")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddContactsClick,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = "Add Contacts") },
                text = { Text("Add Contacts") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tag Header Info Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = tagColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(tagColor)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${tag.category.singleSettingLabel}: ${tag.singleValue}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Showing members currently assigned to '${tag.name}'. Click 'Add Contacts' to select individual users or auto-import from other tags.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (assignedContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No contacts assigned to '${tag.name}'",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap 'Add Contacts' below to assign contacts or import members from another tag.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = onAddContactsClick) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Contacts to Tag")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(assignedContacts, key = { it.contact.id }) { item ->
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
                        val photoUri = item.contact.avatarUri ?: fetchedPhotoUri

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!photoUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = photoUri,
                                        contentDescription = "${item.contact.name} avatar",
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = item.contact.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.contact.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    val primaryDetail = phoneDetails.firstOrNull()
                                    val phoneText = if (phoneDetails.size > 1 && primaryDetail != null) {
                                        "${primaryDetail.label}: ${primaryDetail.number} (+${phoneDetails.size - 1} more)"
                                    } else if (primaryDetail != null) {
                                        "${primaryDetail.label}: ${primaryDetail.number}"
                                    } else {
                                        item.contact.phoneNumber
                                    }

                                    Text(
                                        text = phoneText,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (item.tags.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        @OptIn(ExperimentalLayoutApi::class)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            item.tags.forEach { existingTag ->
                                                TagChip(tag = existingTag)
                                            }
                                        }
                                    }
                                }

                                IconButton(onClick = { onRemoveContact(item.contact.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove from Tag",
                                        tint = MaterialTheme.colorScheme.error
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactsToTagSelectionView(
    tag: TagEntity,
    allTags: List<TagEntity>,
    allContacts: List<ContactWithDetails>,
    initialSelectedIds: List<Long>,
    onBack: () -> Unit,
    onConfirmSelection: (List<Long>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedContactIds = remember { mutableStateListOf<Long>().apply { addAll(initialSelectedIds) } }
    var autoSelectedNotification by remember { mutableStateOf<String?>(null) }

    // Other tags available for auto-selecting members
    val otherTags = remember(allTags, tag) {
        allTags.filter { it.id != tag.id }
    }

    val matchingOtherTags = remember(otherTags, searchQuery) {
        if (searchQuery.isBlank()) {
            otherTags
        } else {
            otherTags.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.category.displayName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Contact filtering by name, phone, or assigned tags
    val filteredContacts = remember(allContacts, searchQuery) {
        if (searchQuery.isBlank()) {
            allContacts
        } else {
            allContacts.filter { item ->
                item.contact.name.contains(searchQuery, ignoreCase = true) ||
                        item.contact.getAllPhoneNumbers().any { it.contains(searchQuery) } ||
                        item.tags.any { it.name.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Add Members to '${tag.name}'",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "${selectedContactIds.size} of ${allContacts.size} contacts selected",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (selectedContactIds.size == allContacts.size) {
                            selectedContactIds.clear()
                        } else {
                            selectedContactIds.clear()
                            selectedContactIds.addAll(allContacts.map { it.contact.id })
                        }
                    }) {
                        Text(
                            if (selectedContactIds.size == allContacts.size) "Deselect All" else "Select All",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { onConfirmSelection(selectedContactIds.toList()) },
                        modifier = Modifier.weight(2f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save (${selectedContactIds.size})")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search contact name, phone, or tag (e.g. 'Family')...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Auto-selection feedback snackbar
            autoSelectedNotification?.let { msg ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(
                            onClick = { autoSelectedNotification = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Tag Quick-Select / Import Chips Row
            if (matchingOtherTags.isNotEmpty()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "Auto-select members from Tag:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(matchingOtherTags, key = { it.id }) { otherTag ->
                            val members = remember(allContacts, otherTag) {
                                allContacts.filter { c -> c.tags.any { it.id == otherTag.id } }
                            }
                            val memberIds = members.map { it.contact.id }
                            val allMembersSelected = memberIds.isNotEmpty() && memberIds.all { selectedContactIds.contains(it) }

                            FilterChip(
                                selected = allMembersSelected,
                                onClick = {
                                    if (allMembersSelected) {
                                        // Deselect members from this tag
                                        selectedContactIds.removeAll(memberIds)
                                        autoSelectedNotification = "Removed ${members.size} contact(s) from '${otherTag.name}'"
                                    } else {
                                        // Auto-select all members belonging to this tag
                                        var newlyAdded = 0
                                        memberIds.forEach { id ->
                                            if (!selectedContactIds.contains(id)) {
                                                selectedContactIds.add(id)
                                                newlyAdded++
                                            }
                                        }
                                        autoSelectedNotification = "Auto-selected ${members.size} member(s) from '${otherTag.name}' tag"
                                    }
                                },
                                label = { Text("${otherTag.name} (${members.size})") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (allMembersSelected) Icons.Default.Check else Icons.Default.Group,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // All Contacts Checkbox List
            if (filteredContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No contacts or tags match '$searchQuery'" else "No contacts available in database.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredContacts, key = { it.contact.id }) { item ->
                        val isSelected = selectedContactIds.contains(item.contact.id)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) {
                                        selectedContactIds.remove(item.contact.id)
                                    } else {
                                        selectedContactIds.add(item.contact.id)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
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
                                val photoUri = item.contact.avatarUri ?: fetchedPhotoUri

                                if (!photoUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = photoUri,
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
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = item.contact.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.contact.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    val primaryDetail = phoneDetails.firstOrNull()
                                    val phoneText = if (phoneDetails.size > 1 && primaryDetail != null) {
                                        "${primaryDetail.label}: ${primaryDetail.number} (+${phoneDetails.size - 1} more)"
                                    } else if (primaryDetail != null) {
                                        "${primaryDetail.label}: ${primaryDetail.number}"
                                    } else {
                                        item.contact.phoneNumber
                                    }

                                    Text(
                                        text = phoneText,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (item.tags.isNotEmpty()) {
                                        @OptIn(ExperimentalLayoutApi::class)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            item.tags.forEach { existingTag ->
                                                TagChip(tag = existingTag)
                                            }
                                        }
                                    }
                                }

                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (!selectedContactIds.contains(item.contact.id)) {
                                                selectedContactIds.add(item.contact.id)
                                            }
                                        } else {
                                            selectedContactIds.remove(item.contact.id)
                                        }
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

