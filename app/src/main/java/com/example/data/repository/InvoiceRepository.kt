package com.example.data.repository

import android.content.Context
import com.example.data.local.AttachmentDao
import com.example.data.local.CategoryDao
import com.example.data.local.CustomFieldDao
import com.example.data.local.ItemDao
import com.example.data.local.ReminderDao
import com.example.data.model.Attachment
import com.example.data.model.Category
import com.example.data.model.CategoryMarker
import com.example.data.model.CustomField
import com.example.data.model.CustomFieldTemplate
import com.example.data.model.CustomFieldType
import com.example.data.model.Item
import com.example.data.model.ItemWithDetails
import com.example.data.model.Reminder
import com.example.data.model.WarrantyUnit
import com.example.data.preferences.SettingsManager
import com.example.utils.DateUtils
import com.example.utils.FileStorageHelper
import com.example.utils.PendingAttachment
import com.example.utils.ValidationResult
import com.example.utils.ValidationUtils
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class InvoiceRepository(
    private val categoryDao: CategoryDao,
    private val itemDao: ItemDao,
    private val customFieldDao: CustomFieldDao,
    private val attachmentDao: AttachmentDao,
    private val reminderDao: ReminderDao
) {

    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val allItemsWithDetails: Flow<List<ItemWithDetails>> = itemDao.getAllItemsWithDetails()
    val allItems: Flow<List<Item>> = itemDao.getAllItems()
    val pendingReminders: Flow<List<Reminder>> = reminderDao.getPendingReminders()

    fun getItemWithDetailsById(id: Long): Flow<ItemWithDetails?> =
        itemDao.getItemWithDetailsById(id)

    suspend fun getItemWithDetailsByIdOnce(id: Long): ItemWithDetails? =
        itemDao.getItemWithDetailsByIdOnce(id)

    suspend fun getItemById(id: Long): Item? = itemDao.getItemById(id)

    fun getTemplatesForCategory(categoryId: Long): Flow<List<CustomFieldTemplate>> =
        customFieldDao.getTemplatesForCategory(categoryId)

    suspend fun saveCustomFieldTemplate(categoryId: Long, name: String, type: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            customFieldDao.insertTemplate(
                CustomFieldTemplate(
                    categoryId = categoryId,
                    name = trimmed,
                    type = type
                )
            )
        }
    }

    suspend fun ensureDefaultCategories(): List<Category> {
        itemDao.deleteLegacyDummyItems()
        val existing = categoryDao.getAllCategoriesList()
        if (existing.isNotEmpty()) return existing

        val defaults = listOf(
            Category(name = "VEHICLE", isDefault = true, colorMarker = CategoryMarker.DOT_RED, sortOrder = 0),
            Category(name = "ELECTRONICS", isDefault = true, colorMarker = CategoryMarker.DOT_WHITE, sortOrder = 1),
            Category(name = "SHOPPING", isDefault = true, colorMarker = CategoryMarker.DOT_GRAY, sortOrder = 2),
            Category(name = "ELECTRIC BILLS", isDefault = true, colorMarker = CategoryMarker.SQUARE_RED, sortOrder = 3)
        )
        categoryDao.insertCategoriesIgnore(defaults)
        return categoryDao.getAllCategoriesList()
    }

    suspend fun createCategory(rawName: String, colorMarker: String): Result<Category> {
        val existing = categoryDao.getAllCategoriesList()
        val validation = ValidationUtils.validateCategoryName(
            rawName = rawName,
            existingNames = existing.map { it.name }
        )
        return when (validation) {
            is ValidationResult.Invalid -> Result.failure(IllegalArgumentException(validation.errorMessage))
            is ValidationResult.Valid -> {
                val nextSortOrder = (existing.maxOfOrNull { it.sortOrder } ?: 0) + 1
                val newCategory = Category(
                    name = validation.cleanedValue,
                    isDefault = false,
                    colorMarker = colorMarker,
                    sortOrder = nextSortOrder
                )
                val id = categoryDao.insertCategory(newCategory)
                Result.success(newCategory.copy(id = id))
            }
        }
    }

    suspend fun renameCategory(
        category: Category,
        newRawName: String,
        newColorMarker: String = category.colorMarker
    ): Result<Category> {
        val existing = categoryDao.getAllCategoriesList()
        val validation = ValidationUtils.validateCategoryName(
            rawName = newRawName,
            existingNames = existing.map { it.name },
            currentName = category.name
        )
        return when (validation) {
            is ValidationResult.Invalid -> Result.failure(IllegalArgumentException(validation.errorMessage))
            is ValidationResult.Valid -> {
                val updated = category.copy(
                    name = validation.cleanedValue,
                    colorMarker = newColorMarker
                )
                categoryDao.updateCategory(updated)
                Result.success(updated)
            }
        }
    }

    /**
     * Deletes a custom category (default categories cannot be deleted) and moves its items to "UNCATEGORIZED".
     */
    suspend fun deleteCustomCategory(category: Category): Result<Category> {
        if (category.isDefault) {
            return Result.failure(IllegalStateException("Default categories cannot be deleted"))
        }

        // Find or create UNCATEGORIZED
        var uncategorized = categoryDao.getCategoryByName("UNCATEGORIZED")
        if (uncategorized == null) {
            val existing = categoryDao.getAllCategoriesList()
            val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: 0) + 1
            val uncat = Category(
                name = "UNCATEGORIZED",
                isDefault = true,
                colorMarker = CategoryMarker.DOT_GRAY,
                sortOrder = nextOrder
            )
            val id = categoryDao.insertCategory(uncat)
            uncategorized = uncat.copy(id = id)
        }

        itemDao.moveItemsToCategory(oldCategoryId = category.id, newCategoryId = uncategorized.id)
        categoryDao.deleteCategoryById(category.id)
        return Result.success(uncategorized)
    }

    /**
     * Saves or updates an Item along with its CustomFields, Attachments, and Reminders.
     */
    suspend fun saveOrUpdateItem(
        context: Context,
        settingsManager: SettingsManager,
        item: Item,
        customFields: List<CustomField>,
        pendingAttachments: List<PendingAttachment>
    ): Long {
        val computedExpiry = DateUtils.calculateExpiryDate(
            startDateMillis = item.warrantyStartDate,
            warrantyValue = item.warrantyValue,
            warrantyUnit = item.warrantyUnit
        )

        val now = System.currentTimeMillis()
        val itemToSave = item.copy(
            expiryDate = computedExpiry,
            updatedAt = now,
            createdAt = if (item.id == 0L) now else item.createdAt
        )

        val itemId = if (itemToSave.id == 0L) {
            itemDao.insertItem(itemToSave)
        } else {
            itemDao.updateItem(itemToSave)
            itemToSave.id
        }

        // 1. Update CustomFields
        customFieldDao.deleteCustomFieldsForItem(itemId)
        val validFields = customFields
            .filter { it.name.isNotBlank() }
            .mapIndexed { index, field ->
                field.copy(
                    id = 0L,
                    itemId = itemId,
                    name = field.name.trim(),
                    value = field.value.trim(),
                    sortOrder = index
                )
            }
        if (validFields.isNotEmpty()) {
            customFieldDao.insertCustomFields(validFields)
        }

        // 2. Update Attachments
        val categoryName = categoryDao.getCategoryById(itemToSave.categoryId)?.name ?: "UNCATEGORIZED"
        val existingAttachments = attachmentDao.getAttachmentsForItemOnce(itemId)
        val retainedExistingIds = pendingAttachments.mapNotNull { it.existingAttachmentId }.toSet()

        // Delete removed attachments from storage and DB
        existingAttachments.filter { it.id !in retainedExistingIds }.forEach { removed ->
            FileStorageHelper.deleteAttachmentFile(removed.localPath)
            attachmentDao.deleteAttachmentById(removed.id)
        }

        // Save new or retained attachments
        for (pending in pendingAttachments) {
            val savedAttachment = FileStorageHelper.saveAttachmentToStorage(
                context = context,
                settingsManager = settingsManager,
                pending = pending,
                categoryName = categoryName,
                itemId = itemId
            )
            if (savedAttachment != null) {
                attachmentDao.insertAttachment(savedAttachment)
            }
        }

        // 3. Reschedule Reminders
        rescheduleReminders(itemId = itemId, expiryDate = computedExpiry)

        return itemId
    }

    private suspend fun rescheduleReminders(itemId: Long, expiryDate: Long?) {
        reminderDao.deleteRemindersForItem(itemId)
        if (expiryDate == null || expiryDate <= 0L) return

        val reminderPairs = DateUtils.calculateReminderDates(expiryDate)
        val now = System.currentTimeMillis()
        val reminders = reminderPairs.map { (daysBefore, reminderDate) ->
            Reminder(
                itemId = itemId,
                reminderDate = reminderDate,
                daysBefore = daysBefore,
                isSent = reminderDate < now
            )
        }
        reminderDao.insertReminders(reminders)
    }

    suspend fun deleteAttachment(attachment: Attachment) {
        FileStorageHelper.deleteAttachmentFile(attachment.localPath)
        attachmentDao.deleteAttachmentById(attachment.id)
    }

    suspend fun deleteItem(context: Context, itemId: Long) {
        val details = itemDao.getItemWithDetailsByIdOnce(itemId)
        if (details != null) {
            val categoryName = details.category?.name ?: "UNCATEGORIZED"
            FileStorageHelper.deleteItemAttachmentsFolder(
                context = context,
                categoryName = categoryName,
                itemId = itemId,
                attachments = details.attachments
            )
        }
        reminderDao.deleteRemindersForItem(itemId)
        itemDao.deleteItemById(itemId)
    }
}
