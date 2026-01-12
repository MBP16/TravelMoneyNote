package io.github.mbp16.travelmoneynote

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mbp16.travelmoneynote.data.*
import io.github.mbp16.travelmoneynote.ui.screens.TransactionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class PersonWithBalance(
    val person: Person,
    val totalCash: Double,
    val cashSpent: Double,
    val cardSpent: Double
) {
    val remainingCash: Double get() = totalCash - cashSpent
}

data class ExpenseWithPayments(
    val expense: Expense,
    val payments: List<PaymentWithPerson>
)

data class PaymentWithPerson(
    val payment: Payment,
    val personName: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val travelDao = database.travelDao()
    private val personDao = database.personDao()
    private val cashEntryDao = database.cashEntryDao()
    private val expenseDao = database.expenseDao()
    private val paymentDao = database.paymentDao()
    
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    
    private val _selectedTravelId = MutableStateFlow(prefs.getLong("selectedTravelId", -1L))
    private val _standardCurrency = MutableStateFlow(prefs.getString("standardCurrency", "KRW") ?: "KRW")
    val selectedTravelId: StateFlow<Long> = _selectedTravelId.asStateFlow()
    val standardCurrency: StateFlow<String> = _standardCurrency.asStateFlow()
    
    val travels: StateFlow<List<Travel>> = travelDao.getAllTravels()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val currentCurrency: StateFlow<String> = combine(travels, _selectedTravelId) { travelList, travelId ->
        travelList.find { it.id == travelId }?.currency ?: "KRW"
    }.stateIn(viewModelScope, SharingStarted.Lazily, "KRW")
    
    val persons: StateFlow<List<Person>> = _selectedTravelId.flatMapLatest { travelId ->
        if (travelId > 0) {
            personDao.getPersonsByTravel(travelId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val expenses: StateFlow<List<Expense>> = _selectedTravelId.flatMapLatest { travelId ->
        if (travelId > 0) {
            expenseDao.getExpensesByTravel(travelId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val cashEntries: StateFlow<List<CashEntry>> = cashEntryDao.getAllCashEntries()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    fun getPersonsWithBalance(): Flow<List<PersonWithBalance>> {
        return persons.flatMapLatest { personList ->
            if (personList.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    personList.map { person ->
                        combine(
                            cashEntryDao.getTotalCashForPerson(person.id),
                            paymentDao.getTotalCashSpentByPerson(person.id),
                            paymentDao.getTotalCardSpentByPerson(person.id)
                        ) { totalCash, cashSpent, cardSpent ->
                            PersonWithBalance(person, totalCash, cashSpent, cardSpent)
                        }
                    }
                ) { it.toList() }
            }
        }
    }
    
    fun getExpenseWithPayments(expenseId: Long): Flow<ExpenseWithPayments?> {
        return combine(
            expenseDao.getAllExpenses().map { it.find { e -> e.id == expenseId } },
            paymentDao.getPaymentsForExpense(expenseId),
            persons
        ) { expense, payments, personList ->
            expense?.let {
                ExpenseWithPayments(
                    expense = it,
                    payments = payments.map { payment ->
                        PaymentWithPerson(
                            payment = payment,
                            personName = personList.find { p -> p.id == payment.personId }?.name ?: "Unknown"
                        )
                    }
                )
            }
        }
    }
    
    fun addPerson(name: String) {
        val travelId = _selectedTravelId.value
        if (travelId <= 0) return
        viewModelScope.launch {
            personDao.insert(Person(travelId = travelId, name = name))
        }
    }

    fun updatePerson(person: Person) {
        viewModelScope.launch {
            personDao.update(person)
        }
    }
    
    fun deletePerson(person: Person) {
        viewModelScope.launch {
            personDao.delete(person)
        }
    }
    
    fun addCashEntry(personId: Long, amount: Double, description: String) {
        viewModelScope.launch {
            cashEntryDao.insert(
                CashEntry(
                    personId = personId,
                    amount = amount,
                    description = description
                )
            )
        }
    }

    fun updateCashEntry(cashEntry: CashEntry) {
        viewModelScope.launch {
            cashEntryDao.update(cashEntry)
        }
    }

    fun deleteCashEntry(cashEntry: CashEntry) {
        viewModelScope.launch {
            cashEntryDao.delete(cashEntry)
        }
    }
    
    fun addExpenseWithPayments(
        title: String,
        totalAmount: Double,
        description: String,
        photoUri: String?,
        payments: List<Triple<Long, Double, PaymentMethod>>
    ) {
        val travelId = _selectedTravelId.value
        if (travelId <= 0) return
        viewModelScope.launch {
            val expenseId = expenseDao.insert(
                Expense(
                    travelId = travelId,
                    title = title,
                    totalAmount = totalAmount,
                    description = description,
                    photoUri = photoUri
                )
            )
            val paymentEntities = payments.map { (personId, amount, method) ->
                Payment(
                    expenseId = expenseId,
                    personId = personId,
                    amount = amount,
                    method = method
                )
            }
            paymentDao.insertAll(paymentEntities)
        }
    }

    fun updateExpenseWithPayments(
        expenseId: Long,
        title: String,
        totalAmount: Double,
        description: String,
        photoUri: String?,
        payments: List<Triple<Long, Double, PaymentMethod>>
    ) {
        val travelId = _selectedTravelId.value
        if (travelId <= 0) return
        viewModelScope.launch {
            expenseDao.update(
                Expense(
                    id = expenseId,
                    travelId = travelId,
                    title = title,
                    totalAmount = totalAmount,
                    description = description,
                    photoUri = photoUri
                )
            )
            paymentDao.deletePaymentsForExpense(expenseId)
            val paymentEntities = payments.map { (personId, amount, method) ->
                Payment(
                    expenseId = expenseId,
                    personId = personId,
                    amount = amount,
                    method = method
                )
            }
            paymentDao.insertAll(paymentEntities)
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            expenseDao.delete(expense)
        }
    }
    
    fun addTravel(name: String, startDate: Long, endDate: Long, currency: String) {
        viewModelScope.launch {
            val travelId = travelDao.insert(Travel(name = name, startDate = startDate, endDate = endDate, currency = currency))
            selectTravel(travelId)
        }
    }
    
    fun updateTravel(travel: Travel) {
        viewModelScope.launch {
            travelDao.update(travel)
        }
    }
    
    fun deleteTravel(travel: Travel) {
        viewModelScope.launch {
            travelDao.delete(travel)
            if (_selectedTravelId.value == travel.id) {
                selectTravel(-1L)
            }
        }
    }
    
    fun selectTravel(travelId: Long) {
        prefs.edit().putLong("selectedTravelId", travelId).apply()
        _selectedTravelId.value = travelId
    }

    fun setStandardCurrency(currency: String) {
        prefs.edit().putString("standardCurrency", currency).apply()
        _standardCurrency.value = currency
    }
    
    fun resetDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            database.clearAllTables()
            selectTravel(-1L)
        }
    }
    
    fun getTransactionsForPerson(personId: Long): Flow<List<TransactionItem>> {
        return combine(
            cashEntryDao.getCashEntriesForPerson(personId),
            paymentDao.getPaymentsForPerson(personId),
            expenseDao.getAllExpenses()
        ) { cashEntries, payments, expenses ->
            val cashTransactions = cashEntries.map { entry ->
                TransactionItem(
                    id = entry.id,
                    amount = entry.amount,
                    isPositive = true,
                    description = entry.description,
                    type = "현금 추가",
                    createdAt = entry.createdAt
                )
            }
            val paymentTransactions = payments.mapNotNull { payment ->
                val expense = expenses.find { it.id == payment.expenseId }
                expense?.let {
                    TransactionItem(
                        id = payment.id,
                        amount = payment.amount,
                        isPositive = false,
                        description = expense.title,
                        type = if (payment.method == PaymentMethod.CASH) "현금 결제" else "카드 결제",
                        createdAt = expense.createdAt
                    )
                }
            }
            (cashTransactions + paymentTransactions).sortedByDescending { it.createdAt }
        }
    }
    
    private val json = Json { prettyPrint = true }
    
    suspend fun exportDataToJson(): String = withContext(Dispatchers.IO) {
        val allTravels = travelDao.getAllTravelsOnce()
        val travelExports = allTravels.map { travel ->
            val persons = personDao.getPersonsByTravelOnce(travel.id)
            val personExports = persons.map { person ->
                val cashEntries = cashEntryDao.getCashEntriesForPersonOnce(person.id)
                PersonExport(
                    id = person.id,
                    name = person.name,
                    cashEntries = cashEntries.map { entry ->
                        CashEntryExport(
                            id = entry.id,
                            amount = entry.amount,
                            description = entry.description,
                            createdAt = entry.createdAt
                        )
                    }
                )
            }
            val expenses = expenseDao.getExpensesByTravelOnce(travel.id)
            val expenseExports = expenses.map { expense ->
                val payments = paymentDao.getPaymentsForExpenseOnce(expense.id)
                ExpenseExport(
                    id = expense.id,
                    title = expense.title,
                    totalAmount = expense.totalAmount,
                    description = expense.description,
                    photoUri = expense.photoUri,
                    createdAt = expense.createdAt,
                    payments = payments.map { payment ->
                        PaymentExport(
                            id = payment.id,
                            personId = payment.personId,
                            amount = payment.amount,
                            method = payment.method.name
                        )
                    }
                )
            }
            TravelExport(
                id = travel.id,
                name = travel.name,
                startDate = travel.startDate,
                endDate = travel.endDate,
                currency = travel.currency,
                persons = personExports,
                expenses = expenseExports
            )
        }
        val exportData = ExportData(
            travels = travelExports,
            standardCurrency = _standardCurrency.value
        )
        json.encodeToString(exportData)
    }
    
    fun exportToFile(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonData = exportDataToJson()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(jsonData.toByteArray())
                    }
                }
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }
    
    fun importFromFile(uri: Uri, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonData = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: throw Exception("파일을 읽을 수 없습니다")
                }
                val exportData = json.decodeFromString<ExportData>(jsonData)
                
                withContext(Dispatchers.IO) {
                    database.clearAllTables()
                    
                    val travelIdMap = mutableMapOf<Long, Long>()
                    val personIdMap = mutableMapOf<Long, Long>()
                    
                    for (travel in exportData.travels) {
                        val newTravelId = travelDao.insert(
                            Travel(
                                name = travel.name,
                                startDate = travel.startDate,
                                endDate = travel.endDate,
                                currency = travel.currency
                            )
                        )
                        travelIdMap[travel.id] = newTravelId
                        
                        for (person in travel.persons) {
                            val newPersonId = personDao.insert(
                                Person(
                                    travelId = newTravelId,
                                    name = person.name
                                )
                            )
                            personIdMap[person.id] = newPersonId
                            
                            for (cashEntry in person.cashEntries) {
                                cashEntryDao.insert(
                                    CashEntry(
                                        personId = newPersonId,
                                        amount = cashEntry.amount,
                                        description = cashEntry.description,
                                        createdAt = cashEntry.createdAt
                                    )
                                )
                            }
                        }
                        
                        for (expense in travel.expenses) {
                            val newExpenseId = expenseDao.insert(
                                Expense(
                                    travelId = newTravelId,
                                    title = expense.title,
                                    totalAmount = expense.totalAmount,
                                    description = expense.description,
                                    photoUri = expense.photoUri,
                                    createdAt = expense.createdAt
                                )
                            )
                            
                            for (payment in expense.payments) {
                                val newPersonId = personIdMap[payment.personId] ?: continue
                                paymentDao.insert(
                                    Payment(
                                        expenseId = newExpenseId,
                                        personId = newPersonId,
                                        amount = payment.amount,
                                        method = PaymentMethod.valueOf(payment.method)
                                    )
                                )
                            }
                        }
                    }
                    
                    prefs.edit().putString("standardCurrency", exportData.standardCurrency).apply()
                    _standardCurrency.value = exportData.standardCurrency
                }
                
                selectTravel(-1L)
                onComplete(true, "데이터를 성공적으로 불러왔습니다")
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false, "불러오기 실패: ${e.message}")
            }
        }
    }
}
