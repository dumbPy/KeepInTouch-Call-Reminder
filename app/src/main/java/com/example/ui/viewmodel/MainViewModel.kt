package com.example.data.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.dto.ContactWithDetails
import com.example.data.model.*
import com.example.data.repository.ContactRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AgendaSortOption(val displayName: String, val description: String) {
    OVERDUE_DAYS("Overdue Days", "Most overdue contacts first"),
    PRIORITY_WEIGHT("Priority Weight", "Higher priority first"),
    FREQUENCY("Reminder Frequency", "Shortest frequency cadence first"),
    NAME("Name", "Alphabetical A-Z")
}

enum class ContactSortOption(val displayName: String) {
    NAME_ASC("A-Z"),
    RECENTLY_ADDED("Recently Added")
}

class MainViewModel(private val repository: ContactRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGroupFilter = MutableStateFlow<GroupEntity?>(null)
    val selectedGroupFilter: StateFlow<GroupEntity?> = _selectedGroupFilter.asStateFlow()

    private val _contactSortOption = MutableStateFlow(ContactSortOption.NAME_ASC)
    val contactSortOption: StateFlow<ContactSortOption> = _contactSortOption.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val allGroups: StateFlow<List<GroupEntity>> = repository.allGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allContactsWithDetails: StateFlow<List<ContactWithDetails>> = repository.allContactsWithDetails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredContactsWithDetails: StateFlow<List<ContactWithDetails>> =
        combine(repository.allContactsWithDetails, _searchQuery, _selectedGroupFilter, _contactSortOption) { list, query, group, sortOpt ->
            val filtered = list.filter { item ->
                val matchesQuery = query.isBlank() ||
                        item.contact.name.contains(query, ignoreCase = true) ||
                        item.contact.getAllPhoneNumbers().any { it.contains(query) }
                val matchesGroup = group == null || item.contact.groupId == group.id
                matchesQuery && matchesGroup
            }
            when (sortOpt) {
                ContactSortOption.NAME_ASC -> filtered.sortedBy { it.contact.name.lowercase() }
                ContactSortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.contact.createdAt }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sortOptions = MutableStateFlow<List<AgendaSortOption>>(
        listOf(AgendaSortOption.OVERDUE_DAYS, AgendaSortOption.PRIORITY_WEIGHT, AgendaSortOption.FREQUENCY)
    )
    val sortOptions: StateFlow<List<AgendaSortOption>> = _sortOptions.asStateFlow()

    private val _lookaheadDays = MutableStateFlow(7)
    val lookaheadDays: StateFlow<Int> = _lookaheadDays.asStateFlow()

    fun setLookaheadDays(days: Int) {
        _lookaheadDays.value = days
    }

    // Due & Overdue Contacts (Must have frequency tracked, Not Snoozed / Snooze Expired)
    val dueAgendaList: StateFlow<List<ContactWithDetails>> = combine(
        repository.allContactsWithDetails,
        _sortOptions
    ) { list, options ->
        val filtered = list.filter { item ->
            item.hasFrequencyTracked() && !item.isSnoozed() && item.daysUntilDue() <= 0
        }
        sortAgendaContacts(filtered, options)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined Upcoming & Snoozed Contacts (Next N Lookahead Days, Must have frequency tracked)
    val upcomingAndSnoozedAgendaList: StateFlow<List<ContactWithDetails>> = combine(
        repository.allContactsWithDetails,
        _lookaheadDays,
        _sortOptions
    ) { list, lookahead, options ->
        val filtered = list.filter { item ->
            if (!item.hasFrequencyTracked()) return@filter false
            val days = item.daysUntilDue()
            if (item.isSnoozed()) {
                days <= lookahead
            } else {
                days > 0 && days <= lookahead
            }
        }
        sortAgendaContacts(filtered, options)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyAgendaList: StateFlow<List<ContactWithDetails>> = dueAgendaList

    private fun sortAgendaContacts(contacts: List<ContactWithDetails>, options: List<AgendaSortOption>): List<ContactWithDetails> {
        if (options.isEmpty()) return contacts
        var comparator: Comparator<ContactWithDetails>? = null

        for (opt in options) {
            val comp: Comparator<ContactWithDetails> = when (opt) {
                AgendaSortOption.OVERDUE_DAYS -> compareBy { it.daysUntilDue() }
                AgendaSortOption.PRIORITY_WEIGHT -> compareByDescending { it.resolvedPriority() }
                AgendaSortOption.FREQUENCY -> compareBy { it.resolvedFrequencyDays() ?: Int.MAX_VALUE }
                AgendaSortOption.NAME -> compareBy { it.contact.name.lowercase() }
            }
            comparator = if (comparator == null) comp else comparator.then(comp)
        }

        return if (comparator != null) contacts.sortedWith(comparator) else contacts
    }

    fun updateSortOptions(options: List<AgendaSortOption>) {
        _sortOptions.value = options
    }

    init {
        viewModelScope.launch {
            repository.seedDefaultDataIfEmpty()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedGroupFilter(group: GroupEntity?) {
        _selectedGroupFilter.value = group
    }

    fun setContactSortOption(option: ContactSortOption) {
        _contactSortOption.value = option
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun syncCallLogsIncremental(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _isSyncing.value = true
            try {
                val newLogs = repository.syncCallLogsIncremental()
                if (!silent) {
                    val message = if (newLogs > 0) "Synced $newLogs new call log entry(ies)" else "Call logs are up to date"
                    _userMessage.value = message
                }
            } catch (e: Exception) {
                if (!silent) {
                    _userMessage.value = "Sync error: ${e.localizedMessage ?: "Check permissions"}"
                }
            } finally {
                if (!silent) _isSyncing.value = false
            }
        }
    }

    fun syncCallLogs() {
        syncCallLogsIncremental(silent = false)
    }

    fun syncFullContactsAndCallLogs() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = repository.syncContactsAndCallLogs()
                val imported = result.importedContactsCount
                val newLogs = result.newCallLogsCount
                
                val message = when {
                    imported > 0 && newLogs > 0 -> "Synced $imported new contact(s) & $newLogs call log(s)"
                    imported > 0 -> "Synced $imported new contact(s) from phonebook"
                    newLogs > 0 -> "Synced $newLogs new call log entry(ies)"
                    else -> "Contacts & call logs are up to date"
                }
                _userMessage.value = message
            } catch (e: Exception) {
                _userMessage.value = "Sync error: ${e.localizedMessage ?: "Check permissions"}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun logCall(contactId: Long, type: InteractionType, durationSec: Long = 0, note: String? = null) {
        viewModelScope.launch {
            repository.logCallInteraction(contactId, type, durationSec, note)
            _userMessage.value = "Touchpoint logged"
        }
    }

    fun snoozeContact(contactId: Long, snoozeDays: Int) {
        viewModelScope.launch {
            repository.snoozeContact(contactId, snoozeDays)
            _userMessage.value = "Snoozed for $snoozeDays day(s)"
        }
    }

    fun unsnoozeContact(contactId: Long) {
        viewModelScope.launch {
            repository.resetSnooze(contactId)
            _userMessage.value = "Snooze canceled"
        }
    }

    fun removeSampleContacts() {
        viewModelScope.launch {
            val count = repository.removeSampleContacts()
            _userMessage.value = "Removed $count sample contact(s)"
        }
    }

    fun addContact(name: String, phone: String, notes: String?, groupId: Long?, customFreq: Int?, customPriority: Int?) {
        viewModelScope.launch {
            val contact = ContactEntity(
                name = name,
                phoneNumber = phone,
                notes = notes,
                customFrequencyDays = customFreq,
                customPriority = customPriority,
                groupId = groupId
            )
            repository.addContact(contact)
            _userMessage.value = "Contact added"
        }
    }

    fun updateContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.updateContact(contact)
            _userMessage.value = "Contact updated"
        }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.deleteContact(contact)
            _userMessage.value = "Contact deleted"
        }
    }

    fun addGroup(name: String, defaultFrequencyDays: Int, defaultPriority: Int, colorHex: String) {
        viewModelScope.launch {
            val group = GroupEntity(
                name = name,
                defaultFrequencyDays = defaultFrequencyDays,
                defaultPriority = defaultPriority,
                colorHex = colorHex
            )
            repository.addGroup(group)
            _userMessage.value = "Group added"
        }
    }

    fun updateGroup(group: GroupEntity) {
        viewModelScope.launch {
            repository.updateGroup(group)
            _userMessage.value = "Group updated"
        }
    }

    fun deleteGroup(group: GroupEntity) {
        viewModelScope.launch {
            repository.deleteGroup(group)
            _userMessage.value = "Group deleted"
        }
    }

    fun updateContactsForGroup(groupId: Long, selectedContactIds: List<Long>) {
        viewModelScope.launch {
            repository.updateContactsForGroup(groupId, selectedContactIds)
            _userMessage.value = "Group members updated"
        }
    }

    suspend fun getExportJsonString(): String {
        return repository.exportDataToJson()
    }

    fun importBackupJson(jsonString: String) {
        viewModelScope.launch {
            try {
                val (contacts, groups) = repository.importDataFromJson(jsonString)
                _userMessage.value = "Backup restored: $contacts contact(s), $groups group(s) imported"
            } catch (e: Exception) {
                _userMessage.value = "Import failed: ${e.localizedMessage ?: "Invalid JSON backup file"}"
            }
        }
    }

    fun getContactDetailsFlow(contactId: Long) = repository.getContactDetailsFlow(contactId)
    fun getContactLogsFlow(contactId: Long) = repository.getContactLogsFlow(contactId)

    class Factory(private val repository: ContactRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository) as T
        }
    }
}
