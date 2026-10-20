package br.com.meucontrole.financeiro.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import br.com.meucontrole.financeiro.backup.BackupStore
import br.com.meucontrole.financeiro.data.db.AppDatabase
import br.com.meucontrole.financeiro.data.db.ChangeLogEntity
import br.com.meucontrole.financeiro.data.db.toDomain
import br.com.meucontrole.financeiro.data.db.toEntities
import br.com.meucontrole.financeiro.data.db.toEntity
import br.com.meucontrole.financeiro.data.db.toSettings
import br.com.meucontrole.financeiro.domain.calc.RecurrenceEngine
import br.com.meucontrole.financeiro.domain.model.AppSettings
import br.com.meucontrole.financeiro.domain.model.BillOccurrence
import br.com.meucontrole.financeiro.domain.model.BillTemplate
import br.com.meucontrole.financeiro.domain.model.Debt
import br.com.meucontrole.financeiro.domain.model.DebtStatus
import br.com.meucontrole.financeiro.domain.model.FinanceData
import br.com.meucontrole.financeiro.domain.model.IncomeOccurrence
import br.com.meucontrole.financeiro.domain.model.IncomeSource
import br.com.meucontrole.financeiro.domain.model.Installment
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.domain.model.Payment
import br.com.meucontrole.financeiro.domain.usecase.DebtDraft
import br.com.meucontrole.financeiro.domain.usecase.DebtFactory
import br.com.meucontrole.financeiro.domain.usecase.DebtStatusRules
import br.com.meucontrole.financeiro.domain.usecase.Outcome
import br.com.meucontrole.financeiro.domain.usecase.PaymentEditRules
import br.com.meucontrole.financeiro.domain.usecase.PaymentPlan
import br.com.meucontrole.financeiro.domain.usecase.PaymentPlanner
import br.com.meucontrole.financeiro.domain.usecase.PaymentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate

/** Resultado de uma operação de escrita, para exibição na interface. */
sealed class OpResult {
    data class Success(val message: String, val id: Long = 0) : OpResult()
    data class Failure(val message: String) : OpResult()
    data class NeedsDecision(val decision: PaymentPlan.NeedsDecision) : OpResult()
}

/**
 * Única porta de acesso aos dados. Toda escrita financeira acontece dentro de
 * [AppDatabase.withTransaction]: ou tudo é gravado, ou nada é.
 */
class FinanceRepository(
    private val db: AppDatabase,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val clock: () -> Long = { System.currentTimeMillis() }
) : BackupStore {

    private val dao = db.dao()

    private data class Part1(val a: List<Debt>, val b: List<Installment>, val c: List<Payment>)
    private data class Part2(val a: List<IncomeSource>, val b: List<IncomeOccurrence>, val c: List<BillTemplate>, val d: List<BillOccurrence>)

    /** Fluxo com a foto completa dos dados. Emite novamente após cada transação concluída. */
    val data: Flow<FinanceData> = combine(
        combine(dao.observeDebts(), dao.observeInstallments(), dao.observePayments()) { d, i, p ->
            Part1(d.map { it.toDomain() }, i.map { it.toDomain() }, p.map { it.toDomain() })
        },
        combine(dao.observeIncomeSources(), dao.observeIncomeOccurrences(), dao.observeBillTemplates(), dao.observeBillOccurrences()) { s, o, t, b ->
            Part2(s.map { it.toDomain() }, o.map { it.toDomain() }, t.map { it.toDomain() }, b.map { it.toDomain() })
        },
        dao.observeChangeLog(),
        dao.observeSettings()
    ) { p1, p2, log, settings ->
        FinanceData(p1.a, p1.b, p1.c, p2.a, p2.b, p2.c, p2.d, log.map { it.toDomain() }, settings.toSettings())
    }.conflate().flowOn(Dispatchers.Default)

    override suspend fun snapshot(): FinanceData = FinanceData(
        debts = dao.debts().map { it.toDomain() },
        installments = dao.installments().map { it.toDomain() },
        payments = dao.payments().map { it.toDomain() },
        incomeSources = dao.incomeSources().map { it.toDomain() },
        incomeOccurrences = dao.incomeOccurrences().map { it.toDomain() },
        billTemplates = dao.billTemplates().map { it.toDomain() },
        billOccurrences = dao.billOccurrences().map { it.toDomain() },
        changeLog = dao.changeLog().map { it.toDomain() },
        settings = dao.settings().toSettings()
    )

    private suspend fun log(type: String, id: Long, text: String) =
        dao.insertLog(ChangeLogEntity(entityType = type, entityId = id, timestamp = clock(), description = text))

    private suspend fun recomputeDebt(debtId: Long) {
        val data = snapshot()
        val debt = data.debts.firstOrNull { it.id == debtId } ?: return
        val status = DebtStatusRules.recompute(debt, data, todayProvider())
        if (status != debt.status) {
            dao.setDebtStatus(debtId, status.name)
            log("debt", debtId, "Status alterado para ${status.label}")
        }
    }

    // ------------------------------- Recorrências -------------------------------

    /** Gera ocorrências faltantes de contas e receitas. Idempotente: índices únicos + INSERT OR IGNORE. */
    suspend fun ensureOccurrences(reference: LocalDate = todayProvider()) {
        val horizon = maxOf(RecurrenceEngine.defaultHorizon(todayProvider()), reference)
        db.withTransaction {
            val templates = dao.billTemplates().map { it.toDomain() }
            val bills = dao.billOccurrences().map { it.toDomain() }
            val missingBills = RecurrenceEngine.missingBillOccurrences(templates, bills, horizon)
            if (missingBills.isNotEmpty()) dao.insertBillOccurrences(missingBills.map { it.toEntity() })

            val sources = dao.incomeSources().map { it.toDomain() }
            val incomes = dao.incomeOccurrences().map { it.toDomain() }
            val missingInc = RecurrenceEngine.missingIncomeOccurrences(sources, incomes, horizon)
            if (missingInc.isNotEmpty()) dao.insertIncomeOccurrences(missingInc.map { it.toEntity() })
        }
    }

    // ------------------------------- Dívidas -------------------------------

    suspend fun createDebt(draft: DebtDraft): OpResult {
        val built = DebtFactory.build(draft, clock())
        if (built is Outcome.Error) return OpResult.Failure(built.message)
        val (debt, installments) = (built as Outcome.Ok).value
        val id = db.withTransaction {
            val id = dao.insertDebt(debt.toEntity())
            dao.insertInstallments(installments.map { it.copy(debtId = id).toEntity() })
            log("debt", id, "Dívida cadastrada: ${debt.installmentCount} parcela(s), total ${Money.format(debt.totalContracted)}")
            id
        }
        return OpResult.Success("Dívida cadastrada.", id)
    }

    /** Edita apenas dados descritivos (não altera parcelas nem pagamentos). */
    suspend fun updateDebtInfo(debt: Debt): OpResult {
        if (debt.name.isBlank()) return OpResult.Failure("Informe o nome da dívida.")
        db.withTransaction {
            val current = dao.debt(debt.id)?.toDomain() ?: return@withTransaction
            val updated = current.copy(
                name = debt.name.trim(), creditor = debt.creditor.trim(), category = debt.category,
                interestRateInfo = debt.interestRateInfo.trim(), registeredCharges = debt.registeredCharges,
                negotiatedDiscount = debt.negotiatedDiscount, notes = debt.notes.trim(), originalAmount = debt.originalAmount
            )
            dao.updateDebt(updated.toEntity())
            log("debt", debt.id, "Dados da dívida editados")
        }
        return OpResult.Success("Dívida atualizada.")
    }

    /** Ajusta valor/vencimento de uma parcela ainda sem pagamentos. */
    suspend fun updateInstallment(inst: Installment): OpResult {
        if (inst.amount <= 0) return OpResult.Failure("O valor da parcela deve ser maior que zero.")
        return db.withTransaction {
            val data = snapshot()
            val hasPayments = data.payments.any { it.target.name == "INSTALLMENT" && it.targetId == inst.id && !it.reversed }
            if (hasPayments) return@withTransaction OpResult.Failure("Esta parcela já tem pagamentos. Estorne-os antes de alterar o valor previsto.")
            val old = data.installments.firstOrNull { it.id == inst.id } ?: return@withTransaction OpResult.Failure("Parcela não encontrada.")
            dao.updateInstallment(old.copy(amount = inst.amount, dueDate = inst.dueDate, notes = inst.notes).toEntity())
            log("debt", old.debtId, "Parcela ${old.label}: ${Money.format(old.amount)} → ${Money.format(inst.amount)}, vencimento ${inst.dueDate}")
            recomputeDebt(old.debtId)
            OpResult.Success("Parcela atualizada.")
        }
    }

    suspend fun deleteDebt(debtId: Long): OpResult {
        db.withTransaction {
            dao.deletePaymentsOfDebt(debtId)
            dao.deleteDebt(debtId) // parcelas removidas em cascata
            log("debt", debtId, "Dívida excluída")
        }
        return OpResult.Success("Dívida excluída.")
    }

    /**
     * Renegociação: a dívida antiga passa a RENEGOCIADA (parcelas e pagamentos preservados)
     * e uma nova dívida é criada com referência à anterior.
     */
    suspend fun renegotiate(oldDebtId: Long, draft: DebtDraft): OpResult {
        val built = DebtFactory.build(draft.copy(renegotiatedFromId = oldDebtId), clock())
        if (built is Outcome.Error) return OpResult.Failure(built.message)
        val (debt, installments) = (built as Outcome.Ok).value
        return db.withTransaction {
            val old = dao.debt(oldDebtId)?.toDomain() ?: return@withTransaction OpResult.Failure("Dívida original não encontrada.")
            if (old.status != DebtStatus.ATIVA) return@withTransaction OpResult.Failure("Só é possível renegociar uma dívida ativa.")
            dao.setDebtStatus(oldDebtId, DebtStatus.RENEGOCIADA.name)
            val id = dao.insertDebt(debt.toEntity())
            dao.insertInstallments(installments.map { it.copy(debtId = id).toEntity() })
            log("debt", oldDebtId, "Dívida renegociada (nova dívida #$id)")
            log("debt", id, "Criada por renegociação da dívida #$oldDebtId")
            OpResult.Success("Renegociação registrada.", id)
        }
    }

    // ------------------------------- Pagamentos -------------------------------

    /**
     * Registra pagamento(s) de forma transacional e idempotente.
     * O mesmo requestId nunca é gravado duas vezes (verificação + índice único).
     */
    suspend fun registerPayment(req: PaymentRequest): OpResult = try {
        db.withTransaction {
            if (dao.countRequest(req.requestId) > 0) return@withTransaction OpResult.Success("Pagamento já registrado.")
            val data = snapshot()
            when (val plan = PaymentPlanner.plan(req, data, todayProvider(), clock())) {
                is PaymentPlan.Invalid -> OpResult.Failure(plan.message)
                is PaymentPlan.NeedsDecision -> OpResult.NeedsDecision(plan)
                is PaymentPlan.Ready -> {
                    dao.insertPayments(plan.payments.map { it.toEntity() })
                    plan.payments.mapNotNull { it.debtId }.distinct().forEach { recomputeDebt(it) }
                    val total = plan.payments.sumOf { it.amount }
                    OpResult.Success("Pagamento de ${Money.format(total)} registrado.")
                }
            }
        }
    } catch (e: SQLiteConstraintException) {
        OpResult.Success("Pagamento já registrado.")
    }

    suspend fun reversePayment(paymentId: Long, reason: String): OpResult = db.withTransaction {
        val p = dao.payment(paymentId)?.toDomain() ?: return@withTransaction OpResult.Failure("Pagamento não encontrado.")
        when (val r = PaymentEditRules.reverse(p, reason, clock())) {
            is Outcome.Error -> OpResult.Failure(r.message)
            is Outcome.Ok -> {
                dao.updatePayment(r.value.toEntity())
                log("payment", p.id, "Estorno de ${Money.format(p.amount)}: ${reason.trim()}")
                // Pagamentos de parcelas e acordos sempre carregam debtId
                p.debtId?.let { recomputeDebt(it) }
                OpResult.Success("Pagamento estornado.")
            }
        }
    }

    suspend fun editPayment(
        paymentId: Long, amount: Long, charges: Long, discount: Long, date: LocalDate, method: String, notes: String
    ): OpResult = db.withTransaction {
        val data = snapshot()
        val p = data.payments.firstOrNull { it.id == paymentId } ?: return@withTransaction OpResult.Failure("Pagamento não encontrado.")
        when (val r = PaymentEditRules.edit(p, amount, charges, discount, date, method, notes, data, clock())) {
            is Outcome.Error -> OpResult.Failure(r.message)
            is Outcome.Ok -> {
                dao.updatePayment(r.value.toEntity())
                log("payment", p.id, "Lançamento corrigido: ${Money.format(p.amount)} → ${Money.format(amount)}")
                p.debtId?.let { recomputeDebt(it) }
                OpResult.Success("Lançamento corrigido.")
            }
        }
    }

    // ------------------------------- Contas -------------------------------

    suspend fun saveBillTemplate(t: BillTemplate): OpResult {
        if (t.name.isBlank()) return OpResult.Failure("Informe o nome da conta.")
        if (t.expectedAmount < 0) return OpResult.Failure("O valor previsto não pode ser negativo.")
        if (t.endDate != null && t.endDate.isBefore(t.startDate)) return OpResult.Failure("A data final é anterior à inicial.")
        val today = todayProvider()
        val id = db.withTransaction {
            if (t.id == 0L) {
                val id = dao.insertBillTemplate(t.copy(createdAt = clock()).toEntity())
                log("bill", id, "Conta cadastrada: ${Money.format(t.expectedAmount)} (${t.recurrence.label})")
                id
            } else {
                val data = snapshot()
                val old = data.billTemplates.firstOrNull { it.id == t.id } ?: return@withTransaction t.id
                dao.updateBillTemplate(t.copy(createdAt = old.createdAt).toEntity())
                val changes = buildList {
                    if (old.name != t.name) add("nome \"${old.name}\" → \"${t.name}\"")
                    if (old.expectedAmount != t.expectedAmount) add("valor ${Money.format(old.expectedAmount)} → ${Money.format(t.expectedAmount)}")
                    if (old.category != t.category) add("categoria ${old.category} → ${t.category}")
                    if (old.recurrence != t.recurrence) add("recorrência ${old.recurrence.label} → ${t.recurrence.label}")
                    if (old.active != t.active) add(if (t.active) "reativada" else "desativada")
                }
                if (changes.isNotEmpty()) log("bill", t.id, "Alterado: " + changes.joinToString("; "))
                // Atualiza apenas ocorrências FUTURAS sem pagamento. Passadas e pagas ficam intactas.
                if (old.expectedAmount != t.expectedAmount) {
                    data.billOccurrences.filter { it.templateId == t.id && !it.dueDate.isBefore(today) }
                        .filter { o -> data.payments.none { it.target.name == "BILL" && it.targetId == o.id && !it.reversed } }
                        .forEach { dao.updateBillOccurrence(it.copy(expectedAmount = t.expectedAmount).toEntity()) }
                }
                // Se a recorrência/data inicial mudou, remove futuras sem pagamento que não pertencem mais à agenda
                if (old.recurrence != t.recurrence || old.startDate != t.startDate || old.endDate != t.endDate || !t.active) {
                    val valid = if (t.active) RecurrenceEngine.billDueDates(t, t.startDate, RecurrenceEngine.defaultHorizon(today).plusYears(1)).toSet() else emptySet()
                    data.billOccurrences.filter { it.templateId == t.id && !it.dueDate.isBefore(today) && it.dueDate !in valid }
                        .filter { o -> data.payments.none { it.target.name == "BILL" && it.targetId == o.id } }
                        .forEach { dao.deleteOccurrence(it.toEntity()) }
                }
                t.id
            }
        }
        ensureOccurrences()
        return OpResult.Success("Conta salva.", id)
    }

    suspend fun deleteBillTemplate(id: Long): OpResult {
        db.withTransaction {
            dao.deletePaymentsOfTemplate(id)
            dao.deleteBillTemplate(id)
            log("bill", id, "Conta excluída")
        }
        return OpResult.Success("Conta excluída.")
    }

    /** Altera uma ocorrência específica (valor/vencimento/observação) sem afetar as demais. */
    suspend fun updateBillOccurrence(o: BillOccurrence): OpResult {
        if (o.expectedAmount < 0) return OpResult.Failure("Valor inválido.")
        return db.withTransaction {
            val old = dao.billOccurrences().firstOrNull { it.id == o.id }?.toDomain()
                ?: return@withTransaction OpResult.Failure("Conta não encontrada.")
            try {
                dao.updateBillOccurrence(o.toEntity())
            } catch (e: SQLiteConstraintException) {
                return@withTransaction OpResult.Failure("Já existe uma ocorrência desta conta nesta data.")
            }
            log("bill", o.templateId, "Ocorrência de ${old.dueDate}: ${Money.format(old.expectedAmount)} → ${Money.format(o.expectedAmount)}" +
                if (old.skipped != o.skipped) (if (o.skipped) " (ignorada)" else " (reativada)") else "")
            OpResult.Success("Conta atualizada.")
        }
    }

    // ------------------------------- Receitas -------------------------------

    suspend fun saveIncomeSource(s: IncomeSource): OpResult {
        if (s.name.isBlank()) return OpResult.Failure("Informe o nome da fonte de renda.")
        if (s.netAmount < 0 || (s.grossAmount ?: 0) < 0) return OpResult.Failure("Valores não podem ser negativos.")
        val today = todayProvider()
        val id = db.withTransaction {
            if (s.id == 0L) {
                val id = dao.insertIncomeSource(s.copy(createdAt = clock()).toEntity())
                log("income", id, "Fonte de renda cadastrada: líquido ${Money.format(s.netAmount)}")
                id
            } else {
                val old = dao.incomeSources().firstOrNull { it.id == s.id }?.toDomain()
                dao.updateIncomeSource(s.copy(createdAt = old?.createdAt ?: clock()).toEntity())
                // Previsões futuras ainda não recebidas são regeradas com a nova configuração
                dao.deleteFutureUnreceived(s.id, today.toEpochDay())
                log("income", s.id, "Fonte de renda editada")
                s.id
            }
        }
        ensureOccurrences()
        return OpResult.Success("Receita salva.", id)
    }

    suspend fun deleteIncomeSource(id: Long): OpResult {
        db.withTransaction {
            dao.deleteIncomeSource(id)
            log("income", id, "Fonte de renda excluída")
        }
        return OpResult.Success("Fonte de renda excluída.")
    }

    /** Registra (ou corrige) o recebimento efetivo de uma ocorrência. amount = null desfaz o recebimento. */
    suspend fun setReceived(occurrenceId: Long, amount: Long?, date: LocalDate?, expected: Long? = null, notes: String? = null): OpResult {
        if (amount != null && amount < 0) return OpResult.Failure("Valor não pode ser negativo.")
        return db.withTransaction {
            val o = dao.incomeOccurrences().firstOrNull { it.id == occurrenceId }?.toDomain()
                ?: return@withTransaction OpResult.Failure("Recebimento não encontrado.")
            val updated = o.copy(
                receivedAmount = amount,
                receivedDate = if (amount == null) null else (date ?: todayProvider()),
                expectedAmount = expected ?: o.expectedAmount,
                notes = notes ?: o.notes
            )
            dao.updateIncomeOccurrence(updated.toEntity())
            log("income", o.sourceId, if (amount == null) "Recebimento de ${o.date} desfeito" else "Recebido ${Money.format(amount)} (previsto ${Money.format(updated.expectedAmount)})")
            OpResult.Success(if (amount == null) "Recebimento desfeito." else "Recebimento registrado.")
        }
    }

    suspend fun addOneOffIncome(sourceId: Long, date: LocalDate, amount: Long, received: Boolean): OpResult {
        if (amount <= 0) return OpResult.Failure("Informe um valor maior que zero.")
        db.withTransaction {
            dao.insertIncomeOccurrences(listOf(
                IncomeOccurrence(sourceId = sourceId, date = date, expectedAmount = amount,
                    receivedAmount = if (received) amount else null, receivedDate = if (received) date else null).toEntity()
            ))
        }
        return OpResult.Success("Lançamento adicionado.")
    }

    // ------------------------------- Configurações -------------------------------

    suspend fun saveSettings(s: AppSettings) {
        db.withTransaction { dao.upsertSettings(s.toEntities()) }
    }

    // ------------------------------- Backup -------------------------------

    override suspend fun replaceAll(data: FinanceData) {
        db.withTransaction {
            dao.clearPayments(); dao.clearInstallments(); dao.clearDebts()
            dao.clearIncomeOccurrences(); dao.clearIncomeSources()
            dao.clearBillOccurrences(); dao.clearBillTemplates(); dao.clearChangeLog()
            insertAll(data)
            dao.upsertSettings(data.settings.toEntities())
            log("backup", 0, "Dados substituídos por restauração de backup")
        }
    }

    override suspend fun insertMerged(data: FinanceData) {
        db.withTransaction {
            insertAll(data)
            log("backup", 0, "Backup mesclado: ${data.debts.size} dívida(s), ${data.payments.size} pagamento(s) adicionados")
        }
    }

    private suspend fun insertAll(data: FinanceData) {
        // Ordem respeita as chaves estrangeiras
        dao.insertDebts(data.debts.map { it.toEntity() })
        dao.insertInstallments(data.installments.map { it.toEntity() })
        dao.insertIncomeSources(data.incomeSources.map { it.toEntity() })
        dao.insertIncomeOccurrences(data.incomeOccurrences.map { it.toEntity() })
        dao.insertBillTemplates(data.billTemplates.map { it.toEntity() })
        dao.insertBillOccurrences(data.billOccurrences.map { it.toEntity() })
        dao.insertPayments(data.payments.map { it.toEntity() })
        dao.insertLogs(data.changeLog.map { it.toEntity() })
    }
}
