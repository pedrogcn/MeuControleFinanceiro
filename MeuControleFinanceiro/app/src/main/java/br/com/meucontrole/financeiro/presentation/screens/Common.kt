package br.com.meucontrole.financeiro.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.meucontrole.financeiro.domain.calc.DueItem
import br.com.meucontrole.financeiro.domain.calc.DueKind
import br.com.meucontrole.financeiro.domain.calc.Fmt
import br.com.meucontrole.financeiro.domain.calc.ItemStatus
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.components.StatusBadge
import br.com.meucontrole.financeiro.presentation.theme.LocalFinanceColors

/**
 * Linha de vencimento/recebimento. O botão de check registra o pagamento em 2 toques
 * (abre o diálogo já preenchido com o valor restante).
 */
@Composable
fun DueItemRow(item: DueItem, onClick: () -> Unit, onQuickAction: (() -> Unit)? = null, showDate: Boolean = true) {
    val fin = LocalFinanceColors.current
    val amountColor = when (item.kind) {
        DueKind.INCOME -> fin.income
        else -> if (item.status == ItemStatus.VENCIDA) fin.expense else MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (showDate) Fmt.date(item.date) + " • " else "") + item.subtitle,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            StatusBadge(item.status)
        }
        Column(horizontalAlignment = Alignment.End) {
            val shown = if (item.status == ItemStatus.PAGA) item.done else item.remaining
            Text((if (item.kind == DueKind.INCOME) "+" else "") + Money.format(shown), color = amountColor, fontWeight = FontWeight.SemiBold)
            if (item.status == ItemStatus.PARCIAL || (item.status == ItemStatus.VENCIDA && item.done > 0)) {
                Text("pago ${Money.format(item.done)} de ${Money.format(item.expected)}", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (onQuickAction != null && item.status != ItemStatus.PAGA) {
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onQuickAction) {
                Icon(Icons.Filled.Check, contentDescription = if (item.kind == DueKind.INCOME) "Registrar recebimento" else "Registrar pagamento")
            }
        }
    }
}

/** Controla os diálogos de ação sobre um [DueItem] (pagar conta/parcela ou receber). */
class DueItemActionState {
    var payItem by mutableStateOf<DueItem?>(null)
    var receiveItem by mutableStateOf<DueItem?>(null)

    fun open(item: DueItem) {
        if (item.kind == DueKind.INCOME) receiveItem = item else payItem = item
    }
}

@Composable
fun rememberDueItemActions() = remember { DueItemActionState() }

@Composable
fun DueItemDialogs(vm: MainViewModel, state: DueItemActionState) {
    state.payItem?.let { item ->
        val target = item.toPayTarget()
        if (target != null) PayDialog(vm, target) { state.payItem = null }
    }
    state.receiveItem?.let { item ->
        ReceiveDialog(vm, item, received = item.status == ItemStatus.PAGA) { state.receiveItem = null }
    }
}
