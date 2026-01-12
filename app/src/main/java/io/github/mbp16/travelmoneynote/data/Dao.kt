package io.github.mbp16.travelmoneynote.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY name")
    fun getAllPersons(): Flow<List<Person>>
    
    @Insert
    suspend fun insert(person: Person): Long
    
    @Update
    suspend fun update(person: Person)
    
    @Delete
    suspend fun delete(person: Person)
}

@Dao
interface CashEntryDao {
    @Query("SELECT * FROM cash_entries ORDER BY createdAt DESC")
    fun getAllCashEntries(): Flow<List<CashEntry>>
    
    @Query("SELECT * FROM cash_entries WHERE personId = :personId ORDER BY createdAt DESC")
    fun getCashEntriesForPerson(personId: Long): Flow<List<CashEntry>>
    
    @Query("SELECT COALESCE(SUM(amount), 0) FROM cash_entries WHERE personId = :personId")
    fun getTotalCashForPerson(personId: Long): Flow<Double>
    
    @Insert
    suspend fun insert(cashEntry: CashEntry): Long
    
    @Delete
    suspend fun delete(cashEntry: CashEntry)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    fun getAllExpenses(): Flow<List<Expense>>
    
    @Insert
    suspend fun insert(expense: Expense): Long
    
    @Delete
    suspend fun delete(expense: Expense)
    
    @Update
    suspend fun update(expense: Expense)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE expenseId = :expenseId")
    fun getPaymentsForExpense(expenseId: Long): Flow<List<Payment>>
    
    @Query("SELECT * FROM payments WHERE personId = :personId")
    fun getPaymentsForPerson(personId: Long): Flow<List<Payment>>
    
    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE personId = :personId AND method = 'CASH'")
    fun getTotalCashSpentByPerson(personId: Long): Flow<Double>
    
    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE personId = :personId AND method = 'CARD'")
    fun getTotalCardSpentByPerson(personId: Long): Flow<Double>
    
    @Insert
    suspend fun insert(payment: Payment): Long
    
    @Insert
    suspend fun insertAll(payments: List<Payment>)
    
    @Delete
    suspend fun delete(payment: Payment)
    
    @Query("DELETE FROM payments WHERE expenseId = :expenseId")
    suspend fun deletePaymentsForExpense(expenseId: Long)
}
