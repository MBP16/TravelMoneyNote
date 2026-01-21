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
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
                ExpenseExport(
                    id = expense.id,
                    title = expense.title,
                    totalAmount = expense.totalAmount,
                    description = expense.description,
                    photoUri = null,  // Deprecated field
                    photoUris = expense.photoUris ?: expense.photoUri,  // Use new field, fallback to old
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
                val context = getApplication<Application>()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        ZipOutputStream(outputStream).use { zipOut ->
                            // Collect all photo URIs that need to be exported
                            val photoMap = mutableMapOf<String, String>() // original URI -> relative path in ZIP
                            var photoCounter = 0
                            
                            // Get all expenses and process their photos
                            val allTravels = travelDao.getAllTravelsOnce()
                            for (travel in allTravels) {
                                val expenses = expenseDao.getExpensesByTravelOnce(travel.id)
                                for (expense in expenses) {
                                    val photoUrisStr = expense.photoUris ?: expense.photoUri
                                    if (!photoUrisStr.isNullOrBlank()) {
                                        // Handle comma-separated URIs
                                        val uris = photoUrisStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        for (uriStr in uris) {
                                            if (!photoMap.containsKey(uriStr)) {
                                                try {
                                                    val uri = Uri.parse(uriStr)
                                                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                                        // Determine file extension
                                                        val extension = when {
                                                            uriStr.endsWith(".jpg", ignoreCase = true) -> "jpg"
                                                            uriStr.endsWith(".jpeg", ignoreCase = true) -> "jpeg"
                                                            uriStr.endsWith(".png", ignoreCase = true) -> "png"
                                                            else -> "jpg" // default
                                                        }
                                                        val relativePath = "photos/expense_${expense.id}_${photoCounter}.$extension"
                                                        photoMap[uriStr] = relativePath
                                                        photoCounter++
                                                        
                                                        // Add photo to ZIP
                                                        val zipEntry = ZipEntry(relativePath)
                                                        zipOut.putNextEntry(zipEntry)
                                                        inputStream.copyTo(zipOut)
                                                        zipOut.closeEntry()
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    // Skip this photo if it can't be read
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // Now generate JSON data with relative paths
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
                                    
                                    // Convert absolute URIs to relative paths
                                    val photoUrisStr = expense.photoUris ?: expense.photoUri
                                    val relativePhotoUris = if (!photoUrisStr.isNullOrBlank()) {
                                        val uris = photoUrisStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        uris.mapNotNull { photoMap[it] }.joinToString(",")
                                    } else {
                                        null
                                    }
                                    
                                    ExpenseExport(
                                        id = expense.id,
                                        title = expense.title,
                                        totalAmount = expense.totalAmount,
                                        description = expense.description,
                                        photoUri = null,
                                        photoUris = relativePhotoUris,
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
                            val jsonData = json.encodeToString(exportData)
                            
                            // Add data.json to ZIP
                            val jsonEntry = ZipEntry("data.json")
                            zipOut.putNextEntry(jsonEntry)
                            zipOut.write(jsonData.toByteArray())
                            zipOut.closeEntry()
                        }
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
                val context = getApplication<Application>()
                
                // Detect file type by trying to read as ZIP first
                val isZipFile = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val bytes = ByteArray(4)
                            val read = inputStream.read(bytes)
                            // ZIP file signature: 0x50 0x4B 0x03 0x04 (PK..)
                            read == 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
                        } ?: false
                    } catch (e: Exception) {
                        false
                    }
                }
                
                if (isZipFile) {
                    // Import from ZIP file
                    importFromZip(uri, onComplete)
                } else {
                    // Import from legacy JSON file
                    importFromJson(uri, onComplete)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false, "불러오기 실패: ${e.message}")
            }
        }
    }
    
    private suspend fun importFromJson(uri: Uri, onComplete: (Boolean, String) -> Unit) {
        try {
            val context = getApplication<Application>()
            val jsonData = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
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
                                photoUri = null,
                                photoUris = expense.photoUris ?: expense.photoUri,
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
    
    private suspend fun importFromZip(uri: Uri, onComplete: (Boolean, String) -> Unit) {
        try {
            val context = getApplication<Application>()
            
            withContext(Dispatchers.IO) {
                // Create a temporary directory for extracting photos
                val photosDir = File(context.filesDir, "imported_photos_temp")
                photosDir.mkdirs()
                
                var jsonData: String? = null
                val photoFiles = mutableMapOf<String, File>() // relative path -> extracted file
                
                // Extract ZIP contents
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zipIn ->
                        var entry: ZipEntry? = zipIn.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                if (entry.name == "data.json") {
                                    // Read JSON data
                                    jsonData = zipIn.bufferedReader().readText()
                                } else if (entry.name.startsWith("photos/")) {
                                    // Extract photo file
                                    val fileName = entry.name.substringAfterLast("/")
                                    val tempFile = File(photosDir, fileName)
                                    tempFile.outputStream().use { output ->
                                        zipIn.copyTo(output)
                                    }
                                    photoFiles[entry.name] = tempFile
                                }
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }
                }
                
                if (jsonData == null) {
                    throw Exception("ZIP 파일에 data.json이 없습니다")
                }
                
                val exportData = json.decodeFromString<ExportData>(jsonData!!)
                
                database.clearAllTables()
                
                val travelIdMap = mutableMapOf<Long, Long>()
                val personIdMap = mutableMapOf<Long, Long>()
                
                // Create permanent photos directory
                val permanentPhotosDir = File(context.filesDir, "expense_photos")
                permanentPhotosDir.mkdirs()
                
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
                        // Process photo URIs - convert relative paths to absolute URIs
                        val photoUrisStr = expense.photoUris ?: expense.photoUri
                        val newPhotoUris = if (!photoUrisStr.isNullOrBlank()) {
                            val relativePaths = photoUrisStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val absoluteUris = relativePaths.mapNotNull { relativePath ->
                                photoFiles[relativePath]?.let { tempFile ->
                                    // Copy to permanent storage
                                    val timestamp = System.currentTimeMillis()
                                    val extension = tempFile.extension
                                    val permanentFile = File(permanentPhotosDir, "photo_${timestamp}_${tempFile.name}")
                                    tempFile.copyTo(permanentFile, overwrite = true)
                                    Uri.fromFile(permanentFile).toString()
                                }
                            }
                            absoluteUris.joinToString(",")
                        } else {
                            null
                        }
                        
                        val newExpenseId = expenseDao.insert(
                            Expense(
                                travelId = newTravelId,
                                title = expense.title,
                                totalAmount = expense.totalAmount,
                                description = expense.description,
                                photoUri = null,
                                photoUris = newPhotoUris,
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
                
                // Clean up temporary directory
                photosDir.deleteRecursively()
                
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
