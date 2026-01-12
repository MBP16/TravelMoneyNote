package io.github.mbp16.travelmoneynote.data

import kotlinx.serialization.Serializable

@Serializable
data class ExportData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val travels: List<TravelExport>,
    val standardCurrency: String
)

@Serializable
data class TravelExport(
    val id: Long,
    val name: String,
    val startDate: Long,
    val endDate: Long,
    val currency: String,
    val persons: List<PersonExport>,
    val expenses: List<ExpenseExport>
)

@Serializable
data class PersonExport(
    val id: Long,
    val name: String,
    val cashEntries: List<CashEntryExport>
)

@Serializable
data class CashEntryExport(
    val id: Long,
    val amount: Double,
    val description: String,
    val createdAt: Long
)

@Serializable
data class ExpenseExport(
    val id: Long,
    val totalAmount: Double,
    val description: String,
    val photoUri: String?,
    val createdAt: Long,
    val payments: List<PaymentExport>
)

@Serializable
data class PaymentExport(
    val id: Long,
    val personId: Long,
    val amount: Double,
    val method: String
)
