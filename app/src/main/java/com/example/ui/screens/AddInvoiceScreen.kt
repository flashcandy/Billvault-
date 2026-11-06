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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Category
import com.example.data.model.CustomField
import com.example.data.model.CustomFieldType
import com.example.data.model.Item
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
import com.example.utils.PendingAttachment
import java.io.File
import java.time.Instant
import java.time.ZoneId

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
    var warrantyStartDateStr by remember { mutableStateOf(todayIso) }
    var userEditedWarrantyStart by remember { mutableStateOf(false) }

    var amountStr by remember { mutableStateOf("") }
    var warrantyValueStr by remember { mutableStateOf("") }
    var warrantyUnit by remember { mutableStateOf(WarrantyUnit.MONTHS) }
    var notes by remember { mutableStateOf("") }

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
                warrantyStartDateStr = DateUtils.formatIsoDate(item.warrantyStartDate)
                userEditedWarrantyStart = item.warrantyStartDate != item.purchaseDate
                amountStr = item.amount?.let { if (it > 0) it.toString() else "" } ?: ""
                warrantyValueStr = item.warrantyValue?.let { if (it > 0) it.toString() else "" } ?: ""
                warrantyUnit = item.warrantyUnit
                notes = item.notes

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

    // Templates for selected category
    val activeCategoryId = selectedCategoryId ?: 0L
    val categoryTemplates by viewModel.repository.getTemplatesForCategory(activeCategoryId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Live calculation of Expiry Date
    val parsedPurchaseMillis = DateUtils.parseDateToMillis(purchaseDateStr)
    val parsedWarrantyStartMillis = DateUtils.parseDateToMillis(warrantyStartDateStr)
        ?: parsedPurchaseMillis
        ?: System.currentTimeMillis()
    val parsedWarrantyValue = warrantyValueStr.trim().toIntOrNull()
    val calculatedExpiryMillis = remember(parsedWarrantyStartMillis, parsedWarrantyValue, warrantyUnit) {
        DateUtils.calculateExpiryDate(
            startDateMillis = parsedWarrantyStartMillis,
            warrantyValue = parsedWarrantyValue,
            warrantyUnit = warrantyUnit
        )
    }

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
                    modifier = Modifier.weight(1f),
                    testTag = "add_cancel_button"
                )

                NothingButton(
                    text = "SAVE",
                    isPrimary = true,
                    onClick = {
                        // Validate required fields inline
                        var hasError = false
                        if (title.trim().isEmpty()) {
                            titleError = "Title is required"
                            hasError = true
                        } else {
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

                        val finalWarrantyStart = DateUtils.parseDateToMillis(warrantyStartDateStr)
                            ?: validPurchaseMillis

                        val itemToSave = Item(
                            id = editItemId ?: 0L,
                            title = title.trim(),
                            categoryId = selectedCategoryId!!,
                            sellerName = sellerName.trim(),
                            invoiceNumber = invoiceNumber.trim(),
                            orderId = orderId.trim(),
                            purchaseDate = validPurchaseMillis,
                            amount = amountStr.trim().toDoubleOrNull(),
                            warrantyValue = parsedWarrantyValue?.takeIf { it > 0 },
                            warrantyUnit = warrantyUnit,
                            warrantyStartDate = finalWarrantyStart,
                            expiryDate = calculatedExpiryMillis,
                            notes = notes.trim(),
                            createdAt = existingCreatedAt
                        )

                        viewModel.saveOrUpdateItem(
                            item = itemToSave,
                            customFields = customFields.toList(),
                            pendingAttachments = pendingAttachments.toList()
                        ) { savedId ->
                            onSaved(savedId)
                        }
                    },
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
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) titleError = null
                    },
                    label = "TITLE *",
                    placeholder = "e.g. Washing machine",
                    errorMessage = titleError,
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
                        if (!userEditedWarrantyStart) {
                            warrantyStartDateStr = it
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
                                    if (!userEditedWarrantyStart) {
                                        warrantyStartDateStr = picked
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

            // 6. Warranty & Expiry Section
            item {
                Text(
                    text = "WARRANTY & EXPIRY CALCULATION",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = DotoFontFamily,
                        letterSpacing = 1.5.sp
                    ),
                    color = NothingRed
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NothingTextField(
                        value = warrantyValueStr,
                        onValueChange = { warrantyValueStr = it.filter { ch -> ch.isDigit() } },
                        label = "WARRANTY / EXPIRY DURATION",
                        placeholder = "Leave empty if no warranty",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        testTag = "input_warranty_value"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WarrantyUnit.ALL.forEach { unit ->
                            NothingChip(
                                text = unit,
                                isSelected = warrantyUnit == unit,
                                onClick = { warrantyUnit = unit },
                                modifier = Modifier.weight(1f),
                                testTag = "warranty_unit_$unit"
                            )
                        }
                    }
                }
            }

            // Warranty Start Date & Read-only Calculated Expiry Date
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NothingTextField(
                        value = warrantyStartDateStr,
                        onValueChange = {
                            warrantyStartDateStr = it
                            userEditedWarrantyStart = true
                        },
                        label = "WARRANTY START DATE",
                        placeholder = "YYYY-MM-DD",
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    showDatePicker(warrantyStartDateStr) { picked ->
                                        warrantyStartDateStr = picked
                                        userEditedWarrantyStart = true
                                    }
                                },
                                modifier = Modifier.testTag("warranty_start_date_picker_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "Pick Warranty Start Date",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "input_warranty_start_date"
                    )

                    val readOnlyExpiryText = if (calculatedExpiryMillis != null) {
                        DateUtils.formatIsoDate(calculatedExpiryMillis)
                    } else {
                        "NONE"
                    }

                    NothingTextField(
                        value = readOnlyExpiryText,
                        onValueChange = {},
                        readOnly = true,
                        label = "CALCULATED EXPIRY",
                        placeholder = "NONE",
                        modifier = Modifier.weight(1f),
                        testTag = "readonly_expiry_date"
                    )
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
                    placeholder = "Coverage details, service center info, or claim instructions...",
                    singleLine = false,
                    testTag = "input_notes"
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
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
