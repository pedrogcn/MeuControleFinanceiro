package br.com.meucontrole.financeiro.data.db

import br.com.meucontrole.financeiro.domain.model.AppSettings
import br.com.meucontrole.financeiro.domain.model.BillOccurrence
import br.com.meucontrole.financeiro.domain.model.BillTemplate
import br.com.meucontrole.financeiro.domain.model.ChangeLogEntry
import br.com.meucontrole.financeiro.domain.model.Debt
import br.com.meucontrole.financeiro.domain.model.DebtStatus
import br.com.meucontrole.financeiro.domain.model.IncomeFrequency
import br.com.meucontrole.financeiro.domain.model.IncomeKind
import br.com.meucontrole.financeiro.domain.model.IncomeOccurrence
import br.com.meucontrole.financeiro.domain.model.IncomeSource
import br.com.meucontrole.financeiro.domain.model.Installment
import br.com.meucontrole.financeiro.domain.model.Payment
import br.com.meucontrole.financeiro.domain.model.PaymentTarget
import br.com.meucontrole.financeiro.domain.model.Recurrence
import br.com.meucontrole.financeiro.domain.model.ThemeMode
import java.time.LocalDate

private fun day(d: LocalDate): Long = d.toEpochDay()
private fun date(l: Long): LocalDate = LocalDate.ofEpochDay(l)

private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

fun DebtEntity.toDomain() = Debt(
    id, name, creditor, category, originalAmount, downPayment, date(contractDate), totalContracted, installmentCount,
    installmentAmount, date(firstDueDate), dueDay, interestRateInfo, registeredCharges, negotiatedDiscount, notes,
    enumOr(status, DebtStatus.ATIVA), renegotiatedFromId, createdAt
)

fun Debt.toEntity() = DebtEntity(
    id, name, creditor, category, originalAmount, downPayment, day(contractDate), totalContracted, installmentCount,
    installmentAmount, day(firstDueDate), dueDay, interestRateInfo, registeredCharges, negotiatedDiscount, notes,
    status.name, renegotiatedFromId, createdAt
)

fun InstallmentEntity.toDomain() = Installment(id, debtId, number, totalCount, amount, date(dueDate), notes)
fun Installment.toEntity() = InstallmentEntity(id, debtId, number, totalCount, amount, day(dueDate), notes)

fun PaymentEntity.toDomain() = Payment(
    id, requestId, enumOr(target, PaymentTarget.INSTALLMENT), targetId, debtId, amount, charges, discount, date(date),
    method, notes, reversed, reversalReason, reversedAt, createdAt, editedAt
)

fun Payment.toEntity() = PaymentEntity(
    id, requestId, target.name, targetId, debtId, amount, charges, discount, day(date), method, notes, reversed,
    reversalReason, reversedAt, createdAt, editedAt
)

fun IncomeSourceEntity.toDomain() = IncomeSource(
    id, name, enumOr(kind, IncomeKind.OUTRA), grossAmount, netAmount, enumOr(frequency, IncomeFrequency.MENSAL),
    date(startDate), endDate?.let { date(it) }, active, notes, createdAt
)

fun IncomeSource.toEntity() = IncomeSourceEntity(
    id, name, kind.name, grossAmount, netAmount, frequency.name, day(startDate), endDate?.let { day(it) }, active, notes, createdAt
)

fun IncomeOccurrenceEntity.toDomain() = IncomeOccurrence(
    id, sourceId, date(date), expectedAmount, receivedAmount, receivedDate?.let { date(it) }, notes
)

fun IncomeOccurrence.toEntity() = IncomeOccurrenceEntity(
    id, sourceId, day(date), expectedAmount, receivedAmount, receivedDate?.let { day(it) }, notes
)

fun BillTemplateEntity.toDomain() = BillTemplate(
    id, name, category, expectedAmount, enumOr(recurrence, Recurrence.MENSAL), date(startDate), endDate?.let { date(it) },
    active, notes, createdAt
)

fun BillTemplate.toEntity() = BillTemplateEntity(
    id, name, category, expectedAmount, recurrence.name, day(startDate), endDate?.let { day(it) }, active, notes, createdAt
)

fun BillOccurrenceEntity.toDomain() = BillOccurrence(id, templateId, date(dueDate), expectedAmount, notes, skipped)
fun BillOccurrence.toEntity() = BillOccurrenceEntity(id, templateId, day(dueDate), expectedAmount, notes, skipped)

fun ChangeLogEntity.toDomain() = ChangeLogEntry(id, entityType, entityId, timestamp, description)
fun ChangeLogEntry.toEntity() = ChangeLogEntity(id, entityType, entityId, timestamp, description)

object SettingKeys {
    const val OPENING_BALANCE = "opening_balance"
    const val OPENING_DATE = "opening_date"
    const val REMIND_DAY_BEFORE = "remind_day_before"
    const val REMIND_SAME_DAY = "remind_same_day"
    const val REMINDER_HOUR = "reminder_hour"
    const val THEME = "theme_mode"
}

fun List<SettingEntity>.toSettings(): AppSettings {
    val m = associate { it.key to it.value }
    return AppSettings(
        openingBalance = m[SettingKeys.OPENING_BALANCE]?.toLongOrNull() ?: 0,
        openingDate = m[SettingKeys.OPENING_DATE]?.toLongOrNull()?.let { date(it) },
        remindDayBefore = m[SettingKeys.REMIND_DAY_BEFORE]?.toBooleanStrictOrNull() ?: true,
        remindSameDay = m[SettingKeys.REMIND_SAME_DAY]?.toBooleanStrictOrNull() ?: true,
        reminderHour = m[SettingKeys.REMINDER_HOUR]?.toIntOrNull()?.coerceIn(0, 23) ?: 8,
        themeMode = m[SettingKeys.THEME]?.let { enumOr(it, ThemeMode.SISTEMA) } ?: ThemeMode.SISTEMA
    )
}

fun AppSettings.toEntities(): List<SettingEntity> = listOf(
    SettingEntity(SettingKeys.OPENING_BALANCE, openingBalance.toString()),
    SettingEntity(SettingKeys.OPENING_DATE, openingDate?.let { day(it).toString() } ?: ""),
    SettingEntity(SettingKeys.REMIND_DAY_BEFORE, remindDayBefore.toString()),
    SettingEntity(SettingKeys.REMIND_SAME_DAY, remindSameDay.toString()),
    SettingEntity(SettingKeys.REMINDER_HOUR, reminderHour.toString()),
    SettingEntity(SettingKeys.THEME, themeMode.name)
)
