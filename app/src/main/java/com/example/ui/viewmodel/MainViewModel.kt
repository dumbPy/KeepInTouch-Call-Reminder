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
    PRIORITY_WEIGHT("Priority Weight", "Higher weight/priority tags first"),
    FREQUENCY("Frequency", "Shortest frequency cadence first"),
    NAME("Name", "Alphabetical A-Z")
}

enum class ContactSortOption(val displayName: String) {
    NAME_ASC("A-Z"),
    RECENTLY_ADDED("Recently Added")
}

class MainViewModel(private val repository: ContactRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTagFilter = MutableStateFlow<TagEntity?>(null)
    val selectedTagFilter: StateFlow<TagEntity?> = _selectedTagFilter.asStateFlow()

    private val _contactSortOption = MutableStateFlow(ContactSortOption.NAME_ASC)
    val contactSortOption: StateFlow<ContactSortOption> = _contactSortOption.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val allTags: StateFlow<List<TagEntity>> = repository.allTags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allContactsWithDetails: StateFlow<List<ContactWithDetails>> =
        combine(repository.allContactsWithDetails, _searchQuery, _selectedTagFilter, _contactSortOption) { list, query, tag, sortOpt ->
            val filtered = list.filter { item ->
                val matchesQuery = query.isBlank() ||
                        item.contact.name.contains(query, ignoreCase = true) ||
                        item.contact.phoneNumber.contains(query)
                val matchesTag = tag == null || item.tags.any { it.id == tag.id }
                matchesQuery && matchesTag
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

    // Due & Overdue Contacts (Not Snoozed / Snooze Expired)
    val dueAgendaList: StateFlow<List<ContactWithDetails>> = combine(
        repository.allContactsWithDetails,
        _sortOptions
    ) { list, options ->
        val filtered = list.filter { item ->
            !item.isSnoozed() && item.daysUntilDue() <= 0
        }
        sortAgendaContacts(filtered, options)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined Upcoming & Snoozed Contacts (Next N Lookahead Days)
    val upcomingAndSnoozedAgendaList: StateFlow<List<ContactWithDetails>> = combine(
        repository.allContactsWithDetails,
        _lookaheadDays,
        _sortOptions
    ) { list, lookahead, options ->
        val filtered = list.filter { item ->
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
                AgendaSortOption.PRIORITY_WEIGHT -> compareByDescending { it.priorityWeight() }
                AgendaSortOption.FREQUENCY -> compareBy { it.resolvedFrequencyDays() }
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

    fun setSelectedTagFilter(tag: TagEntity?) {
        _selectedTagFilter.value = tag
    }

    fun setContactSortOption(option: ContactSortOption) {
        _contactSortOption.value = option
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun syncCallLogs() {
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

    fun addContact(name: String, phone: String, notes: String?, tagIds: List<Long>, customFreq: Int? = null) {
        viewModelScope.launch {
            val contact = ContactEntity(
                name = name,
                phoneNumber = phone,
                notes = notes,
                customFrequencyDays = customFreq
            )
            repository.addContact(contact, tagIds)
            _userMessage.value = "Contact added"
        }
    }

    fun updateContact(contact: ContactEntity, tagIds: List<Long>) {
        viewModelScope.launch {
            repository.updateContact(contact, tagIds)
            _userMessage.value = "Contact updated"
        }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.deleteContact(contact)
            _userMessage.value = "Contact deleted"
        }
    }

    fun addTag(name: String, category: TagCategory, singleValue: String, colorHex: String) {
        viewModelScope.launch {
            val tag = TagEntity(
                name = name,
                category = category,
                singleValue = singleValue,
                colorHex = colorHex
            )
            repository.addTag(tag)
            _userMessage.value = "Tag added"
        }
    }

    fun updateTag(tag: TagEntity) {
        viewModelScope.launch {
            repository.updateTag(tag)
            _userMessage.value = "Tag updated"
        }
    }

    fun deleteTag(tag: TagEntity) {
        viewModelScope.launch {
            repository.deleteTag(tag)
            _userMessage.value = "Tag deleted"
        }
    }

    fun updateContactsForTag(tagId: Long, selectedContactIds: List<Long>) {
        viewModelScope.launch {
            repository.updateContactsForTag(tagId, selectedContactIds)
            _userMessage.value = "Tag contacts updated"
        }
    }

    suspend fun getExportJsonString(): String {
        return repository.exportDataToJson()
    }

    fun importBackupJson(jsonString: String) {
        viewModelScope.launch {
            try {
                val (contacts, tags) = repository.importDataFromJson(jsonString)
                _userMessage.value = "Backup restored: $contacts contact(s), $tags tag(s) imported"
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
