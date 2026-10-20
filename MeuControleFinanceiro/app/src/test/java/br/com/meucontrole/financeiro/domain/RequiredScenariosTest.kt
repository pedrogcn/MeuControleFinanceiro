package br.com.meucontrole.financeiro.domain

import br.com.meucontrole.financeiro.backup.BackupCodec
import br.com.meucontrole.financeiro.backup.BackupStore
import br.com.meucontrole.financeiro.backup.RestoreBackupUseCase
import br.com.meucontrole.financeiro.domain.Fixtures.TODAY
import br.com.meucontrole.financeiro.domain.Fixtures.addDebt
import br.com.meucontrole.financeiro.domain.Fixtures.draft
import br.com.meucontrole.financeiro.domain.Fixtures.installmentReq
import br.com.meucontrole.financeiro.domain.Fixtures.installmentsOf
import br.com.meucontrole.financeiro.domain.Fixtures.pay
import br.com.meucontrole.financeiro.domain.calc.FinanceEngine
import br.com.meucontrole.financeiro.domain.calc.ItemStatus
import br.com.meucontrole.financeiro.domain.calc.RecurrenceEngine
import br.com.meucontrole.financeiro.domain.model.AppSettings
import br.com.meucontrole.financeiro.domain.model.BillOccurrence
import br.com.meucontrole.financeiro.domain.model.BillTemplate
import br.com.meucontrole.financeiro.domain.model.DebtStatus
import br.com.meucontrole.financeiro.domain.model.FinanceData
import br.com.meucontrole.financeiro.domain.model.IncomeFrequency
import br.com.meucontrole.financeiro.domain.model.IncomeKind
import br.com.meucontrole.financeiro.domain.model.IncomeOccurrence
import br.com.meucontrole.financeiro.domain.model.IncomeSource
import br.com.meucontrole.financeiro.domain.model.Payment
import br.com.meucontrole.financeiro.domain.model.PaymentTarget
import br.com.meucontrole.financeiro.domain.model.Recurrence
import br.com.meucontrole.financeiro.domain.usecase.DebtFactory
import br.com.meucontrole.financeiro.domain.usecase.Outcome
import br.com.meucontrole.financeiro.domain.usecase.PaymentEditRules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Os 12 cenários exigidos na especificação. Valores em centavos. */
class RequiredScenariosTest {

    // 1
    @Test
    fun divida1200em12_apos3pagamentos_saldo900() {
        var (data, debtId) = addDebt(FinanceData(), draft(total = 120_000, count = 12))
        val inst = installmentsOf(data, debtId)
        assertTrue(inst.all { it.amount == 10_000L })
        inst.take(3).forEach { data = pay(data, installmentReq(it.id, 10_000)) }
        val s = FinanceEngine(data, TODAY).summaryOf(debtId)!!
        assertEquals(90_000L, s.outstanding)
        assertEquals(30_000L, s.totalPaid)
        assertEquals(3, s.paidCount)
        assertEquals(9, s.remainingCount)
    }

    // 2
    @Test
    fun divida500_pagamentoParcial150_saldo350_semEncargos() {
        var (data, debtId) = addDebt(FinanceData(), draft(total = 50_000, count = 1))
        val inst = installmentsOf(data, debtId).single()
        data = pay(data, installmentReq(inst.id, 15_000))
        val s = FinanceEngine(data, TODAY).summaryOf(debtId)!!
        assertEquals(35_000L, s.outstanding)
        assertEquals(0L, s.chargesCharged)
        assertEquals(DebtStatus.ATIVA, data.debts.single().status)
    }

    // 3
    @Test
    fun divida1000_entrada200_saldoAParcelar800_semContarEntradaDuasVezes() {
        val (data, debtId) = addDebt(FinanceData(), draft(total = 100_000, count = 4, down = 20_000))
        val debt = data.debts.single()
        assertEquals(80_000L, debt.amountToFinance)
        assertEquals(80_000L, installmentsOf(data, debtId).sumOf { it.amount })
        val s = FinanceEngine(data, TODAY).summaryOf(debtId)!!
        assertEquals(80_000L, s.outstanding)
        assertEquals(0L, s.totalPaid) // a entrada não é contada como pagamento de parcela
        assertEquals(100_000L, s.totalForecast) // entrada + parcelas = total contratado
    }

    // 4
    @Test
    fun parcela100_pagamento40_restante60() {
        var (data, debtId) = addDebt(FinanceData(), draft(total = 30_000, count = 3, first = LocalDate.of(2026, 12, 10)))
        val first = installmentsOf(data, debtId).first()
        data = pay(data, installmentReq(first.id, 4_000))
        val v = FinanceEngine(data, TODAY).summaryOf(debtId)!!.installments.first()
        assertEquals(6_000L, v.settlement.remaining)
        assertEquals(ItemStatus.PARCIAL, v.settlement.status)
    }

    // 5
    @Test
    fun parcelaPaga_naoSomaNoSaldoEmAberto_eDividaQuitadaSai() {
        var (data, debtId) = addDebt(FinanceData(), draft(total = 20_000, count = 2))
        val inst = installmentsOf(data, debtId)
        data = pay(data, installmentReq(inst[0].id, 10_000))
        val engine1 = FinanceEngine(data, TODAY)
        assertEquals(10_000L, engine1.totalOutstanding)
        assertEquals(ItemStatus.PAGA, engine1.summaryOf(debtId)!!.installments[0].settlement.status)
        data = pay(data, installmentReq(inst[1].id, 10_000))
        assertEquals(DebtStatus.QUITADA, data.debts.single().status)
        assertEquals(0L, FinanceEngine(data, TODAY).totalOutstanding)
        assertTrue(FinanceEngine(data, TODAY).overdueItems.isEmpty())
    }

    // 6
    @Test
    fun despesaPaga_naoESubtraidaNovamenteDoProjetado() {
        val tpl = BillTemplate(id = 1, name = "Energia", category = "Energia", expectedAmount = 20_000,
            recurrence = Recurrence.NENHUMA, startDate = LocalDate.of(2026, 10, 25))
        val occ = BillOccurrence(id = 1, templateId = 1, dueDate = LocalDate.of(2026, 10, 25), expectedAmount = 20_000)
        val base = FinanceData(
            billTemplates = listOf(tpl), billOccurrences = listOf(occ),
            settings = AppSettings(openingBalance = 100_000, openingDate = LocalDate.of(2026, 10, 1))
        )
        val month = YearMonth.of(2026, 10)
        val before = FinanceEngine(base, TODAY).budget(month.atDay(1), month.atEndOfMonth())
        assertEquals(80_000L, before.projected) // 1000 - 200 pendente

        val paid = base.copy(payments = listOf(Payment(id = 1, requestId = "r1", target = PaymentTarget.BILL, targetId = 1,
            amount = 20_000, date = TODAY)))
        val after = FinanceEngine(paid, TODAY).budget(month.atDay(1), month.atEndOfMonth())
        assertEquals(80_000L, after.cashEstimate) // saiu do caixa uma vez
        assertEquals(0L, after.pendingBills)
        assertEquals(80_000L, after.projected) // e NÃO foi subtraída de novo
    }

    // 7
    @Test
    fun salarioBrutoELiquido_naoSaoSomados() {
        val src = IncomeSource(id = 1, name = "Empresa", kind = IncomeKind.SALARIO, grossAmount = 400_000, netAmount = 320_000,
            frequency = IncomeFrequency.MENSAL, startDate = LocalDate.of(2026, 10, 5))
        val occs = RecurrenceEngine.missingIncomeOccurrences(listOf(src), emptyList(), LocalDate.of(2026, 10, 31))
            .mapIndexed { i, o -> o.copy(id = i + 1L) }
        val data = FinanceData(incomeSources = listOf(src), incomeOccurrences = occs)
        val m = FinanceEngine(data, TODAY).monthSummary(YearMonth.of(2026, 10))
        assertEquals(1, occs.size)
        assertEquals(320_000L, m.salaryExpected)
        assertEquals(0L, m.otherIncomeExpected)
        assertEquals(320_000L, occs.single().expectedAmount)
    }

    // 8
    @Test
    fun contasRecorrentes_naoDuplicamOcorrencias() {
        val tpl = BillTemplate(id = 7, name = "Aluguel", category = "Aluguel", expectedAmount = 90_000,
            recurrence = Recurrence.MENSAL, startDate = LocalDate.of(2026, 1, 31))
        val horizon = LocalDate.of(2026, 12, 31)
        val first = RecurrenceEngine.missingBillOccurrences(listOf(tpl), emptyList(), horizon)
        assertEquals(12, first.size)
        // Fevereiro tem 28 dias em 2026
        assertEquals(LocalDate.of(2026, 2, 28), first[1].dueDate)
        assertEquals(LocalDate.of(2026, 4, 30), first[3].dueDate)
        // Simula abrir o app várias vezes: nada novo é gerado
        val again = RecurrenceEngine.missingBillOccurrences(listOf(tpl), first, horizon)
        assertTrue(again.isEmpty())
        val third = RecurrenceEngine.missingBillOccurrences(listOf(tpl), first + again, horizon)
        assertTrue(third.isEmpty())
    }

    // 9
    @Test
    fun backupInvalido_naoCorrompeDadosExistentes() = runBlocking {
        val (existing, _) = addDebt(FinanceData(), draft(total = 10_000, count = 1))
        val store = FakeStore(existing)
        val useCase = RestoreBackupUseCase(store)

        val invalids = listOf(
            "isto não é json",
            "{\"format\":\"outro-app\",\"version\":1}",
            BackupCodec.encode(existing, 1L).replace("\"amount\":10000", "\"amount\":99999"), // checksum quebra
            "{\"format\":\"meu-controle-financeiro\",\"version\":99,\"data\":{}}"
        )
        for (text in invalids) {
            val r = useCase.preview(text)
            assertTrue("Deveria ser inválido: $text", r is RestoreBackupUseCase.PreviewResult.Invalid)
        }
        assertEquals(0, store.writes)
        assertEquals(existing, store.data)
    }

    // 10
    @Test
    fun estorno_recalculaSaldos() {
        var (data, debtId) = addDebt(FinanceData(), draft(total = 20_000, count = 2))
        val inst = installmentsOf(data, debtId)
        data = pay(data, installmentReq(inst[0].id, 10_000))
        data = pay(data, installmentReq(inst[1].id, 10_000))
        assertEquals(DebtStatus.QUITADA, data.debts.single().status)

        val p = data.payments.last()
        val reversed = (PaymentEditRules.reverse(p, "Lançado em duplicidade", 1) as Outcome.Ok).value
        data = Fixtures.recomputeStatuses(data.copy(payments = data.payments.map { if (it.id == p.id) reversed else it }))

        val s = FinanceEngine(data, TODAY).summaryOf(debtId)!!
        assertEquals(DebtStatus.ATIVA, data.debts.single().status)
        assertEquals(10_000L, s.outstanding)
        assertEquals(10_000L, s.totalPaid)
        assertEquals(2, data.payments.size) // o estorno fica no histórico
        assertTrue(data.payments.last().reversed)
        assertEquals("Lançado em duplicidade", data.payments.last().reversalReason)
    }

    // 11
    @Test
    fun renegociacao_preservaHistoricoAnterior() {
        var (data, oldId) = addDebt(FinanceData(), draft(total = 60_000, count = 6, name = "Cartão"))
        val inst = installmentsOf(data, oldId)
        data = pay(data, installmentReq(inst[0].id, 10_000))
        data = pay(data, installmentReq(inst[1].id, 10_000))
        val paymentsBefore = data.payments

        // Renegocia: antiga vira RENEGOCIADA; nova dívida com saldo acordado de 350,00 em 5x
        data = data.copy(debts = data.debts.map { if (it.id == oldId) it.copy(status = DebtStatus.RENEGOCIADA) else it })
        val (withNew, newId) = addDebt(data, draft(total = 35_000, count = 5, name = "Cartão (acordo)").copy(renegotiatedFromId = oldId))
        data = Fixtures.recomputeStatuses(withNew)

        val engine = FinanceEngine(data, TODAY)
        val old = engine.summaryOf(oldId)!!
        val new = engine.summaryOf(newId)!!
        assertEquals(DebtStatus.RENEGOCIADA, data.debts.first { it.id == oldId }.status)
        assertEquals(6, old.installments.size)           // parcelas antigas preservadas
        assertEquals(20_000L, old.totalPaid)             // pagamentos antigos preservados
        assertEquals(paymentsBefore, data.payments)      // nenhum pagamento apagado
        assertEquals(0L, old.outstanding)                // não conta mais como saldo
        assertEquals(35_000L, new.outstanding)
        assertEquals(35_000L, engine.totalOutstanding)   // sem dupla contagem
        assertEquals(oldId, new.debt.renegotiatedFromId)
    }

    // 12
    @Test
    fun dashboard_refleteLancamentosImediatamente() {
        var (data, debtId) = addDebt(FinanceData(), draft(total = 30_000, count = 3, first = LocalDate.of(2026, 10, 10)))
        val month = YearMonth.of(2026, 10)
        val before = FinanceEngine(data, TODAY).monthSummary(month)
        assertEquals(10_000L, before.installmentsPending)
        assertEquals(0L, before.totalPaidInMonth)
        assertEquals(30_000L, before.debtOutstandingTotal)
        assertEquals(1, before.overdueCount)

        data = pay(data, installmentReq(installmentsOf(data, debtId).first().id, 10_000))
        val after = FinanceEngine(data, TODAY).monthSummary(month)
        assertEquals(0L, after.installmentsPending)
        assertEquals(10_000L, after.totalPaidInMonth)
        assertEquals(20_000L, after.debtOutstandingTotal)
        assertEquals(0, after.overdueCount)
    }

    private class FakeStore(var data: FinanceData) : BackupStore {
        var writes = 0
        override suspend fun snapshot() = data
        override suspend fun replaceAll(data: FinanceData) { writes++; this.data = data }
        override suspend fun insertMerged(data: FinanceData) { writes++ }
    }

    @Test
    fun fabricaRejeitaEntradaMaiorQueTotal() {
        val r = DebtFactory.build(draft(total = 10_000, count = 2, down = 20_000), 0)
        assertTrue(r is Outcome.Error)
        assertFalse(r is Outcome.Ok)
    }

    @Test
    fun incomeOccurrenceTypeUnused() {
        // Garante que receitas não recebidas não entram como dinheiro em caixa
        val src = IncomeSource(id = 1, name = "Freela", kind = IncomeKind.VARIAVEL, netAmount = 50_000,
            frequency = IncomeFrequency.EVENTUAL, startDate = LocalDate.of(2026, 10, 25))
        val occ = IncomeOccurrence(id = 1, sourceId = 1, date = LocalDate.of(2026, 10, 25), expectedAmount = 50_000)
        val data = FinanceData(incomeSources = listOf(src), incomeOccurrences = listOf(occ))
        val b = FinanceEngine(data, TODAY).budget(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertEquals(0L, b.cashEstimate)
        assertEquals(0L, b.receivedIncome)
        assertEquals(50_000L, b.pendingIncome)
        assertEquals(50_000L, b.projected)
    }
}
