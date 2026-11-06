package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiExtractionService
import com.example.data.ai.ExtractedInvoiceResult
import com.example.data.local.AppDatabase
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.data.model.InvoiceWithItems
import com.example.data.model.Reminder
import com.example.data.preferences.SettingsManager
import com.example.data.repository.InvoiceRepository
import com.example.utils.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ExtractionUiState {
    object Idle : ExtractionUiState()
    object Processing : ExtractionUiState()
    data class Success(val result: ExtractedInvoiceResult) : ExtractionUiState()
    data class Error(val message: String) : ExtractionUiState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val settingsManager = SettingsManager(application)
    val repository = InvoiceRepository(
        database.invoiceDao(),
        database.invoiceItemDao(),
        database.reminderDao()
    )

    val aiExtractionService = AiExtractionService(application, settingsManager)

    // Search query & filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow("ALL") // "ALL", "EXPIRING_SOON", "ACTIVE", "EXPIRED"
    val selectedFilter = _selectedFilter.asStateFlow()

    // State of AI Extraction
    private val _extractionState = MutableStateFlow<ExtractionUiState>(ExtractionUiState.Idle)
    val extractionState: StateFlow<ExtractionUiState> = _extractionState.asStateFlow()

    // All invoices from DB
    val invoicesWithItems: StateFlow<List<InvoiceWithItems>> = combine(
        repository.allInvoicesWithItems,
        _searchQuery,
        _selectedFilter
    ) { invoices, query, filter ->
        invoices.filter { invoiceWithItems ->
            val matchesQuery = query.isBlank() ||
                invoiceWithItems.invoice.sellerName.contains(query, ignoreCase = true) ||
                invoiceWithItems.invoice.invoiceNumber.contains(query, ignoreCase = true) ||
                invoiceWithItems.items.any { it.productName.contains(query, ignoreCase = true) }

            if (!matchesQuery) return@filter false

            if (filter == "ALL") return@filter true

            val highestPriorityStatus = invoiceWithItems.items.map {
                DateUtils.getExpiryStatus(it.warrantyEndDate)
            }.minByOrNull { it.ordinal } ?: DateUtils.ExpiryStatus.ACTIVE

            when (filter) {
                "EXPIRING_SOON" -> highestPriorityStatus == DateUtils.ExpiryStatus.EXPIRING_SOON
                "ACTIVE" -> highestPriorityStatus == DateUtils.ExpiryStatus.ACTIVE
                "EXPIRED" -> highestPriorityStatus == DateUtils.ExpiryStatus.EXPIRED
                else -> true
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

    val allItems: StateFlow<List<InvoiceItem>> = repository.allItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            repository.seedSampleDataIfEmpty()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun processInvoiceUri(uri: Uri) {
        viewModelScope.launch {
            _extractionState.value = ExtractionUiState.Processing
            try {
                val result = aiExtractionService.processDocumentUri(uri)
                if (result.error != null) {
                    _extractionState.value = ExtractionUiState.Error(result.error)
                } else {
                    _extractionState.value = ExtractionUiState.Success(result)
                }
            } catch (e: Exception) {
                _extractionState.value = ExtractionUiState.Error(e.localizedMessage ?: "Extraction failed")
            }
        }
    }

    fun resetExtractionState() {
        _extractionState.value = ExtractionUiState.Idle
    }

    fun saveExtractedInvoice(
        invoice: Invoice,
        items: List<InvoiceItem>,
        onComplete: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = repository.saveInvoiceWithItems(invoice, items)
            _extractionState.value = ExtractionUiState.Idle
            onComplete(id)
        }
    }

    fun deleteInvoice(invoiceId: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteInvoice(invoiceId)
            onComplete()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            settingsManager.clearAllData()
            database.clearAllTables()
        }
    }
}
