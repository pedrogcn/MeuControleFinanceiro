package br.com.meucontrole.financeiro.domain

import br.com.meucontrole.financeiro.backup.BackupCodec
import br.com.meucontrole.financeiro.backup.BackupMerger
import br.com.meucontrole.financeiro.domain.Fixtures.TODAY
import br.com.meucontrole.financeiro.domain.Fixtures.addDebt
import br.com.meucontrole.financeiro.domain.Fixtures.draft
import br.com.meucontrole.financeiro.domain.Fixtures.installmentReq
import br.com.meucontrole.financeiro.domain.Fixtures.installmentsOf
import br.com.meucontrole.financeiro.domain.Fixtures.pay
import br.com.meucontrole.financeiro.domain.calc.FinanceEngine
import br.com.meucontrole.financeiro.domain.calc.InstallmentPlanner
import br.com.meucontrole.financeiro.domain.calc.RecurrenceEngine
import br.com.meucontrole.financeiro.domain.calc.ReportBuilder
import br.com.meucontrole.financeiro.domain.model.AppSettings
import br.com.meucontrole.financeiro.domain.model.BillTemplate
import br.com.meucontrole.financeiro.domain.model.DebtStatus
import br.com.meucontrole.financeiro.domain.model.FinanceData
import br.com.meucontrole.financeiro.domain.model.IncomeFrequency
import br.com.meucontrole.financeiro.domain.model.IncomeKind
import br.com.meucontrole.financeiro.domain.model.IncomeSource
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.domain.model.PaymentTarget
import br.com.meucontrole.financeiro.domain.model.Recurrence
import br.com.meucontrole.financeiro.domain.usecase.DebtDraft
import br.com.meucontrole.financeiro.domain.usecase.DebtEntryMode
import br.com.meucontrole.financeiro.domain.usecase.DebtFactory
import br.com.meucontrole.financeiro.domain.usecase.ManualInstallment
import br.com.meucontrole.financeiro.domain.usecase.OverpayMode
import br.com.meucontrole.financeiro.domain.usecase.Outcome
import br.com.meucontrole.financeiro.domain.usecase.PaymentEditRules
import br.com.meucontrole.financeiro.domain.usecase.PaymentPlan
import br.com.meucontrole.financeiro.domain.usecase.PaymentPlanner
import br.com.meucontrole.financeiro.domain.usecase.PaymentRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalculationsTest {

    @Test
    fun money_formatEParse() {
        assertEquals("R$ 1.234,56", Money.format(123_456))
        assertEquals("R$ 0,05", Money.format(5))
        assertEquals("-R$ 1.000.000,00", Money.format(-100_000_000))
        assertEquals(123_456L, Money.parse("1.234,56"))
        assertEquals(123_450L, Money.parse("1234,5"))
        assertEquals(1_000L, Money.parse("R$ 10,00"))
        assertEquals(1_050L, Money.parse("10.50"))
        assertEquals(123_400L, Money.parse("1.234"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("1,234"))
        assertNull(Money.parse("1,2,3"))
    }

    @Test
    fun divisao_centavosNaUltimaParcela() {
        assertEquals(listOf(3_333L, 3_333L, 3_334L), InstallmentPlanner.splitEvenly(10_000, 3))
        assertEquals(10_000L, InstallmentPlanner.splitEvenly(10_000, 7).sum())
        assertEquals(listOf(10_000L), InstallmentPlanner.splitEvenly(10_000, 1))
    }

    @Test
    fun vencimentos_dia31_ajustaMesesCurtos() {
        val dates = InstallmentPlanner.schedule(LocalDate.of(2027, 1, 31), 4, 31)
        assertEquals(listOf(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30)), dates)
        val leap = InstallmentPlanner.schedule(LocalDate.of(2028, 1, 30), 2, 30)
        assertEquals(LocalDate.of(2028, 2, 29), leap[1])
    }

    @Test
    fun parcelasManuais_comValoresDiferentes() {
        val d = DebtDraft(
            name = "Loja", creditor = "", category = "Crediário", originalAmount = 60_000, downPayment = 5_000,
            contractDate = LocalDate.of(2026, 9, 1), totalContracted = 0, mode = DebtEntryMode.MANUAL, installmentCount = 0,
            firstDueDate = LocalDate.of(2026, 10, 1), dueDay = 1,
            manualInstallments = listOf(
                ManualInstallment(30_000, LocalDate.of(2026, 12, 1)),
                ManualInstallment(10_000, LocalDate.of(2026, 10, 1)),
                ManualInstallment(20_000, LocalDate.of(2026, 11, 1))
            )
        )
        val (debt, inst) = (DebtFactory.build(d, 0) as Outcome.Ok).value
        assertEquals(65_000L, debt.totalContracted)
        assertEquals(listOf(10_000L, 20_000L, 30_000L), inst.map { it.amount })
        assertEquals(listOf("1/3", "2/3", "3/3"), inst.map { it.label })
    }

    @Test
    fun pagamentoDuplicado_mesmoRequestId_eRecusado() {
        var (data, debtId) = addDebt(FinanceData(), draft(total = 20_000, count = 2))
        val req = installmentReq(installmentsOf(data, debtId)[0].id, 5_000, requestId = "abc")
        data = pay(data, req)
        val again = PaymentPlanner.plan(req, data, TODAY, 0)
        assertTrue(again is PaymentPlan.Invalid)
        assertEquals(1, data.payments.size)
    }

    @Test
    fun pagamentoAcimaDaParcela_pedeDecisao_eAbateNasProximas() {
        var (data, debtId) = addDebt(FinanceData(), draft(total = 30_000, count = 3))
        val inst = installmentsOf(data, debtId)
        val req = installmentReq(inst[0].id, 15_000)
        val ask = PaymentPlanner.plan(req, data, TODAY, 0)
        assertTrue(ask is PaymentPlan.NeedsDecision)
        ask as PaymentPlan.NeedsDecision
        assertEquals(5_000L, ask.excess)
        assertEquals(0L, ask.excessBeyondDebt)
        assertTrue(ask.canApplyToNext)

        data = pay(data, req.copy(overpayMode = OverpayMode.APPLY_TO_NEXT))
        val s = FinanceEngine(data, TODAY).summaryOf(debtId)!!
        assertEquals(15_000L, s.outstanding)
        assertEquals(0L, s.installments[0].settlement.remaining)
        assertEquals(5_000L, s.installments[1].settlement.remaining)
        assertEquals(0L, s.installments.sumOf { it.settlement.excess }) // sem saldo negativo escondido
    }

    @Test
    fun pagamentoAcimaDaDividaInteira_naoGeraSaldoNegativoSilencioso() {
        val (data, debtId) = addDebt(FinanceData(), draft(total = 20_000, count = 2))
        val inst = installmentsOf(data, debtId)
        val req = installmentReq(inst[0].id, 25_000)
        val ask = PaymentPlanner.plan(req, data, TODAY, 0) as PaymentPlan.NeedsDecision
        assertEquals(5_000L, ask.excessBeyondDebt)
        // Abater nas próximas não é possível sem decidir o excedente
        assertTrue(PaymentPlanner.plan(req.copy(overpayMode = OverpayMode.APPLY_TO_NEXT), data, TODAY, 0) is PaymentPlan.Invalid)
        // Registrar como encargos: excedente fica explícito
        val charged = pay(data, req.copy(overpayMode = OverpayMode.REGISTER_AS_CHARGES))
        val v = FinanceEngine(charged, TODAY).summaryOf(debtId)!!.installments[0]
        assertEquals(0L, v.settlement.remaining)
        assertEquals(15_000L, v.settlement.charges)
        // Limitar ao saldo
        val limited = pay(data, req.copy(overpayMode = OverpayMode.LIMIT_TO_BALANCE))
        assertEquals(10_000L, limited.payments.single().amount)
    }

    @Test
    fun encargosEDescontosEfetivos() {
        var (data, debtId) = addDebt(FinanceData(), draft(total = 10_000, count = 1))
        val id = installmentsOf(data, debtId).single().id
        data = pay(data, installmentReq(id, 10_500).copy(charges = 1_000, discount = 500))
        val s = FinanceEngine(data, TODAY).summaryOf(debtId)!!
        assertEquals(0L, s.outstanding)
        assertEquals(1_000L, s.chargesCharged)
        assertEquals(500L, s.discountsGranted)
        assertEquals(DebtStatus.QUITADA, data.debts.single().status)
    }

    @Test
    fun quitacaoPorAcordo_descontoExplicito_eEstornoReabre() {
        var (data, debtId) = addDebt(FinanceData(), draft(total = 100_000, count = 10))
        data = pay(data, installmentReq(installmentsOf(data, debtId)[0].id, 10_000))
        data = pay(data, PaymentRequest(requestId = "acordo", target = PaymentTarget.DEBT_AGREEMENT, targetId = debtId, amount = 50_000, date = TODAY))
        val agreement = data.payments.last()
        assertEquals(40_000L, agreement.discount)
        assertEquals(DebtStatus.QUITADA, data.debts.single().status)
        assertEquals(0L, FinanceEngine(data, TODAY).totalOutstanding)
        assertEquals(60_000L, FinanceEngine(data, TODAY).summaryOf(debtId)!!.totalPaid)

        val rev = (PaymentEditRules.reverse(agreement, "Acordo cancelado", 1) as Outcome.Ok).value
        data = Fixtures.recomputeStatuses(data.copy(payments = data.payments.map { if (it.id == rev.id) rev else it }))
        assertEquals(DebtStatus.ATIVA, data.debts.single().status)
        assertEquals(90_000L, FinanceEngine(data, TODAY).totalOutstanding)
    }

    @Test
    fun editarLancamento_recalcula_eBloqueiaExcesso() {
        var (data, debtId) = addDebt(FinanceData(), draft(total = 10_000, count = 1))
        val id = installmentsOf(data, debtId).single().id
        data = pay(data, installmentReq(id, 10_000))
        val p = data.payments.single()
        val fixed = (PaymentEditRules.edit(p, 4_000, 0, 0, TODAY, "Pix", "valor corrigido", data, 1) as Outcome.Ok).value
        data = Fixtures.recomputeStatuses(data.copy(payments = listOf(fixed)))
        assertEquals(6_000L, FinanceEngine(data, TODAY).summaryOf(debtId)!!.outstanding)
        assertEquals(DebtStatus.ATIVA, data.debts.single().status)
        assertTrue(PaymentEditRules.edit(fixed, 12_000, 0, 0, TODAY, "", "", data, 2) is Outcome.Error)
        assertTrue(PaymentEditRules.reverse(fixed.copy(reversed = true), "x", 1) is Outcome.Error)
        assertTrue(PaymentEditRules.reverse(fixed, " ", 1) is Outcome.Error)
    }

    @Test
    fun pagamentoAntecipado_contaComoPago() {
        var (data, debtId) = addDebt(FinanceData(), draft(total = 30_000, count = 3, first = LocalDate.of(2027, 1, 5)))
        val last = installmentsOf(data, debtId).last()
        data = pay(data, installmentReq(last.id, 10_000, date = TODAY))
        assertEquals(20_000L, FinanceEngine(data, TODAY).summaryOf(debtId)!!.outstanding)
    }

    @Test
    fun receitaQuinzenal_eSemanal() {
        val q = IncomeSource(id = 1, name = "Adiantamento", kind = IncomeKind.SALARIO, netAmount = 100_000,
            frequency = IncomeFrequency.QUINZENAL, startDate = LocalDate.of(2026, 2, 15))
        assertEquals(listOf(LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 28)),
            RecurrenceEngine.incomeDates(q, q.startDate, LocalDate.of(2026, 2, 28)))
        val w = q.copy(frequency = IncomeFrequency.SEMANAL, startDate = LocalDate.of(2026, 10, 2))
        assertEquals(5, RecurrenceEngine.incomeDates(w, w.startDate, LocalDate.of(2026, 10, 31)).size)
    }

    @Test
    fun contaAnual_29deFevereiro() {
        val t = BillTemplate(id = 1, name = "IPVA", category = "Transporte", expectedAmount = 1, recurrence = Recurrence.ANUAL,
            startDate = LocalDate.of(2028, 2, 29))
        assertEquals(listOf(LocalDate.of(2028, 2, 29), LocalDate.of(2029, 2, 28)),
            RecurrenceEngine.billDueDates(t, t.startDate, LocalDate.of(2029, 12, 31)))
    }

    @Test
    fun backup_idaEVolta_preservaTudo() {
        var (data, debtId) = addDebt(FinanceData(settings = AppSettings(openingBalance = 12_345, openingDate = TODAY)), draft(total = 30_000, count = 3))
        data = pay(data, installmentReq(installmentsOf(data, debtId)[0].id, 10_000).copy(notes = "Pago com \"aspas\"; e ponto-e-vírgula"))
        val text = BackupCodec.encode(data, 99L)
        val decoded = BackupCodec.decode(text)
        assertTrue(decoded.toString(), decoded is BackupCodec.Result.Success)
        assertEquals(data, (decoded as BackupCodec.Result.Success).data)
    }

    @Test
    fun backup_referenciaQuebrada_eRejeitado() {
        val (data, _) = addDebt(FinanceData(), draft(total = 30_000, count = 3))
        val broken = data.copy(installments = data.installments.map { it.copy(debtId = 999) })
        val r = BackupCodec.decode(BackupCodec.encode(broken, 1))
        assertTrue(r is BackupCodec.Result.Failure)
    }

    @Test
    fun mesclagem_ignoraDuplicados_eRemapeiaIds() {
        var (current, debtId) = addDebt(FinanceData(), draft(total = 30_000, count = 3, name = "A"))
        current = pay(current, installmentReq(installmentsOf(current, debtId)[0].id, 10_000, requestId = "p1"))
        // Backup contém a mesma dívida A e uma nova B (com IDs que colidem)
        var (incoming, bId) = addDebt(current, draft(total = 20_000, count = 2, name = "B"))
        incoming = pay(incoming, installmentReq(installmentsOf(incoming, bId)[0].id, 10_000, requestId = "p2"))
        val m = BackupMerger.merge(current, incoming)
        assertEquals(1, m.toInsert.debts.size)
        assertEquals("B", m.toInsert.debts.single().name)
        assertTrue(m.toInsert.debts.single().id > current.debts.maxOf { it.id })
        assertEquals(2, m.toInsert.installments.size)
        assertEquals(1, m.toInsert.payments.size)
        assertTrue(m.duplicates.any { it.contains("\"A\"") })
        // Após inserir, o resultado é íntegro
        val merged = current.copy(
            debts = current.debts + m.toInsert.debts,
            installments = current.installments + m.toInsert.installments,
            payments = current.payments + m.toInsert.payments
        )
        assertEquals(30_000L, FinanceEngine(merged, TODAY).totalOutstanding)
    }

    @Test
    fun relatorio_usaMesmosNumerosDoDashboard() {
        var (data, debtId) = addDebt(FinanceData(), draft(total = 30_000, count = 3, first = LocalDate.of(2026, 10, 10)))
        data = pay(data, installmentReq(installmentsOf(data, debtId)[0].id, 10_000))
        val month = YearMonth.of(2026, 10)
        val r = ReportBuilder.monthly(data, month, TODAY)
        val dash = FinanceEngine(data, TODAY).monthSummary(month)
        assertEquals(dash, r.summary)
        assertEquals(10_000L, r.history.last().expenses)
        val csv = ReportBuilder.monthlyCsv(r)
        assertTrue(csv.contains("Pago em dívidas;100,00"))
        assertTrue(ReportBuilder.debtsCsv(data, TODAY).contains("1/3"))
    }

    @Test
    fun historico_acumulaAPartirDoSaldoInicial() {
        val src = IncomeSource(id = 1, name = "Salário", kind = IncomeKind.SALARIO, netAmount = 200_000,
            frequency = IncomeFrequency.MENSAL, startDate = LocalDate.of(2026, 9, 5))
        val occ = RecurrenceEngine.missingIncomeOccurrences(listOf(src), emptyList(), LocalDate.of(2026, 10, 31))
            .mapIndexed { i, o -> o.copy(id = i + 1L, receivedAmount = 200_000, receivedDate = o.date) }
        val data = FinanceData(incomeSources = listOf(src), incomeOccurrences = occ,
            settings = AppSettings(openingBalance = 50_000, openingDate = LocalDate.of(2026, 9, 1)))
        val h = FinanceEngine(data, TODAY).history(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10)))
        assertEquals(250_000L, h[0].cumulative)
        assertEquals(450_000L, h[1].cumulative)
    }
}
