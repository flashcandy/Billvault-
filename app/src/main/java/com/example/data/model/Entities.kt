package com.example.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

object WarrantyUnit {
    const val DAYS = "DAYS"
    const val MONTHS = "MONTHS"
    const val YEARS = "YEARS"

    val ALL = listOf(DAYS, MONTHS, YEARS)
}

object CustomFieldType {
    const val TEXT = "TEXT"
    const val NUMBER = "NUMBER"
    const val DATE = "DATE"
    const val YES_NO = "YES/NO"

    val ALL = listOf(TEXT, NUMBER, DATE, YES_NO)
}

object CategoryMarker {
    const val DOT_RED = "DOT_RED"
    const val DOT_WHITE = "DOT_WHITE"
    const val DOT_GRAY = "DOT_GRAY"
    const val SQUARE_RED = "SQUARE_RED"
    const val SQUARE_WHITE = "SQUARE_WHITE"

    val ALL = listOf(DOT_RED, DOT_WHITE, DOT_GRAY, SQUARE_RED, SQUARE_WHITE)
}

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isDefault: Boolean = false,
    val colorMarker: String = CategoryMarker.DOT_RED,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["categoryId"])]
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val categoryId: Long,
    val sellerName: String = "",
    val invoiceNumber: String = "",
    val orderId: String = "",
    val purchaseDate: Long, // Epoch millis
    val amount: Double? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "warranty_parts",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["itemId"])]
)
data class WarrantyPart(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val label: String, // custom text, max 30 characters
    val durationValue: Int,
    val durationUnit: String = WarrantyUnit.YEARS, // DAYS, MONTHS, or YEARS
    val startDate: Long, // Epoch millis
    val expiryDate: Long, // Epoch millis, calculated from startDate + durationValue and unit
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "custom_fields",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["itemId"])]
)
data class CustomField(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val name: String,
    val type: String = CustomFieldType.TEXT, // TEXT, NUMBER, DATE, YES/NO
    val value: String = "",
    val sortOrder: Int = 0
)

@Entity(
    tableName = "custom_field_templates",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId", "name"], unique = true)]
)
data class CustomFieldTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val type: String = CustomFieldType.TEXT
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["itemId"])]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val fileName: String,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WarrantyPart::class,
            parentColumns = ["id"],
            childColumns = ["partId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["itemId"]),
        Index(value = ["partId"])
    ]
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val partId: Long? = null,
    val reminderDate: Long, // Epoch timestamp in millis
    val daysBefore: Int, // 30, 7, 1
    val isSent: Boolean = false
)

data class ItemWithDetails(
    @Embedded val item: Item,
    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: Category?,
    @Relation(
        parentColumn = "id",
        entityColumn = "itemId"
    )
    val customFields: List<CustomField>,
    @Relation(
        parentColumn = "id",
        entityColumn = "itemId"
    )
    val attachments: List<Attachment>,
    @Relation(
        parentColumn = "id",
        entityColumn = "itemId"
    )
    val warrantyParts: List<WarrantyPart>,
    @Relation(
        parentColumn = "id",
        entityColumn = "itemId"
    )
    val reminders: List<Reminder>
) {
    val effectiveExpiryDate: Long?
        get() = warrantyParts.mapNotNull { it.expiryDate.takeIf { exp -> exp > 0L } }.minOrNull()

    val primaryWarrantyPart: WarrantyPart?
        get() = warrantyParts.minByOrNull { it.sortOrder }
}
