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
    val payments: List<PaymentWithPerson>,
    val expenseUsers: List<ExpenseUserWithPerson> = emptyList()
)

data class PaymentWithPerson(
    val payment: Payment,
    val personName: String
)

data class ExpenseUserWithPerson(
    val expenseUser: ExpenseUser,
    val personName: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val travelDao = database.travelDao()
    private val personDao = database.personDao()
    private val cashEntryDao = database.cashEntryDao()
    private val expenseDao = database.expenseDao()
    private val paymentDao = database.paymentDao()
    private val expenseUserDao = database.expenseUserDao()
    
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    
    private val _selectedTravelId = MutableStateFlow(prefs.getLong("selectedTravelId", -1L))
    private val _standardCurrency = MutableStateFlow(prefs.getString("standardCurrency", "KRW") ?: "KRW")
    val selectedTravelId: StateFlow<Long> = _selectedTravelId.asStateFlow()
    val standardCurrency: StateFlow<String> = _standardCurrency.asStateFlow()
    
    // Exchange rate service
    private val exchangeRateService = ExchangeRateService()
    private val _exchangeRates = MutableStateFlow<ExchangeRates?>(null)
    val exchangeRates: StateFlow<ExchangeRates?> = _exchangeRates.asStateFlow()
    
    val travels: StateFlow<List<Travel>> = travelDao.getAllTravels()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val currentCurrency: StateFlow<String> = combine(travels, _selectedTravelId) { travelList, travelId ->
        travelList.find { it.id == travelId }?.currency ?: "KRW"
    }.stateIn(viewModelScope, SharingStarted.Lazily, "KRW")
    
    init {
        // Fetch exchange rates when currencies change
        viewModelScope.launch {
            combine(currentCurrency, _standardCurrency) { current, standard ->
                Pair(current, standard)
            }.collect { (currentCurr, standardCurr) ->
                if (currentCurr != standardCurr) {
                    refreshExchangeRates(standardCurr)
                }
            }
        }
    }
    
    fun refreshExchangeRates(baseCurrency: String = _standardCurrency.value) {
        viewModelScope.launch {
            _exchangeRates.value = exchangeRateService.getExchangeRates(baseCurrency)
        }
    }
    
    fun convertToStandardCurrency(amount: Double, fromCurrency: String): Double? {
        val standardCurr = _standardCurrency.value
        if (fromCurrency == standardCurr) return amount
        return exchangeRateService.convertAmount(amount, fromCurrency, standardCurr, _exchangeRates.value)
    }
    
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
            expenseUserDao.getExpenseUsersForExpense(expenseId),
            persons
        ) { expense, payments, expenseUsers, personList ->
            expense?.let {
                ExpenseWithPayments(
                    expense = it,
                    payments = payments.map { payment ->
                        PaymentWithPerson(
                            payment = payment,
                            personName = personList.find { p -> p.id == payment.personId }?.name ?: "Unknown"
                        )
                    },
                    expenseUsers = expenseUsers.map { eu ->
                        ExpenseUserWithPerson(
                            expenseUser = eu,
                            personName = personList.find { p -> p.id == eu.personId }?.name ?: "Unknown"
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
        payments: List<Triple<Long, Double, PaymentMethod>>,
        expenseUsers: List<Triple<Long, Double, String>> = emptyList()
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
                    photoUri = null,  // Deprecated field
                    photoUris = photoUri  // New field for multiple URIs
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
            val expenseUserEntities = expenseUsers.map { (personId, amount, desc) ->
                ExpenseUser(
                    expenseId = expenseId,
                    personId = personId,
                    amount = amount,
                    description = desc
                )
            }
            expenseUserDao.insertAll(expenseUserEntities)
        }
    }

    fun updateExpenseWithPayments(
        expenseId: Long,
        title: String,
        totalAmount: Double,
        description: String,
        photoUri: String?,
        payments: List<Triple<Long, Double, PaymentMethod>>,
        expenseUsers: List<Triple<Long, Double, String>> = emptyList()
    ) {
        val travelId = _selectedTravelId.value
        if (travelId <= 0) return
        viewModelScope.launch {
            // Get the original expense to preserve createdAt
            val originalExpense = expenseDao.getExpenseById(expenseId)
            val createdAt = originalExpense?.createdAt ?: System.currentTimeMillis()
            
            expenseDao.update(
                Expense(
                    id = expenseId,
                    travelId = travelId,
                    title = title,
                    totalAmount = totalAmount,
                    description = description,
                    photoUri = null,  // Deprecated field
                    photoUris = photoUri,  // New field for multiple URIs
                    createdAt = createdAt
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
            expenseUserDao.deleteExpenseUsersForExpense(expenseId)
            val expenseUserEntities = expenseUsers.map { (personId, amount, desc) ->
                ExpenseUser(
                    expenseId = expenseId,
                    personId = personId,
                    amount = amount,
                    description = desc
                )
            }
            expenseUserDao.insertAll(expenseUserEntities)
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

    data class Settlement(
        val fromPersonId: Long,
        val fromPersonName: String,
        val toPersonId: Long,
        val toPersonName: String,
        val amount: Double
    )

    fun getSettlementsForTravel(): Flow<List<Settlement>> {
        return combine(
            persons,
            expenses,
            paymentDao.getAllPayments(),
            expenseUserDao.getAllExpenseUsers()
        ) { personList, expenseList, allPayments, allExpenseUsers ->
            val expenseIds = expenseList.map { it.id }.toSet()
            val payments = allPayments.filter { it.expenseId in expenseIds }
            val expenseUsers = allExpenseUsers.filter { it.expenseId in expenseIds }

            val personMap = personList.associateBy { it.id }
            val balances = mutableMapOf<Long, Double>()
            personList.forEach { balances[it.id] = 0.0 }

            // 결제한 사람은 + (다른 사람들이 갚아야 함)
            payments.forEach { payment ->
                balances[payment.personId] = balances.getOrDefault(payment.personId, 0.0) + payment.amount
            }

            // 사용한 사람은 - (갚아야 함)
            expenseUsers.forEach { eu ->
                balances[eu.personId] = balances.getOrDefault(eu.personId, 0.0) - eu.amount
            }

            val creditors = balances.filter { it.value > 0.001 }.toMutableMap()
            val debtors = balances.filter { it.value < -0.001 }.mapValues { -it.value }.toMutableMap()

            val settlements = mutableListOf<Settlement>()
            for ((creditorId, creditAmount) in creditors.toList()) {
                var remaining = creditAmount
                for ((debtorId, debtAmount) in debtors.toList()) {
                    if (remaining <= 0.001) break
                    val amount = minOf(remaining, debtAmount)
                    if (amount > 0.001) {
                        settlements.add(
                            Settlement(
                                fromPersonId = debtorId,
                                fromPersonName = personMap[debtorId]?.name ?: "Unknown",
                                toPersonId = creditorId,
                                toPersonName = personMap[creditorId]?.name ?: "Unknown",
                                amount = amount
                            )
                        )
                        remaining -= amount
                        debtors[debtorId] = debtAmount - amount
                    }
                }
            }
            settlements
        }
    }
    
    private val json = Json { prettyPrint = true }
    
    suspend fun exportDataToJson(): String = withContext(Dispatchers.IO) {
        val allTravels = travelDao.getAllTravelsOnce()
        val context = getApplication<Application>()
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
                val expenseUsers = expenseUserDao.getExpenseUsersForExpenseOnce(expense.id)
                
                // Read photo files and encode to base64
                val photoUrisString = expense.photoUris ?: expense.photoUri
                val photoDataList = if (!photoUrisString.isNullOrEmpty()) {
                    photoUrisString.split(",").mapNotNull { uriString ->
                        try {
                            val uri = android.net.Uri.parse(uriString.trim())
                            val file = java.io.File(uri.path ?: return@mapNotNull null)
                            if (file.exists() && file.canRead()) {
                                val bytes = file.readBytes()
                                android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                            } else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                } else null
                
                ExpenseExport(
                    id = expense.id,
                    title = expense.title,
                    totalAmount = expense.totalAmount,
                    description = expense.description,
                    photoUri = null,  // Deprecated field
                    photoUris = expense.photoUris ?: expense.photoUri,  // Use new field, fallback to old
                    photoData = photoDataList,  // Base64 encoded photos
                    createdAt = expense.createdAt,
                    payments = payments.map { payment ->
                        PaymentExport(
                            id = payment.id,
                            personId = payment.personId,
                            amount = payment.amount,
                            method = payment.method.name
                        )
                    },
                    expenseUsers = expenseUsers.map { eu ->
                        ExpenseUserExport(
                            id = eu.id,
                            personId = eu.personId,
                            amount = eu.amount,
                            description = eu.description
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
                            // Restore photos from base64 data if available
                            val photoUrisString = if (!expense.photoData.isNullOrEmpty()) {
                                val context = getApplication<Application>()
                                val restoredUris = expense.photoData.mapIndexedNotNull { index, base64Data ->
                                    try {
                                        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                                        val imageFile = java.io.File(context.filesDir, "IMPORTED_${timeStamp}_$index.jpg")
                                        imageFile.writeBytes(bytes)
                                        imageFile.absolutePath
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                if (restoredUris.isNotEmpty()) {
                                    restoredUris.joinToString(",")
                                } else {
                                    expense.photoUris ?: expense.photoUri
                                }
                            } else {
                                expense.photoUris ?: expense.photoUri
                            }
                            
                            val newExpenseId = expenseDao.insert(
                                Expense(
                                    travelId = newTravelId,
                                    title = expense.title,
                                    totalAmount = expense.totalAmount,
                                    description = expense.description,
                                    photoUri = null,  // Deprecated field
                                    photoUris = photoUrisString,  // Use restored photo URIs
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

                            for (expenseUser in expense.expenseUsers) {
                                val newPersonId = personIdMap[expenseUser.personId] ?: continue
                                expenseUserDao.insert(
                                    ExpenseUser(
                                        expenseId = newExpenseId,
                                        personId = newPersonId,
                                        amount = expenseUser.amount,
                                        description = expenseUser.description
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
