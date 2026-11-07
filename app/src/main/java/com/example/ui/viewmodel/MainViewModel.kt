package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Attachment
import com.example.data.model.Category
import com.example.data.model.CustomField
import com.example.data.model.Item
import com.example.data.model.ItemWithDetails
import com.example.data.model.Reminder
import com.example.data.preferences.SettingsManager
import com.example.data.repository.InvoiceRepository
import com.example.utils.FileStorageHelper
import com.example.utils.PendingAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val settingsManager = SettingsManager(application)
    val repository = InvoiceRepository(
        categoryDao = database.categoryDao(),
        itemDao = database.itemDao(),
        customFieldDao = database.customFieldDao(),
        attachmentDao = database.attachmentDao(),
        reminderDao = database.reminderDao()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // null means "ALL" categories
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId = _selectedCategoryId.asStateFlow()

    private val _migrationWarning = MutableStateFlow(AppDatabase.migrationFailedMessage)
    val migrationWarning: StateFlow<String?> = _migrationWarning.asStateFlow()

    val categories: StateFlow<List<Category>> = repository.allCategories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val itemsWithDetails: StateFlow<List<ItemWithDetails>> = combine(
        repository.allItemsWithDetails,
        _searchQuery,
        _selectedCategoryId
    ) { items, query, catId ->
        items.filter { details ->
            val matchesCategory = catId == null || details.item.categoryId == catId
            if (!matchesCategory) return@filter false

            if (query.isBlank()) return@filter true

            details.item.title.contains(query, ignoreCase = true) ||
                details.item.sellerName.contains(query, ignoreCase = true) ||
                details.item.invoiceNumber.contains(query, ignoreCase = true) ||
                details.item.orderId.contains(query, ignoreCase = true) ||
                details.category?.name?.contains(query, ignoreCase = true) == true ||
                details.customFields.any {
                    it.name.contains(query, ignoreCase = true) || it.value.contains(query, ignoreCase = true)
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val pendingReminders: StateFlow<List<Reminder>> = repository.pendingReminders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allItems: StateFlow<List<Item>> = repository.allItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaultCategories()
        }
    }

    fun dismissMigrationWarning() {
        AppDatabase.migrationFailedMessage = null
        _migrationWarning.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategoryFilter(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun createCategory(
        name: String,
        colorMarker: String,
        onResult: (Result<Category>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.createCategory(name, colorMarker)
            onResult(result)
        }
    }

    fun renameCategory(
        category: Category,
        newName: String,
        newColorMarker: String,
        onResult: (Result<Category>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.renameCategory(category, newName, newColorMarker)
            onResult(result)
        }
    }

    fun deleteCustomCategory(
        category: Category,
        onResult: (Result<Category>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.deleteCustomCategory(category)
            if (result.isSuccess && _selectedCategoryId.value == category.id) {
                _selectedCategoryId.value = null
            }
            onResult(result)
        }
    }

    fun saveOrUpdateItem(
        item: Item,
        customFields: List<CustomField>,
        pendingAttachments: List<PendingAttachment>,
        onComplete: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val savedId = repository.saveOrUpdateItem(
                context = getApplication(),
                settingsManager = settingsManager,
                item = item,
                customFields = customFields,
                pendingAttachments = pendingAttachments
            )
            onComplete(savedId)
        }
    }

    fun saveCustomFieldTemplate(categoryId: Long, name: String, type: String) {
        viewModelScope.launch {
            repository.saveCustomFieldTemplate(categoryId, name, type)
        }
    }

    fun deleteAttachment(attachment: Attachment) {
        viewModelScope.launch {
            repository.deleteAttachment(attachment)
        }
    }

    fun deleteItem(itemId: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteItem(getApplication(), itemId)
            onComplete()
        }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsManager.clearAllData()
            database.clearAllTables()
            try {
                FileStorageHelper.getAppPrivateBaseDir(getApplication()).deleteRecursively()
            } catch (_: Exception) {
            }
            repository.ensureDefaultCategories()
        }
    }
}
