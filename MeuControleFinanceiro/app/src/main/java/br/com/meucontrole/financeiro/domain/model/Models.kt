package br.com.meucontrole.financeiro.domain.model

import java.time.LocalDate

// ---------------------------------------------------------------------------
// Modelos de domínio. Independentes de Room/Android para que as regras
// financeiras possam ser testadas isoladamente. Valores em CENTAVOS (Long).
// ---------------------------------------------------------------------------

enum class DebtStatus(val label: String) {
    ATIVA("Ativa"),
    QUITADA("Quitada"),
    RENEGOCIADA("Renegociada")
}

/**
 * Dívida.
 * - originalAmount: valor original da dívida/bem (informativo).
 * - totalContracted: valor total contratado, INCLUINDO a entrada.
 * - downPayment: entrada já paga. O saldo a parcelar é totalContracted - downPayment.
 * - registeredCharges / negotiatedDiscount: encargos e descontos INFORMADOS no cadastro
 *   (informativos; supõe-se que já estejam embutidos no valor das parcelas).
 * - interestRateInfo: taxa de juros como texto livre ("2,5% a.m."). O app nunca inventa juros.
 */
data class Debt(
    val id: Long = 0,
    val name: String,
    val creditor: String = "",
    val category: String = "Outros",
    val originalAmount: Long,
    val downPayment: Long = 0,
    val contractDate: LocalDate,
    val totalContracted: Long,
    val installmentCount: Int,
    val installmentAmount: Long,
    val firstDueDate: LocalDate,
    val dueDay: Int,
    val interestRateInfo: String = "",
    val registeredCharges: Long = 0,
    val negotiatedDiscount: Long = 0,
    val notes: String = "",
    val status: DebtStatus = DebtStatus.ATIVA,
    val renegotiatedFromId: Long? = null,
    val createdAt: Long = 0
) {
    val amountToFinance: Long get() = totalContracted - downPayment
}

data class Installment(
    val id: Long = 0,
    val debtId: Long,
    val number: Int,
    val totalCount: Int,
    val amount: Long,
    val dueDate: LocalDate,
    val notes: String = ""
) {
    val label: String get() = "$number/$totalCount"
}

enum class PaymentTarget { INSTALLMENT, BILL, DEBT_AGREEMENT }

/**
 * Pagamento efetivo (saída de dinheiro).
 * - amount: valor desembolsado.
 * - charges: encargos efetivamente cobrados (juros, multa) — aumentam o devido.
 * - discount: descontos efetivamente concedidos — reduzem o devido.
 * - requestId: identificador único da operação (impede duplo lançamento).
 * - Pagamentos estornados (reversed = true) permanecem no histórico, mas não entram em nenhum cálculo.
 */
data class Payment(
    val id: Long = 0,
    val requestId: String,
    val target: PaymentTarget,
    val targetId: Long,
    val debtId: Long? = null,
    val amount: Long,
    val charges: Long = 0,
    val discount: Long = 0,
    val date: LocalDate,
    val method: String = "",
    val notes: String = "",
    val reversed: Boolean = false,
    val reversalReason: String? = null,
    val reversedAt: Long? = null,
    val createdAt: Long = 0,
    val editedAt: Long? = null
)

enum class Recurrence(val label: String) {
    NENHUMA("Única"),
    SEMANAL("Semanal"),
    MENSAL("Mensal"),
    ANUAL("Anual")
}

/** Conta mensal/recorrente (modelo). As ocorrências são geradas a partir dela. */
data class BillTemplate(
    val id: Long = 0,
    val name: String,
    val category: String,
    val expectedAmount: Long,
    val recurrence: Recurrence,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val active: Boolean = true,
    val notes: String = "",
    val createdAt: Long = 0
)

/** Ocorrência concreta de uma conta (ex.: energia de outubro). Única por (templateId, dueDate). */
data class BillOccurrence(
    val id: Long = 0,
    val templateId: Long,
    val dueDate: LocalDate,
    val expectedAmount: Long,
    val notes: String = "",
    val skipped: Boolean = false
)

enum class IncomeFrequency(val label: String) {
    MENSAL("Mensal"),
    QUINZENAL("Quinzenal"),
    SEMANAL("Semanal"),
    EVENTUAL("Eventual")
}

enum class IncomeKind(val label: String) {
    SALARIO("Salário"),
    BENEFICIO("Benefício"),
    EXTRA("Hora extra / extra"),
    VARIAVEL("Renda variável"),
    OUTRA("Outra renda")
}

/**
 * Fonte de renda. grossAmount é APENAS informativo e nunca entra nos cálculos.
 * O orçamento usa netAmount (líquido).
 */
data class IncomeSource(
    val id: Long = 0,
    val name: String,
    val kind: IncomeKind,
    val grossAmount: Long? = null,
    val netAmount: Long,
    val frequency: IncomeFrequency,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val active: Boolean = true,
    val notes: String = "",
    val createdAt: Long = 0
)

/** Ocorrência de recebimento. Prevista (expectedAmount) x recebida (receivedAmount). */
data class IncomeOccurrence(
    val id: Long = 0,
    val sourceId: Long,
    val date: LocalDate,
    val expectedAmount: Long,
    val receivedAmount: Long? = null,
    val receivedDate: LocalDate? = null,
    val notes: String = ""
) {
    val isReceived: Boolean get() = receivedAmount != null
}

data class ChangeLogEntry(
    val id: Long = 0,
    val entityType: String,
    val entityId: Long,
    val timestamp: Long,
    val description: String
)

enum class ThemeMode(val label: String) { SISTEMA("Seguir o sistema"), CLARO("Claro"), ESCURO("Escuro") }

data class AppSettings(
    val openingBalance: Long = 0,
    val openingDate: LocalDate? = null,
    val remindDayBefore: Boolean = true,
    val remindSameDay: Boolean = true,
    val reminderHour: Int = 8,
    val themeMode: ThemeMode = ThemeMode.SISTEMA
)

/** Foto completa dos dados. Todos os cálculos partem daqui (mesma fonte para dashboard e relatórios). */
data class FinanceData(
    val debts: List<Debt> = emptyList(),
    val installments: List<Installment> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val incomeSources: List<IncomeSource> = emptyList(),
    val incomeOccurrences: List<IncomeOccurrence> = emptyList(),
    val billTemplates: List<BillTemplate> = emptyList(),
    val billOccurrences: List<BillOccurrence> = emptyList(),
    val changeLog: List<ChangeLogEntry> = emptyList(),
    val settings: AppSettings = AppSettings()
) {
    val isEmpty: Boolean
        get() = debts.isEmpty() && incomeSources.isEmpty() && billTemplates.isEmpty()
}

object Categories {
    val bills = listOf(
        "Água", "Energia", "Aluguel", "Internet", "Telefone", "Mercado",
        "Transporte", "Saúde", "Educação", "Assinaturas", "Outras despesas"
    )
    val debts = listOf(
        "Cartão de crédito", "Empréstimo", "Financiamento", "Crediário",
        "Acordo", "Pessoal", "Outros"
    )
    val paymentMethods = listOf("Pix", "Dinheiro", "Débito", "Crédito", "Boleto", "Transferência", "Outro")
}
