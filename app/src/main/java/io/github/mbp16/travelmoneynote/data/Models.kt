package io.github.mbp16.travelmoneynote.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "travels")
data class Travel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startDate: Long,
    val endDate: Long,
    val currency: String = "KRW"
)

@Entity(
    tableName = "persons",
    foreignKeys = [
        ForeignKey(
            entity = Travel::class,
            parentColumns = ["id"],
            childColumns = ["travelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("travelId")]
)
data class Person(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val travelId: Long,
    val name: String
)

@Entity(
    tableName = "cash_entries",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("personId")]
)
data class CashEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personId: Long,
    val amount: Double,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = Travel::class,
            parentColumns = ["id"],
            childColumns = ["travelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("travelId")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val travelId: Long,
    val title: String = "",
    val totalAmount: Double,
    val description: String = "",
    val photoUri: String? = null,  // Deprecated: kept for backward compatibility
    val photoUris: String? = null,  // New: comma-separated list of photo URIs
    val createdAt: Long = System.currentTimeMillis()
)

enum class PaymentMethod {
    CASH, CARD
}

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = Expense::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("expenseId"), Index("personId")]
)
data class Payment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val expenseId: Long,
    val personId: Long,
    val amount: Double,
    val method: PaymentMethod
)

@Entity(
    tableName = "expense_users",
    foreignKeys = [
        ForeignKey(
            entity = Expense::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("expenseId"), Index("personId")]
)
data class ExpenseUser(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val expenseId: Long,
    val personId: Long,
    val amount: Double,
    val description: String = ""
)
