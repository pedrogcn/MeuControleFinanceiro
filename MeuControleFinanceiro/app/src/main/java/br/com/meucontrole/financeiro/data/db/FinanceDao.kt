package br.com.meucontrole.financeiro.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {

    // ---------------- Observação (atualiza a interface automaticamente) ----------------
    @Query("SELECT * FROM debts ORDER BY createdAt DESC") fun observeDebts(): Flow<List<DebtEntity>>
    @Query("SELECT * FROM installments ORDER BY debtId, number") fun observeInstallments(): Flow<List<InstallmentEntity>>
    @Query("SELECT * FROM payments ORDER BY date DESC, id DESC") fun observePayments(): Flow<List<PaymentEntity>>
    @Query("SELECT * FROM income_sources ORDER BY name") fun observeIncomeSources(): Flow<List<IncomeSourceEntity>>
    @Query("SELECT * FROM income_occurrences ORDER BY date") fun observeIncomeOccurrences(): Flow<List<IncomeOccurrenceEntity>>
    @Query("SELECT * FROM bill_templates ORDER BY name") fun observeBillTemplates(): Flow<List<BillTemplateEntity>>
    @Query("SELECT * FROM bill_occurrences ORDER BY dueDate") fun observeBillOccurrences(): Flow<List<BillOccurrenceEntity>>
    @Query("SELECT * FROM change_log ORDER BY timestamp DESC") fun observeChangeLog(): Flow<List<ChangeLogEntity>>
    @Query("SELECT * FROM settings") fun observeSettings(): Flow<List<SettingEntity>>

    // ---------------- Leituras pontuais (dentro de transações) ----------------
    @Query("SELECT * FROM debts") suspend fun debts(): List<DebtEntity>
    @Query("SELECT * FROM installments") suspend fun installments(): List<InstallmentEntity>
    @Query("SELECT * FROM payments") suspend fun payments(): List<PaymentEntity>
    @Query("SELECT * FROM income_sources") suspend fun incomeSources(): List<IncomeSourceEntity>
    @Query("SELECT * FROM income_occurrences") suspend fun incomeOccurrences(): List<IncomeOccurrenceEntity>
    @Query("SELECT * FROM bill_templates") suspend fun billTemplates(): List<BillTemplateEntity>
    @Query("SELECT * FROM bill_occurrences") suspend fun billOccurrences(): List<BillOccurrenceEntity>
    @Query("SELECT * FROM change_log") suspend fun changeLog(): List<ChangeLogEntity>
    @Query("SELECT * FROM settings") suspend fun settings(): List<SettingEntity>

    @Query("SELECT * FROM debts WHERE id = :id") suspend fun debt(id: Long): DebtEntity?
    @Query("SELECT * FROM payments WHERE id = :id") suspend fun payment(id: Long): PaymentEntity?
    @Query("SELECT COUNT(*) FROM payments WHERE requestId = :requestId") suspend fun countRequest(requestId: String): Int

    // ---------------- Escritas ----------------
    @Insert suspend fun insertDebt(e: DebtEntity): Long
    @Update suspend fun updateDebt(e: DebtEntity)
    @Query("UPDATE debts SET status = :status WHERE id = :id") suspend fun setDebtStatus(id: Long, status: String)
    @Query("DELETE FROM debts WHERE id = :id") suspend fun deleteDebt(id: Long)

    @Insert suspend fun insertInstallments(e: List<InstallmentEntity>)
    @Update suspend fun updateInstallment(e: InstallmentEntity)

    /** ABORT: requestId único impede pagamento duplicado mesmo em reprocessamento. */
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPayments(e: List<PaymentEntity>)
    @Update suspend fun updatePayment(e: PaymentEntity)
    @Query("DELETE FROM payments WHERE debtId = :debtId OR (target = 'DEBT_AGREEMENT' AND targetId = :debtId)")
    suspend fun deletePaymentsOfDebt(debtId: Long)
    @Query("DELETE FROM payments WHERE target = 'BILL' AND targetId IN (SELECT id FROM bill_occurrences WHERE templateId = :templateId)")
    suspend fun deletePaymentsOfTemplate(templateId: Long)

    @Insert suspend fun insertIncomeSource(e: IncomeSourceEntity): Long
    @Update suspend fun updateIncomeSource(e: IncomeSourceEntity)
    @Query("DELETE FROM income_sources WHERE id = :id") suspend fun deleteIncomeSource(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIncomeOccurrences(e: List<IncomeOccurrenceEntity>)
    @Update suspend fun updateIncomeOccurrence(e: IncomeOccurrenceEntity)
    @Query("DELETE FROM income_occurrences WHERE sourceId = :sourceId AND receivedAmount IS NULL AND date >= :fromDay")
    suspend fun deleteFutureUnreceived(sourceId: Long, fromDay: Long)

    @Insert suspend fun insertBillTemplate(e: BillTemplateEntity): Long
    @Update suspend fun updateBillTemplate(e: BillTemplateEntity)
    @Query("DELETE FROM bill_templates WHERE id = :id") suspend fun deleteBillTemplate(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertBillOccurrences(e: List<BillOccurrenceEntity>)
    @Update suspend fun updateBillOccurrence(e: BillOccurrenceEntity)

    @Insert suspend fun insertLog(e: ChangeLogEntity)
    @Insert suspend fun insertLogs(e: List<ChangeLogEntity>)

    @Upsert suspend fun upsertSettings(e: List<SettingEntity>)

    // ---------------- Restauração ----------------
    @Insert suspend fun insertDebts(e: List<DebtEntity>)
    @Insert suspend fun insertIncomeSources(e: List<IncomeSourceEntity>)
    @Insert suspend fun insertBillTemplates(e: List<BillTemplateEntity>)
    @Query("DELETE FROM payments") suspend fun clearPayments()
    @Query("DELETE FROM installments") suspend fun clearInstallments()
    @Query("DELETE FROM debts") suspend fun clearDebts()
    @Query("DELETE FROM income_occurrences") suspend fun clearIncomeOccurrences()
    @Query("DELETE FROM income_sources") suspend fun clearIncomeSources()
    @Query("DELETE FROM bill_occurrences") suspend fun clearBillOccurrences()
    @Query("DELETE FROM bill_templates") suspend fun clearBillTemplates()
    @Query("DELETE FROM change_log") suspend fun clearChangeLog()

    @Delete suspend fun deleteOccurrence(e: BillOccurrenceEntity)
}
