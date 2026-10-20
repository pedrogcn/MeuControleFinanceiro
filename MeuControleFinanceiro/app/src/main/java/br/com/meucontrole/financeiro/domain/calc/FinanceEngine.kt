package br.com.meucontrole.financeiro.domain.calc

import br.com.meucontrole.financeiro.domain.model.BillOccurrence
import br.com.meucontrole.financeiro.domain.model.BillTemplate
import br.com.meucontrole.financeiro.domain.model.Debt
import br.com.meucontrole.financeiro.domain.model.DebtStatus
import br.com.meucontrole.financeiro.domain.model.FinanceData
import br.com.meucontrole.financeiro.domain.model.IncomeKind
import br.com.meucontrole.financeiro.domain.model.IncomeOccurrence
import br.com.meucontrole.financeiro.domain.model.IncomeSource
import br.com.meucontrole.financeiro.domain.model.Installment
import br.com.meucontrole.financeiro.domain.model.Payment
import br.com.meucontrole.financeiro.domain.model.PaymentTarget
import java.time.LocalDate
import java.time.YearMonth

// ------------------------------- Resultados -------------------------------

data class InstallmentView(val installment: Installment, val settlement: Settlement, val payments: List<Payment>)

data class DebtSummary(
    val debt: Debt,
    val installments: List<InstallmentView>,
    /** Soma prevista das parcelas (saldo a parcelar). */
    val installmentsPlanned: Long,
    /** Entrada + parcelas previstas + encargos cobrados - descontos concedidos. */
    val totalForecast: Long,
    val chargesCharged: Long,
    val discountsGranted: Long,
    /** Pago em parcelas + acordos (sem a entrada). */
    val totalPaid: Long,
    /** Saldo devedor atualizado. Zero para dívidas quitadas ou renegociadas. */
    val outstanding: Long,
    val overdue: Long,
    val paidCount: Int,
    val remainingCount: Int,
    val progress: Float,
    val nextDue: InstallmentView?,
    val forecastPayoff: LocalDate?,
    val agreementPayments: List<Payment>
)

enum class DueKind { BILL, INSTALLMENT, INCOME }

data class DueItem(
    val kind: DueKind,
    val refId: Long,
    val title: String,
    val subtitle: String,
    val date: LocalDate,
    val expected: Long,
    val done: Long,
    val remaining: Long,
    val status: ItemStatus,
    val category: String
)

data class BudgetResult(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val openingBalance: Long,
    val openingDate: LocalDate?,
    /** Receitas efetivamente recebidas no período. */
    val receivedIncome: Long,
    /** Receitas ainda previstas (não recebidas) até o fim do período. */
    val pendingIncome: Long,
    /** Despesas (contas + dívidas) efetivamente pagas no período. */
    val paidExpenses: Long,
    val pendingBills: Long,
    val pendingInstallments: Long,
    /** Saldo realizado do período = recebido - pago. */
    val realizedPeriod: Long,
    /** Caixa estimado hoje = saldo inicial + tudo recebido - tudo pago desde a data do saldo inicial. */
    val cashEstimate: Long,
    /** Projetado = caixa estimado + receitas previstas - compromissos pendentes até o fim do período. */
    val projected: Long
) {
    val reserved: Long get() = pendingBills + pendingInstallments
    val expensesExceedIncome: Boolean get() = reserved > cashEstimate.coerceAtLeast(0) + pendingIncome
}

data class MonthSummary(
    val month: YearMonth,
    val salaryExpected: Long,
    val otherIncomeExpected: Long,
    val incomeReceived: Long,
    val incomePending: Long,
    val billsExpected: Long,
    val billsPaid: Long,
    val billsPending: Long,
    val installmentsDue: Long,
    val installmentsPaid: Long,
    val installmentsPending: Long,
    /** Total efetivamente pago no mês (por data do pagamento): contas + dívidas. */
    val totalPaidInMonth: Long,
    val debtOutstandingTotal: Long,
    val overdueTotal: Long,
    val overdueCount: Int,
    val debtProgress: Float,
    val budget: BudgetResult,
    val upcoming: List<DueItem>,
    val hasAnyData: Boolean
)

data class MonthFlow(val month: YearMonth, val income: Long, val expenses: Long, val net: Long, val cumulative: Long)

data class CategoryTotal(val category: String, val expected: Long, val paid: Long)

// ------------------------------- Motor -------------------------------

class FinanceEngine(private val data: FinanceData, private val today: LocalDate) {

    private val paymentsByTarget: Map<Pair<PaymentTarget, Long>, List<Payment>> =
        data.payments.groupBy { it.target to it.targetId }

    private val activePayments = data.payments.filter { !it.reversed }
    private val templatesById: Map<Long, BillTemplate> = data.billTemplates.associateBy { it.id }
    private val sourcesById: Map<Long, IncomeSource> = data.incomeSources.associateBy { it.id }
    private val debtsById: Map<Long, Debt> = data.debts.associateBy { it.id }

    val debtSummaries: List<DebtSummary> by lazy { data.debts.map { summarize(it) } }
    private val summariesById by lazy { debtSummaries.associateBy { it.debt.id } }

    fun summaryOf(debtId: Long): DebtSummary? = summariesById[debtId]

    fun paymentsOf(target: PaymentTarget, id: Long): List<Payment> =
        paymentsByTarget[target to id].orEmpty().sortedByDescending { it.date }

    // ---------------- Dívidas ----------------

    private fun summarize(debt: Debt): DebtSummary {
        val views = data.installments.filter { it.debtId == debt.id }.sortedBy { it.number }.map { inst ->
            val ps = paymentsByTarget[PaymentTarget.INSTALLMENT to inst.id].orEmpty()
            InstallmentView(inst, Settlements.of(inst.amount, ps, inst.dueDate, today), ps)
        }
        val agreements = paymentsByTarget[PaymentTarget.DEBT_AGREEMENT to debt.id].orEmpty()
        val activeAgreements = agreements.filter { !it.reversed }
        val planned = views.sumOf { it.installment.amount }
        val charges = views.sumOf { it.settlement.charges } + activeAgreements.sumOf { it.charges }
        val discounts = views.sumOf { it.settlement.discount } + activeAgreements.sumOf { it.discount }
        val paid = views.sumOf { it.settlement.paid } + activeAgreements.sumOf { it.amount }
        val isOpen = debt.status == DebtStatus.ATIVA
        val outstanding = if (isOpen) views.sumOf { it.settlement.remaining } else 0L
        val overdue = if (isOpen) views.filter { it.settlement.status == ItemStatus.VENCIDA }.sumOf { it.settlement.remaining } else 0L
        val paidCount = views.count { it.settlement.isSettled }
        val remainingCount = if (isOpen) views.count { !it.settlement.isSettled } else 0
        val owedTotal = views.sumOf { it.settlement.owed }
        val progress = when {
            debt.status == DebtStatus.QUITADA -> 1f
            owedTotal <= 0 -> 0f
            else -> br.com.meucontrole.financeiro.domain.model.Money.ratio(owedTotal - outstanding, owedTotal)
        }
        val pending = if (isOpen) views.filter { !it.settlement.isSettled } else emptyList()
        return DebtSummary(
            debt = debt,
            installments = views,
            installmentsPlanned = planned,
            totalForecast = debt.downPayment + planned + charges - discounts,
            chargesCharged = charges,
            discountsGranted = discounts,
            totalPaid = paid,
            outstanding = outstanding,
            overdue = overdue,
            paidCount = paidCount,
            remainingCount = remainingCount,
            progress = progress,
            nextDue = pending.minByOrNull { it.installment.dueDate },
            forecastPayoff = pending.maxOfOrNull { it.installment.dueDate },
            agreementPayments = agreements
        )
    }

    val totalOutstanding: Long by lazy { debtSummaries.sumOf { it.outstanding } }

    /** Progresso geral: quanto do total devido (dívidas ativas e quitadas) já foi pago. */
    val overallDebtProgress: Float by lazy {
        val relevant = debtSummaries.filter { it.debt.status != DebtStatus.RENEGOCIADA }
        val owed = relevant.sumOf { s -> if (s.debt.status == DebtStatus.QUITADA) s.totalPaid else s.installments.sumOf { it.settlement.owed } }
        val out = relevant.sumOf { it.outstanding }
        if (owed <= 0) 0f else br.com.meucontrole.financeiro.domain.model.Money.ratio(owed - out, owed)
    }

    // ---------------- Itens a pagar / receber ----------------

    private fun billSettlement(o: BillOccurrence): Settlement =
        Settlements.of(o.expectedAmount, paymentsByTarget[PaymentTarget.BILL to o.id].orEmpty(), o.dueDate, today)

    fun billItem(o: BillOccurrence): DueItem {
        val t = templatesById[o.templateId]
        val s = billSettlement(o)
        return DueItem(
            kind = DueKind.BILL, refId = o.id, title = t?.name ?: "Conta",
            subtitle = t?.category ?: "", date = o.dueDate, expected = s.owed, done = s.paid,
            remaining = s.remaining, status = s.status, category = t?.category ?: "Outras despesas"
        )
    }

    private fun installmentItem(v: InstallmentView, debt: Debt): DueItem = DueItem(
        kind = DueKind.INSTALLMENT, refId = v.installment.id, title = debt.name,
        subtitle = "Parcela ${v.installment.label}" + if (debt.creditor.isNotBlank()) " • ${debt.creditor}" else "",
        date = v.installment.dueDate, expected = v.settlement.owed, done = v.settlement.paid,
        remaining = if (debt.status == DebtStatus.ATIVA) v.settlement.remaining else 0,
        status = if (debt.status == DebtStatus.ATIVA) v.settlement.status else ItemStatus.PAGA,
        category = debt.category
    )

    fun incomeItem(o: IncomeOccurrence): DueItem {
        val s = sourcesById[o.sourceId]
        val received = o.receivedAmount ?: 0
        val status = when {
            o.isReceived -> ItemStatus.PAGA
            o.date.isBefore(today) -> ItemStatus.VENCIDA
            else -> ItemStatus.PENDENTE
        }
        return DueItem(
            kind = DueKind.INCOME, refId = o.id, title = s?.name ?: "Receita",
            subtitle = s?.kind?.label ?: "", date = o.date, expected = o.expectedAmount, done = received,
            remaining = if (o.isReceived) 0 else o.expectedAmount, status = status, category = s?.kind?.label ?: ""
        )
    }

    private val visibleBills: List<BillOccurrence> by lazy { data.billOccurrences.filter { !it.skipped } }

    /** Parcelas que interessam ao calendário: dívidas ativas, ou parcelas com pagamentos em dívidas encerradas. */
    private val visibleInstallments: List<Pair<InstallmentView, Debt>> by lazy {
        debtSummaries.flatMap { s ->
            s.installments.filter { s.debt.status == DebtStatus.ATIVA || it.settlement.paid > 0 }.map { it to s.debt }
        }
    }

    fun itemsBetween(from: LocalDate, to: LocalDate, includeIncome: Boolean = true): List<DueItem> {
        val bills = visibleBills.filter { !it.dueDate.isBefore(from) && !it.dueDate.isAfter(to) }.map { billItem(it) }
        val inst = visibleInstallments.filter { (v, _) -> !v.installment.dueDate.isBefore(from) && !v.installment.dueDate.isAfter(to) }
            .map { (v, d) -> installmentItem(v, d) }
        val inc = if (includeIncome) data.incomeOccurrences.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.map { incomeItem(it) } else emptyList()
        return (bills + inst + inc).sortedWith(compareBy({ it.date }, { it.kind.ordinal }, { it.title }))
    }

    fun itemsForMonth(month: YearMonth): List<DueItem> = itemsBetween(month.atDay(1), month.atEndOfMonth())

    /** Contas e parcelas vencidas e não pagas (todas, independente do mês). */
    val overdueItems: List<DueItem> by lazy {
        val bills = visibleBills.map { billItem(it) }.filter { it.status == ItemStatus.VENCIDA }
        val inst = visibleInstallments.filter { it.second.status == DebtStatus.ATIVA }
            .map { (v, d) -> installmentItem(v, d) }.filter { it.status == ItemStatus.VENCIDA }
        (bills + inst).sortedBy { it.date }
    }

    fun upcoming(days: Long = 30, limit: Int = 8): List<DueItem> =
        itemsBetween(today, today.plusDays(days), includeIncome = true)
            .filter { it.status != ItemStatus.PAGA }
            .take(limit)

    // ---------------- Orçamento ----------------

    private fun paymentsBetween(from: LocalDate, to: LocalDate) =
        activePayments.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }

    private fun receivedBetween(from: LocalDate, to: LocalDate): Long =
        data.incomeOccurrences.filter { it.isReceived }
            .filter { val d = it.receivedDate ?: it.date; !d.isBefore(from) && !d.isAfter(to) }
            .sumOf { it.receivedAmount ?: 0 }

    /**
     * "Quanto posso gastar?" para o período [start, end].
     * - Realizado: só dinheiro que entrou/saiu de fato (nunca soma previsões).
     * - Projetado: caixa estimado + receitas ainda previstas no período - compromissos pendentes
     *   (inclui vencidos não pagos). Despesas pagas não são subtraídas novamente porque contam
     *   apenas pelo "restante" de cada item.
     */
    fun budget(start: LocalDate, end: LocalDate): BudgetResult {
        val settings = data.settings
        val received = receivedBetween(start, end)
        val paid = paymentsBetween(start, end).sumOf { it.amount }

        val cashFrom = settings.openingDate ?: LocalDate.MIN
        val cashTo = if (today.isAfter(end)) end else today
        val cash = settings.openingBalance +
            receivedBetween(cashFrom, cashTo) -
            paymentsBetween(cashFrom, cashTo).sumOf { it.amount }

        val pendingIncome = data.incomeOccurrences
            .filter { !it.isReceived && !it.date.isAfter(end) && !it.date.isBefore(maxOf(start, today)) }
            .sumOf { it.expectedAmount }
        val pendingBills = visibleBills.filter { !it.dueDate.isAfter(end) }
            .sumOf { billSettlement(it).remaining }
        val pendingInst = debtSummaries.filter { it.debt.status == DebtStatus.ATIVA }
            .flatMap { it.installments }
            .filter { !it.installment.dueDate.isAfter(end) }
            .sumOf { it.settlement.remaining }

        return BudgetResult(
            periodStart = start,
            periodEnd = end,
            openingBalance = settings.openingBalance,
            openingDate = settings.openingDate,
            receivedIncome = received,
            pendingIncome = pendingIncome,
            paidExpenses = paid,
            pendingBills = pendingBills,
            pendingInstallments = pendingInst,
            realizedPeriod = received - paid,
            cashEstimate = cash,
            projected = cash + pendingIncome - pendingBills - pendingInst
        )
    }

    // ---------------- Mês (dashboard) ----------------

    fun monthSummary(month: YearMonth): MonthSummary {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        val incomes = data.incomeOccurrences.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        val salary = incomes.filter { sourcesById[it.sourceId]?.kind == IncomeKind.SALARIO }.sumOf { it.expectedAmount }
        val other = incomes.filter { sourcesById[it.sourceId]?.kind != IncomeKind.SALARIO }.sumOf { it.expectedAmount }
        val received = receivedBetween(start, end)
        val incomePending = incomes.filter { !it.isReceived }.sumOf { it.expectedAmount }

        val bills = visibleBills.filter { !it.dueDate.isBefore(start) && !it.dueDate.isAfter(end) }.map { billSettlement(it) }
        val inst = debtSummaries.filter { it.debt.status == DebtStatus.ATIVA }
            .flatMap { it.installments }
            .filter { !it.installment.dueDate.isBefore(start) && !it.installment.dueDate.isAfter(end) }

        val monthPayments = paymentsBetween(start, end)
        val billsPaid = monthPayments.filter { it.target == PaymentTarget.BILL }.sumOf { it.amount }
        val debtPaid = monthPayments.filter { it.target != PaymentTarget.BILL }.sumOf { it.amount }

        return MonthSummary(
            month = month,
            salaryExpected = salary,
            otherIncomeExpected = other,
            incomeReceived = received,
            incomePending = incomePending,
            billsExpected = bills.sumOf { it.owed },
            billsPaid = billsPaid,
            billsPending = bills.sumOf { it.remaining },
            installmentsDue = inst.sumOf { it.settlement.owed },
            installmentsPaid = debtPaid,
            installmentsPending = inst.sumOf { it.settlement.remaining },
            totalPaidInMonth = billsPaid + debtPaid,
            debtOutstandingTotal = totalOutstanding,
            overdueTotal = overdueItems.sumOf { it.remaining },
            overdueCount = overdueItems.size,
            debtProgress = overallDebtProgress,
            budget = budget(start, end),
            upcoming = upcoming(),
            hasAnyData = !data.isEmpty
        )
    }

    /** Fluxo realizado (recebido x pago) mês a mês, com saldo acumulado a partir do saldo inicial. */
    fun history(months: List<YearMonth>): List<MonthFlow> {
        val opening = data.settings.openingBalance
        val openingDate = data.settings.openingDate
        // Acumulado antes do primeiro mês exibido
        var cumulative = opening
        months.firstOrNull()?.let { first ->
            val before = first.atDay(1).minusDays(1)
            val from = openingDate ?: LocalDate.MIN
            if (!before.isBefore(from)) {
                cumulative += receivedBetween(from, before) - paymentsBetween(from, before).sumOf { it.amount }
            }
        }
        return months.map { m ->
            val from = maxOf(m.atDay(1), openingDate ?: LocalDate.MIN)
            val inc = receivedBetween(m.atDay(1), m.atEndOfMonth())
            val exp = paymentsBetween(m.atDay(1), m.atEndOfMonth()).sumOf { it.amount }
            // Para o acumulado, só conta o que ocorreu a partir da data do saldo inicial
            val incCash = if (from.isAfter(m.atEndOfMonth())) 0 else receivedBetween(from, m.atEndOfMonth())
            val expCash = if (from.isAfter(m.atEndOfMonth())) 0 else paymentsBetween(from, m.atEndOfMonth()).sumOf { it.amount }
            cumulative += incCash - expCash
            MonthFlow(m, inc, exp, inc - exp, cumulative)
        }
    }

    /** Despesas do mês por categoria (previsto x pago). Contas e dívidas ficam em grupos separados. */
    fun categoriesForMonth(month: YearMonth): List<CategoryTotal> {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        val map = linkedMapOf<String, Pair<Long, Long>>()
        fun add(cat: String, exp: Long, paid: Long) {
            val cur = map[cat] ?: (0L to 0L)
            map[cat] = (cur.first + exp) to (cur.second + paid)
        }
        visibleBills.filter { !it.dueDate.isBefore(start) && !it.dueDate.isAfter(end) }.forEach {
            add(templatesById[it.templateId]?.category ?: "Outras despesas", billSettlement(it).owed, 0)
        }
        debtSummaries.filter { it.debt.status == DebtStatus.ATIVA }.forEach { s ->
            s.installments.filter { !it.installment.dueDate.isBefore(start) && !it.installment.dueDate.isAfter(end) }
                .forEach { add("Dívida: ${s.debt.category}", it.settlement.owed, 0) }
        }
        paymentsBetween(start, end).forEach { p ->
            val cat = when (p.target) {
                PaymentTarget.BILL -> data.billOccurrences.firstOrNull { it.id == p.targetId }
                    ?.let { templatesById[it.templateId]?.category } ?: "Outras despesas"
                else -> "Dívida: " + (debtsById[p.debtId]?.category ?: "Outros")
            }
            add(cat, 0, p.amount)
        }
        return map.map { (k, v) -> CategoryTotal(k, v.first, v.second) }.sortedByDescending { maxOf(it.expected, it.paid) }
    }
}
