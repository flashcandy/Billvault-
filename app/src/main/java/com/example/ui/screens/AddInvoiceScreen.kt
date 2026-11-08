package com.example.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.utils.ValidationResult
import com.example.utils.ValidationUtils
import com.example.data.model.Category
import com.example.data.model.CustomField
import com.example.data.model.CustomFieldType
import com.example.data.model.Item
import com.example.data.model.WarrantyPart
import com.example.data.model.WarrantyUnit
import com.example.ui.components.CategoryManageDialog
import com.example.ui.components.NothingButton
import com.example.ui.components.NothingChip
import com.example.ui.components.NothingDivider
import com.example.ui.components.NothingHeader
import com.example.ui.components.NothingTextField
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.DateUtils
import com.example.utils.FileStorageHelper
import com.example.utils.OpenRouterParseOutcome
import com.example.utils.OpenRouterService
import com.example.utils.PendingAttachment
import com.example.utils.WarrantyParserUtils
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId

data class WarrantyPartUIState(
    val id: Long = 0L,
    var label: String = "Warranty",
    var durationValue: Int = 1,
    var durationUnit: String = WarrantyUnit.YEARS,
    var startDateStr: String = "",
    var userEditedStartDate: Boolean = false
) {
    fun getEffectiveStartDateMillis(purchaseDateMillis: Long): Long {
        return DateUtils.parseDateToMillis(startDateStr) ?: purchaseDateMillis
    }

    fun getCalculatedExpiryMillis(purchaseDateMillis: Long): Long {
        val startMillis = getEffectiveStartDateMillis(purchaseDateMillis)
        return DateUtils.calculateExpiryDate(startMillis, durationValue, durationUnit) ?: startMillis
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddInvoiceScreen(
    viewModel: MainViewModel,
    editItemId: Long? = null,
    onSaved: (Long) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler { onCancel() }

    val context = LocalContext.current
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var existingCreatedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var isLoaded by remember { mutableStateOf(editItemId == null) }

    // Form fields
    var title by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var sellerName by remember { mutableStateOf("") }
    var invoiceNumber by remember { mutableStateOf("") }
    var orderId by remember { mutableStateOf("") }

    val todayIso = remember { DateUtils.formatIsoDate(System.currentTimeMillis()) }
    var purchaseDateStr by remember { mutableStateOf(todayIso) }
    var amountStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Warranty Section State
    val coroutineScope = rememberCoroutineScope()
    var warrantyDescriptionText by remember { mutableStateOf("") }
    val warrantyPartsState = remember { mutableStateListOf<WarrantyPartUIState>() }
    var isParsingWarranty by remember { mutableStateOf(false) }
    var aiReviewStatusMessage by remember { mutableStateOf<String?>(null) }
    val discardedPartMessages = remember { mutableStateListOf<String>() }
    val unparsedLinesList = remember { mutableStateListOf<String>() }
    var unparsedSegmentsMessage by remember { mutableStateOf<String?>(null) }

    // Warranty Part Dialog State
    var showPartDialog by remember { mutableStateOf(false) }
    var editingPartIndex by remember { mutableStateOf<Int?>(null) }
    var dialogLabel by remember { mutableStateOf("") }
    var dialogDurationValueStr by remember { mutableStateOf("1") }
    var dialogDurationUnit by remember { mutableStateOf(WarrantyUnit.YEARS) }
    var dialogStartDateStr by remember { mutableStateOf(todayIso) }
    var dialogUserEditedStart by remember { mutableStateOf(false) }

    // Custom fields & attachments
    val customFields = remember { mutableStateListOf<CustomField>() }
    val pendingAttachments = remember { mutableStateListOf<PendingAttachment>() }

    // Inline Validation Errors
    var titleError by remember { mutableStateOf<String?>(null) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var purchaseDateError by remember { mutableStateOf<String?>(null) }

    // Category Dialog State
    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var categoryDialogError by remember { mutableStateOf<String?>(null) }

    // Default category selection once categories load
    LaunchedEffect(categories) {
        if (selectedCategoryId == null && categories.isNotEmpty() && editItemId == null) {
            selectedCategoryId = categories.first().id
        }
    }

    // Load existing item if editing
    LaunchedEffect(editItemId) {
        if (editItemId != null && editItemId > 0L) {
            val details = viewModel.repository.getItemWithDetailsByIdOnce(editItemId)
            if (details != null) {
                val item = details.item
                existingCreatedAt = item.createdAt
                title = item.title
                selectedCategoryId = item.categoryId
                sellerName = item.sellerName
                invoiceNumber = item.invoiceNumber
                orderId = item.orderId
                purchaseDateStr = DateUtils.formatIsoDate(item.purchaseDate)
                amountStr = item.amount?.let { if (it > 0) it.toString() else "" } ?: ""
                notes = item.notes

                warrantyPartsState.clear()
                if (details.warrantyParts.isNotEmpty()) {
                    warrantyPartsState.addAll(
                        details.warrantyParts.sortedBy { it.sortOrder }.map { part ->
                            WarrantyPartUIState(
                                id = part.id,
                                label = part.label,
                                durationValue = part.durationValue,
                                durationUnit = part.durationUnit,
                                startDateStr = DateUtils.formatIsoDate(part.startDate),
                                userEditedStartDate = part.startDate != item.purchaseDate
                            )
                        }
                    )
                }

                customFields.clear()
                customFields.addAll(details.customFields.sortedBy { it.sortOrder })

                pendingAttachments.clear()
                pendingAttachments.addAll(
                    details.attachments.map { att ->
                        PendingAttachment(
                            existingAttachmentId = att.id,
                            uri = null,
                            existingLocalPath = att.localPath,
                            fileName = att.fileName,
                            mimeType = att.mimeType,
                            sizeBytes = att.sizeBytes
                        )
                    }
                )
            }
            isLoaded = true
        }
    }

    val titleFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isLoaded) {
        if (isLoaded) {
            try {
                titleFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Templates for selected category
    val activeCategoryId = selectedCategoryId ?: 0L
    val categoryTemplates by viewModel.repository.getTemplatesForCategory(activeCategoryId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // File Picker Launcher (PDF or Image)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val metadata = FileStorageHelper.inspectUriMetadata(context, uri)
            pendingAttachments.add(metadata)
        }
    }

    // Camera Photo Attachment Launcher
    var cameraTempFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedFile = cameraTempFile
        if (success && capturedFile != null && capturedFile.exists()) {
            pendingAttachments.add(
                PendingAttachment(
                    uri = Uri.fromFile(capturedFile),
                    fileName = capturedFile.name,
                    mimeType = "image/jpeg",
                    sizeBytes = capturedFile.length()
                )
            )
        }
    }

    fun showDatePicker(initialDateStr: String, onDateSelected: (String) -> Unit) {
        val millis = DateUtils.parseDateToMillis(initialDateStr) ?: System.currentTimeMillis()
        val localDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        DatePickerDialog(
            context,
            { _, year, monthZeroBased, dayOfMonth ->
                val pickedMillis = DateUtils.localDateToMillis(year, monthZeroBased + 1, dayOfMonth)
                onDateSelected(DateUtils.formatIsoDate(pickedMillis))
            },
            localDate.year,
            localDate.monthValue - 1,
            localDate.dayOfMonth
        ).show()
    }

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NothingButton(
                    text = "CANCEL",
                    isPrimary = false,
                    onClick = onCancel,
                    contentDescription = "Cancel item creation or editing",
                    modifier = Modifier.weight(1f),
                    testTag = "add_cancel_button"
                )

                NothingButton(
                    text = "SAVE",
                    isPrimary = true,
                    onClick = {
                        // Validate required fields inline
                        var hasError = false
                        val titleValidation = ValidationUtils.validateTitle(title)
                        val cleanTitle: String
                        if (titleValidation is ValidationResult.Invalid) {
                            titleError = titleValidation.errorMessage
                            hasError = true
                            cleanTitle = ""
                        } else {
                            cleanTitle = (titleValidation as ValidationResult.Valid).cleanedValue
                            titleError = null
                        }

                        if (selectedCategoryId == null) {
                            categoryError = "Category is required"
                            hasError = true
                        } else {
                            categoryError = null
                        }

                        val validPurchaseMillis = DateUtils.parseDateToMillis(purchaseDateStr)
                        if (validPurchaseMillis == null) {
                            purchaseDateError = "Valid date (YYYY-MM-DD) is required"
                            hasError = true
                        } else {
                            purchaseDateError = null
                        }

                        if (hasError || validPurchaseMillis == null || selectedCategoryId == null) {
                            return@NothingButton
                        }

                        val itemToSave = Item(
                            id = editItemId ?: 0L,
                            title = cleanTitle,
                            categoryId = selectedCategoryId!!,
                            sellerName = sellerName.trim(),
                            invoiceNumber = invoiceNumber.trim(),
                            orderId = orderId.trim(),
                            purchaseDate = validPurchaseMillis,
                            amount = amountStr.trim().toDoubleOrNull(),
                            notes = notes.trim(),
                            createdAt = existingCreatedAt
                        )

                        val finalWarrantyPartsToSave = warrantyPartsState.mapIndexed { index, partState ->
                            val startMillis = partState.getEffectiveStartDateMillis(validPurchaseMillis)
                            val expiryMillis = partState.getCalculatedExpiryMillis(validPurchaseMillis)
                            WarrantyPart(
                                id = partState.id,
                                itemId = editItemId ?: 0L,
                                label = partState.label.trim().take(30).ifBlank { "Warranty" },
                                durationValue = partState.durationValue,
                                durationUnit = partState.durationUnit,
                                startDate = startMillis,
                                expiryDate = expiryMillis,
                                sortOrder = index
                            )
                        }

                        viewModel.saveOrUpdateItem(
                            item = itemToSave,
                            customFields = customFields.toList(),
                            pendingAttachments = pendingAttachments.toList(),
                            warrantyParts = finalWarrantyPartsToSave
                        ) { savedId ->
                            onSaved(savedId)
                        }
                    },
                    contentDescription = "Save bill item and warranty parts",
                    modifier = Modifier.weight(1f),
                    testTag = "add_save_button"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (!isLoaded) return@Scaffold

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                NothingHeader(
                    title = if (editItemId == null) "NEW BILL / ITEM" else "EDIT BILL / ITEM",
                    subtitle = "Manual entry • Nothing OS Vault"
                )
            }

            // 1. Title (Required)
            item {
                NothingTextField(
                    value = title,
                    onValueChange = { input ->
                        val singleLine = ValidationUtils.replaceLineBreaksWithSpaces(input)
                        title = singleLine
                        val normalized = ValidationUtils.normalizeTitle(singleLine)
                        titleError = when {
                            normalized.length > ValidationUtils.MAX_TITLE_LENGTH ->
                                "Title cannot exceed ${ValidationUtils.MAX_TITLE_LENGTH} characters (${normalized.length}/${ValidationUtils.MAX_TITLE_LENGTH})"
                            titleError != null && normalized.isNotBlank() && normalized.length <= ValidationUtils.MAX_TITLE_LENGTH ->
                                null
                            else -> titleError
                        }
                    },
                    label = "TITLE *",
                    placeholder = "e.g. Washing machine",
                    singleLine = true,
                    errorMessage = titleError,
                    helperText = "Short product name, for example Washing machine. Put longer details in Notes.",
                    counterText = if (title.length >= 40) "${title.length}/${ValidationUtils.MAX_TITLE_LENGTH}" else null,
                    textFieldModifier = Modifier.focusRequester(titleFocusRequester),
                    testTag = "input_title"
                )
            }

            // 2. Category (Required, pill chips + "+ NEW" chip)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CATEGORY *",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                            color = if (categoryError != null) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val currentCat = categories.find { it.id == selectedCategoryId }
                        if (currentCat != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        editingCategory = currentCat
                                        categoryDialogError = null
                                        showCategoryDialog = true
                                    }
                                    .padding(4.dp)
                                    .testTag("edit_selected_category_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Category",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "EDIT '${currentCat.name}'",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories, key = { it.id }) { category ->
                            NothingChip(
                                text = category.name,
                                isSelected = selectedCategoryId == category.id,
                                colorMarker = category.colorMarker,
                                onClick = {
                                    selectedCategoryId = category.id
                                    categoryError = null
                                },
                                onLongClick = {
                                    editingCategory = category
                                    categoryDialogError = null
                                    showCategoryDialog = true
                                },
                                testTag = "add_category_chip_${category.name}"
                            )
                        }

                        item {
                            NothingChip(
                                text = "+ NEW",
                                isSelected = false,
                                onClick = {
                                    editingCategory = null
                                    categoryDialogError = null
                                    showCategoryDialog = true
                                },
                                testTag = "add_new_category_chip"
                            )
                        }
                    }

                    if (!categoryError.isNullOrBlank()) {
                        Text(
                            text = categoryError!!.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = NothingRed,
                            modifier = Modifier.testTag("input_category_error")
                        )
                    }
                }
            }

            // 3. Seller / Store Name & Amount
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NothingTextField(
                        value = sellerName,
                        onValueChange = { sellerName = it },
                        label = "SELLER OR STORE NAME",
                        placeholder = "e.g. Croma, Flipkart",
                        modifier = Modifier.weight(1f),
                        testTag = "input_seller_name"
                    )

                    NothingTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = "AMOUNT (OPTIONAL)",
                        placeholder = "0.00",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        testTag = "input_amount"
                    )
                }
            }

            // 4. Invoice / Bill Number & Order ID
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NothingTextField(
                        value = invoiceNumber,
                        onValueChange = { invoiceNumber = it },
                        label = "INVOICE / BILL NUMBER",
                        placeholder = "INV-2026-01",
                        modifier = Modifier.weight(1f),
                        testTag = "input_invoice_number"
                    )

                    NothingTextField(
                        value = orderId,
                        onValueChange = { orderId = it },
                        label = "ORDER ID (OPTIONAL)",
                        placeholder = "OD-102938",
                        modifier = Modifier.weight(1f),
                        testTag = "input_order_id"
                    )
                }
            }

            // 5. Purchase or Bill Date (Required, Date Picker)
            item {
                NothingTextField(
                    value = purchaseDateStr,
                    onValueChange = {
                        purchaseDateStr = it
                        warrantyPartsState.forEach { part ->
                            if (!part.userEditedStartDate) {
                                part.startDateStr = it
                            }
                        }
                        if (DateUtils.parseDateToMillis(it) != null) {
                            purchaseDateError = null
                        }
                    },
                    label = "PURCHASE OR BILL DATE (YYYY-MM-DD) *",
                    placeholder = "YYYY-MM-DD",
                    errorMessage = purchaseDateError,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                showDatePicker(purchaseDateStr) { picked ->
                                    purchaseDateStr = picked
                                    warrantyPartsState.forEach { part ->
                                        if (!part.userEditedStartDate) {
                                            part.startDateStr = picked
                                        }
                                    }
                                    purchaseDateError = null
                                }
                            },
                            modifier = Modifier.testTag("purchase_date_picker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Pick Purchase Date",
                                tint = NothingRed
                            )
                        }
                    },
                    testTag = "input_purchase_date"
                )
            }

            item { NothingDivider() }

            // 6. Warranty Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WARRANTY PARTS (${warrantyPartsState.size})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = DotoFontFamily,
                            letterSpacing = 1.5.sp
                        ),
                        color = NothingRed
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NothingTextField(
                        value = warrantyDescriptionText,
                        onValueChange = { warrantyDescriptionText = it },
                        label = "DESCRIBE WARRANTY",
                        placeholder = "Describe the warranty, for example: 1 year body, 2 year motor",
                        singleLine = false,
                        testTag = "input_warranty_description"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NothingButton(
                            text = if (isParsingWarranty) "PARSING..." else "PARSE",
                            isPrimary = true,
                            enabled = !isParsingWarranty,
                            onClick = {
                                unparsedSegmentsMessage = null
                                aiReviewStatusMessage = null
                                discardedPartMessages.clear()
                                unparsedLinesList.clear()

                                if (warrantyDescriptionText.isNotBlank()) {
                                    val apiKey = viewModel.settingsManager.openRouterApiKey.trim()
                                    val preferredModel = viewModel.settingsManager.selectedAiModel

                                    if (apiKey.isNotBlank()) {
                                        isParsingWarranty = true
                                        coroutineScope.launch {
                                            // PRIVACY: Only productName (title) and warrantyText are sent.
                                            val outcome = OpenRouterService.parseWarrantyWithAi(
                                                productName = title,
                                                warrantyText = warrantyDescriptionText,
                                                apiKey = apiKey,
                                                preferredModel = preferredModel
                                            )
                                            isParsingWarranty = false

                                            when (outcome) {
                                                is OpenRouterParseOutcome.Success -> {
                                                    outcome.validParts.forEach { parsed ->
                                                        warrantyPartsState.add(
                                                            WarrantyPartUIState(
                                                                label = parsed.label,
                                                                durationValue = parsed.durationValue,
                                                                durationUnit = parsed.durationUnit,
                                                                startDateStr = purchaseDateStr,
                                                                userEditedStartDate = false
                                                            )
                                                        )
                                                    }
                                                    discardedPartMessages.addAll(outcome.discardedMessages)
                                                    val fallbackTag = if (outcome.usedFallbackModel) " [Fallback: ${outcome.modelUsed}]" else " [${outcome.modelUsed}]"
                                                    aiReviewStatusMessage = "AI parsed ${outcome.validParts.size} part(s)$fallbackTag. Review below and tap SAVE to confirm."
                                                }
                                                is OpenRouterParseOutcome.Failure -> {
                                                    val localResult = WarrantyParserUtils.parseLocal(warrantyDescriptionText)
                                                    localResult.parts.forEach { parsed ->
                                                        warrantyPartsState.add(
                                                            WarrantyPartUIState(
                                                                label = parsed.label,
                                                                durationValue = parsed.durationValue,
                                                                durationUnit = parsed.durationUnit,
                                                                startDateStr = purchaseDateStr,
                                                                userEditedStartDate = false
                                                            )
                                                        )
                                                    }
                                                    aiReviewStatusMessage = "AI request failed (${outcome.reason}). Used local fallback parser — review below and tap SAVE to confirm."
                                                    if (localResult.unparsedSegments.isNotEmpty()) {
                                                        unparsedLinesList.addAll(localResult.unparsedSegments)
                                                        unparsedSegmentsMessage = "Could not parse: ${localResult.unparsedSegments.joinToString(", ")}. Add manually below."
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        val result = WarrantyParserUtils.parseLocal(warrantyDescriptionText)
                                        if (result.parts.isNotEmpty()) {
                                            result.parts.forEach { parsed ->
                                                warrantyPartsState.add(
                                                    WarrantyPartUIState(
                                                        label = parsed.label,
                                                        durationValue = parsed.durationValue,
                                                        durationUnit = parsed.durationUnit,
                                                        startDateStr = purchaseDateStr,
                                                        userEditedStartDate = false
                                                    )
                                                )
                                            }
                                            aiReviewStatusMessage = "Parsed ${result.parts.size} part(s) locally. Review below and tap SAVE to confirm."
                                        }
                                        if (result.unparsedSegments.isNotEmpty()) {
                                            unparsedLinesList.addAll(result.unparsedSegments)
                                            unparsedSegmentsMessage = "Could not parse: ${result.unparsedSegments.joinToString(", ")}. Add manually below."
                                        }
                                    }
                                }
                            },
                            contentDescription = "Parse warranty description text into structured parts",
                            modifier = Modifier.weight(1f),
                            testTag = "warranty_parse_button"
                        )

                        NothingButton(
                            text = "+ ADD PART",
                            isPrimary = false,
                            onClick = {
                                editingPartIndex = null
                                dialogLabel = "Warranty"
                                dialogDurationValueStr = "1"
                                dialogDurationUnit = WarrantyUnit.YEARS
                                dialogStartDateStr = purchaseDateStr
                                dialogUserEditedStart = false
                                showPartDialog = true
                            },
                            contentDescription = "Add a new warranty part manually",
                            modifier = Modifier.weight(1f),
                            testTag = "warranty_add_part_button"
                        )
                    }

                    if (isParsingWarranty) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, NothingRed, RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("warranty_ai_loading"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = NothingRed
                            )
                            Text(
                                text = "PARSING WARRANTY WITH OPENROUTER AI...",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = DotoFontFamily,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    if (!aiReviewStatusMessage.isNullOrBlank()) {
                        Text(
                            text = aiReviewStatusMessage!!,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = SpaceGroteskFontFamily
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .testTag("warranty_review_status_message")
                        )
                    }

                    discardedPartMessages.forEachIndexed { idx, msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingRed,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .testTag("warranty_discarded_message_$idx")
                        )
                    }

                    if (!unparsedSegmentsMessage.isNullOrBlank()) {
                        Text(
                            text = unparsedSegmentsMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingRed,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .testTag("warranty_unparsed_message")
                        )
                    }

                    if (unparsedLinesList.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, NothingRed, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "UNPARSED LINES (ADD MANUALLY)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = DotoFontFamily,
                                    letterSpacing = 1.sp
                                ),
                                color = NothingRed
                            )

                            unparsedLinesList.forEachIndexed { idx, line ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("unparsed_line_row_$idx"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "\"$line\"",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = SpaceGroteskFontFamily
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    )

                                    NothingChip(
                                        text = "+ ADD MANUALLY",
                                        isSelected = false,
                                        onClick = {
                                            editingPartIndex = null
                                            dialogLabel = line.take(30).ifBlank { "Warranty" }
                                            dialogDurationValueStr = "1"
                                            dialogDurationUnit = WarrantyUnit.YEARS
                                            dialogStartDateStr = purchaseDateStr
                                            dialogUserEditedStart = false
                                            showPartDialog = true
                                        },
                                        testTag = "unparsed_line_add_button_$idx"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (warrantyPartsState.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO WARRANTY PARTS ADDED\nEnter text above and tap PARSE, or tap + ADD PART",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = SpaceGroteskFontFamily
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(warrantyPartsState) { index, part ->
                    val purchaseMillis = DateUtils.parseDateToMillis(purchaseDateStr) ?: System.currentTimeMillis()
                    val startMillis = part.getEffectiveStartDateMillis(purchaseMillis)
                    val expiryMillis = part.getCalculatedExpiryMillis(purchaseMillis)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(NothingRed)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = part.label.uppercase(),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            editingPartIndex = index
                                            dialogLabel = part.label
                                            dialogDurationValueStr = part.durationValue.toString()
                                            dialogDurationUnit = part.durationUnit
                                            dialogStartDateStr = if (part.startDateStr.isNotBlank()) part.startDateStr else purchaseDateStr
                                            dialogUserEditedStart = part.userEditedStartDate
                                            showPartDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Warranty Part ${part.label}",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            warrantyPartsState.removeAt(index)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Warranty Part ${part.label}",
                                            modifier = Modifier.size(16.dp),
                                            tint = NothingRed
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "DURATION: ${part.durationValue} ${part.durationUnit}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = DotoFontFamily,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = "EXP: ${DateUtils.formatIsoDate(expiryMillis)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = DotoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = NothingRed
                                )
                            }

                            Text(
                                text = "START: ${DateUtils.formatIsoDate(startMillis)} • Use delivery date if it differs.",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            item { NothingDivider() }

            // 7. Section 2a: Custom Fields
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CUSTOM FIELDS (${customFields.size})",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = DotoFontFamily,
                                letterSpacing = 1.5.sp
                            ),
                            color = NothingRed
                        )

                        NothingButton(
                            text = "+ ADD FIELD",
                            isPrimary = false,
                            onClick = {
                                customFields.add(
                                    CustomField(
                                        itemId = editItemId ?: 0L,
                                        name = "",
                                        type = CustomFieldType.TEXT,
                                        value = "",
                                        sortOrder = customFields.size
                                    )
                                )
                            },
                            modifier = Modifier.width(140.dp),
                            testTag = "add_custom_field_button"
                        )
                    }

                    if (categoryTemplates.isNotEmpty()) {
                        Text(
                            text = "SAVED TEMPLATES FOR THIS CATEGORY:",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categoryTemplates.forEach { tpl ->
                                NothingChip(
                                    text = "+ ${tpl.name} (${tpl.type})",
                                    isSelected = false,
                                    onClick = {
                                        if (customFields.none { it.name.equals(tpl.name, ignoreCase = true) }) {
                                            customFields.add(
                                                CustomField(
                                                    itemId = editItemId ?: 0L,
                                                    name = tpl.name,
                                                    type = tpl.type,
                                                    value = if (tpl.type == CustomFieldType.YES_NO) "YES" else "",
                                                    sortOrder = customFields.size
                                                )
                                            )
                                        }
                                    },
                                    testTag = "template_chip_${tpl.name}"
                                )
                            }
                        }
                    }
                }
            }

            itemsIndexed(customFields) { index, field ->
                CustomFieldEditorCard(
                    index = index,
                    field = field,
                    onFieldChange = { updated -> customFields[index] = updated },
                    onRemove = { customFields.removeAt(index) },
                    onSaveTemplate = {
                        val catId = selectedCategoryId
                        if (catId != null && field.name.isNotBlank()) {
                            viewModel.saveCustomFieldTemplate(catId, field.name, field.type)
                        }
                    },
                    onPickDate = { currentVal, onPicked ->
                        showDatePicker(currentVal, onPicked)
                    }
                )
            }

            item { NothingDivider() }

            // 8. Section 2b: Attachments
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "ATTACHMENTS (${pendingAttachments.size})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = DotoFontFamily,
                            letterSpacing = 1.5.sp
                        ),
                        color = NothingRed
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NothingButton(
                            text = "ATTACH FILE",
                            icon = Icons.Default.AttachFile,
                            isPrimary = false,
                            onClick = {
                                filePickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "attach_file_button"
                        )

                        NothingButton(
                            text = "TAKE PHOTO",
                            icon = Icons.Default.CameraAlt,
                            isPrimary = false,
                            onClick = {
                                try {
                                    val temp = File(
                                        context.cacheDir,
                                        "photo_${System.currentTimeMillis()}.jpg"
                                    )
                                    cameraTempFile = temp
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        temp
                                    )
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "take_photo_attachment_button"
                        )
                    }
                }
            }

            itemsIndexed(pendingAttachments, key = { _, att -> att.id }) { index, att ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = NothingRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = att.fileName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${att.mimeType.uppercase()} • ${DateUtils.formatFileSize(att.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { pendingAttachments.removeAt(index) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Attachment",
                            tint = NothingRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            item { NothingDivider() }

            // 9. Notes (Multiline, optional)
            item {
                NothingTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "NOTES (OPTIONAL)",
                    placeholder = "Coverage details, pasted invoice tables, service center info...",
                    helperText = "Paste detailed descriptions, specifications, or invoice tables here.",
                    singleLine = false,
                    testTag = "input_notes"
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showPartDialog) {
        val purchaseMillis = DateUtils.parseDateToMillis(purchaseDateStr) ?: System.currentTimeMillis()
        val currentStartMillis = DateUtils.parseDateToMillis(dialogStartDateStr) ?: purchaseMillis
        val currentDurationVal = dialogDurationValueStr.trim().toIntOrNull()
        val previewExpiryMillis = DateUtils.calculateExpiryDate(currentStartMillis, currentDurationVal, dialogDurationUnit) ?: currentStartMillis

        AlertDialog(
            onDismissRequest = { showPartDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = {
                Text(
                    text = if (editingPartIndex == null) "ADD WARRANTY PART" else "EDIT WARRANTY PART",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = DotoFontFamily,
                        letterSpacing = 1.5.sp
                    ),
                    color = NothingRed
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NothingTextField(
                        value = dialogLabel,
                        onValueChange = { dialogLabel = it.take(30) },
                        label = "LABEL (MAX 30 CHARS)",
                        placeholder = "e.g. Motor, Body, Compressor",
                        counterText = "${dialogLabel.length}/30",
                        testTag = "part_dialog_label_input"
                    )

                    NothingTextField(
                        value = dialogDurationValueStr,
                        onValueChange = { dialogDurationValueStr = it.filter { ch -> ch.isDigit() } },
                        label = "DURATION VALUE",
                        placeholder = "1",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        testTag = "part_dialog_duration_input"
                    )

                    Column {
                        Text(
                            text = "DURATION UNIT",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WarrantyUnit.ALL.forEach { unit ->
                                NothingChip(
                                    text = unit,
                                    isSelected = dialogDurationUnit == unit,
                                    onClick = { dialogDurationUnit = unit },
                                    modifier = Modifier.weight(1f),
                                    testTag = "part_dialog_unit_$unit"
                                )
                            }
                        }
                    }

                    NothingTextField(
                        value = dialogStartDateStr,
                        onValueChange = {
                            dialogStartDateStr = it
                            dialogUserEditedStart = true
                        },
                        label = "WARRANTY START DATE",
                        placeholder = "YYYY-MM-DD",
                        helperText = "Use the delivery date if it differs.",
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    showDatePicker(dialogStartDateStr) { picked ->
                                        dialogStartDateStr = picked
                                        dialogUserEditedStart = true
                                    }
                                },
                                modifier = Modifier.testTag("part_dialog_start_date_picker")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "Pick Warranty Start Date",
                                    tint = NothingRed
                                )
                            }
                        },
                        testTag = "part_dialog_start_date_input"
                    )

                    Text(
                        text = "CALCULATED EXPIRY: ${DateUtils.formatIsoDate(previewExpiryMillis)}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = DotoFontFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = NothingRed
                    )
                }
            },
            confirmButton = {
                NothingButton(
                    text = "SAVE PART",
                    isPrimary = true,
                    onClick = {
                        val durationVal = dialogDurationValueStr.trim().toIntOrNull() ?: 1
                        val cleanLabel = dialogLabel.trim().take(30).ifBlank { "Warranty" }
                        val effectiveStartStr = dialogStartDateStr.ifBlank { purchaseDateStr }

                        val newPartState = WarrantyPartUIState(
                            id = if (editingPartIndex != null && editingPartIndex!! < warrantyPartsState.size) warrantyPartsState[editingPartIndex!!].id else 0L,
                            label = cleanLabel,
                            durationValue = durationVal,
                            durationUnit = dialogDurationUnit,
                            startDateStr = effectiveStartStr,
                            userEditedStartDate = dialogUserEditedStart || (effectiveStartStr != purchaseDateStr)
                        )

                        if (editingPartIndex != null && editingPartIndex!! < warrantyPartsState.size) {
                            warrantyPartsState[editingPartIndex!!] = newPartState
                        } else {
                            warrantyPartsState.add(newPartState)
                        }
                        showPartDialog = false
                    },
                    modifier = Modifier.width(130.dp),
                    testTag = "part_dialog_save_button"
                )
            },
            dismissButton = {
                NothingButton(
                    text = "CANCEL",
                    isPrimary = false,
                    onClick = { showPartDialog = false },
                    modifier = Modifier.width(100.dp),
                    testTag = "part_dialog_cancel_button"
                )
            }
        )
    }

    if (showCategoryDialog) {
        CategoryManageDialog(
            initialCategory = editingCategory,
            errorMessage = categoryDialogError,
            onDismiss = { showCategoryDialog = false },
            onSave = { name, marker ->
                val target = editingCategory
                if (target == null) {
                    viewModel.createCategory(name, marker) { res ->
                        res.onSuccess { created ->
                            selectedCategoryId = created.id
                            categoryError = null
                            showCategoryDialog = false
                        }.onFailure { err ->
                            categoryDialogError = err.message
                        }
                    }
                } else {
                    viewModel.renameCategory(target, name, marker) { res ->
                        res.onSuccess {
                            showCategoryDialog = false
                        }.onFailure { err ->
                            categoryDialogError = err.message
                        }
                    }
                }
            },
            onDeleteRequest = { catToDelete ->
                viewModel.deleteCustomCategory(catToDelete) { res ->
                    res.onSuccess { uncategorized ->
                        if (selectedCategoryId == catToDelete.id) {
                            selectedCategoryId = uncategorized.id
                        }
                        showCategoryDialog = false
                    }
                }
            }
        )
    }
}

@Composable
private fun CustomFieldEditorCard(
    index: Int,
    field: CustomField,
    onFieldChange: (CustomField) -> Unit,
    onRemove: () -> Unit,
    onSaveTemplate: () -> Unit,
    onPickDate: (String, (String) -> Unit) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FIELD #${index + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = DotoFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (field.name.isNotBlank()) {
                        IconButton(
                            onClick = onSaveTemplate,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("save_template_field_$index")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = "Save Field as Category Template",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("remove_custom_field_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Custom Field",
                            tint = NothingRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Type selector row: TEXT, NUMBER, DATE, YES/NO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CustomFieldType.ALL.forEach { typeOption ->
                    NothingChip(
                        text = typeOption,
                        isSelected = field.type == typeOption,
                        onClick = {
                            val defaultVal = when (typeOption) {
                                CustomFieldType.YES_NO -> "YES"
                                CustomFieldType.DATE -> DateUtils.formatIsoDate(System.currentTimeMillis())
                                else -> ""
                            }
                            onFieldChange(field.copy(type = typeOption, value = defaultVal))
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "custom_field_type_${index}_$typeOption"
                    )
                }
            }

            NothingTextField(
                value = field.name,
                onValueChange = { onFieldChange(field.copy(name = it)) },
                label = "FIELD NAME",
                placeholder = "e.g. Serial Number, Policy ID, Service Date",
                testTag = "custom_field_name_$index"
            )

            when (field.type) {
                CustomFieldType.YES_NO -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "VALUE (YES / NO)",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("YES", "NO").forEach { yn ->
                                NothingChip(
                                    text = yn,
                                    isSelected = field.value.equals(yn, ignoreCase = true),
                                    onClick = { onFieldChange(field.copy(value = yn)) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "custom_field_yesno_${index}_$yn"
                                )
                            }
                        }
                    }
                }

                CustomFieldType.DATE -> {
                    NothingTextField(
                        value = field.value,
                        onValueChange = { onFieldChange(field.copy(value = it)) },
                        label = "DATE VALUE (YYYY-MM-DD)",
                        placeholder = "YYYY-MM-DD",
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    onPickDate(field.value) { picked ->
                                        onFieldChange(field.copy(value = picked))
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "Pick Date",
                                    tint = NothingRed
                                )
                            }
                        },
                        testTag = "custom_field_value_$index"
                    )
                }

                CustomFieldType.NUMBER -> {
                    NothingTextField(
                        value = field.value,
                        onValueChange = { onFieldChange(field.copy(value = it)) },
                        label = "NUMBER VALUE",
                        placeholder = "0",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        testTag = "custom_field_value_$index"
                    )
                }

                else -> {
                    NothingTextField(
                        value = field.value,
                        onValueChange = { onFieldChange(field.copy(value = it)) },
                        label = "TEXT VALUE",
                        placeholder = "Enter value...",
                        testTag = "custom_field_value_$index"
                    )
                }
            }
        }
    }
}
