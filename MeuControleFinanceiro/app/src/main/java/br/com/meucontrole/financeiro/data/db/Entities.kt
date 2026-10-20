package br.com.meucontrole.financeiro.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Datas são armazenadas como "epoch day" (Long) e enums como texto.
// Valores monetários SEMPRE em centavos (Long/INTEGER no SQLite).

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val creditor: String,
    val category: String,
    val originalAmount: Long,
    val downPayment: Long,
    val contractDate: Long,
    val totalContracted: Long,
    val installmentCount: Int,
    val installmentAmount: Long,
    val firstDueDate: Long,
    val dueDay: Int,
    val interestRateInfo: String,
    val registeredCharges: Long,
    val negotiatedDiscount: Long,
    val notes: String,
    val status: String,
    val renegotiatedFromId: Long?,
    val createdAt: Long
)

@Entity(
    tableName = "installments",
    foreignKeys = [ForeignKey(entity = DebtEntity::class, parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("debtId"), Index(value = ["debtId", "number"], unique = true)]
)
data class InstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val number: Int,
    val totalCount: Int,
    val amount: Long,
    val dueDate: Long,
    val notes: String
)

@Entity(
    tableName = "payments",
    indices = [Index(value = ["requestId"], unique = true), Index(value = ["target", "targetId"]), Index("debtId")]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestId: String,
    val target: String,
    val targetId: Long,
    val debtId: Long?,
    val amount: Long,
    val charges: Long,
    val discount: Long,
    val date: Long,
    val method: String,
    val notes: String,
    val reversed: Boolean,
    val reversalReason: String?,
    val reversedAt: Long?,
    val createdAt: Long,
    val editedAt: Long?
)

@Entity(tableName = "income_sources")
data class IncomeSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val grossAmount: Long?,
    val netAmount: Long,
    val frequency: String,
    val startDate: Long,
    val endDate: Long?,
    val active: Boolean,
    val notes: String,
    val createdAt: Long
)

@Entity(
    tableName = "income_occurrences",
    foreignKeys = [ForeignKey(entity = IncomeSourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sourceId", "date"], unique = true)]
)
data class IncomeOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val date: Long,
    val expectedAmount: Long,
    val receivedAmount: Long?,
    val receivedDate: Long?,
    val notes: String
)

@Entity(tableName = "bill_templates")
data class BillTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val expectedAmount: Long,
    val recurrence: String,
    val startDate: Long,
    val endDate: Long?,
    val active: Boolean,
    val notes: String,
    val createdAt: Long
)

@Entity(
    tableName = "bill_occurrences",
    foreignKeys = [ForeignKey(entity = BillTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["templateId", "dueDate"], unique = true)]
)
data class BillOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val dueDate: Long,
    val expectedAmount: Long,
    val notes: String,
    val skipped: Boolean
)

@Entity(tableName = "change_log", indices = [Index(value = ["entityType", "entityId"])])
data class ChangeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: Long,
    val timestamp: Long,
    val description: String
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
