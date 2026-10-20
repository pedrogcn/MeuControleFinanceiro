package br.com.meucontrole.financeiro.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import br.com.meucontrole.financeiro.domain.calc.DueItem
import br.com.meucontrole.financeiro.domain.calc.DueKind
import br.com.meucontrole.financeiro.domain.calc.Fmt
import br.com.meucontrole.financeiro.domain.calc.ItemStatus
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.components.AppScaffold
import br.com.meucontrole.financeiro.presentation.components.InfoCard
import br.com.meucontrole.financeiro.presentation.components.Legend
import br.com.meucontrole.financeiro.presentation.components.MonthSelector
import br.com.meucontrole.financeiro.presentation.components.SectionTitle
import br.com.meucontrole.financeiro.presentation.navigation.Routes
import br.com.meucontrole.financeiro.presentation.theme.LocalFinanceColors
import java.time.LocalDate

@Composable
fun CalendarScreen(vm: MainViewModel, nav: NavController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val actions = rememberDueItemActions()
    var billDetail by remember { mutableStateOf<DueItem?>(null) }
    val fin = LocalFinanceColors.current

    AppScaffold(title = "Calendário", topLevel = true) { padding ->
        val u = ui ?: run { Loading(); return@AppScaffold }
        val items = remember(u, month) { u.engine.itemsForMonth(month) }
        val byDay = remember(items) { items.groupBy { it.date } }
        val overdueOutside = remember(u, month) { u.engine.overdueItems.filter { it.date.isBefore(month.atDay(1)) } }

        fun open(item: DueItem) {
            when (item.kind) {
                DueKind.INSTALLMENT -> u.data.installments.firstOrNull { it.id == item.refId }?.let { nav.navigate(Routes.debt(it.debtId)) }
                DueKind.BILL -> billDetail = item
                DueKind.INCOME -> actions.open(item)
            }
        }

        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            MonthSelector(month, { vm.setMonth(it); selected = null })
            Row(Modifier.fillMaxWidth()) {
                listOf("D", "S", "T", "Q", "Q", "S", "S").forEach {
                    Text(it, Modifier.weight(1f).padding(vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            val first = month.atDay(1)
            val offset = first.dayOfWeek.value % 7 // domingo = 0
            val cells = offset + month.lengthOfMonth()
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - offset + 1
                        if (dayNum < 1 || dayNum > month.lengthOfMonth()) {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = month.atDay(dayNum)
                            val dayItems = byDay[date].orEmpty()
                            DayCell(
                                date = date,
                                items = dayItems,
                                isToday = date == u.today,
                                isSelected = date == selected,
                                modifier = Modifier.weight(1f),
                                onClick = { selected = if (selected == date) null else date }
                            )
                        }
                    }
                }
            }
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend(fin.expense, "Vencido"); Legend(MaterialTheme.colorScheme.primary, "A pagar")
                Legend(fin.income, "Pago/recebido"); Legend(MaterialTheme.colorScheme.tertiary, "Receita")
            }

            val sel = selected
            val list = if (sel != null) byDay[sel].orEmpty() else items
            SectionTitle(if (sel != null) "Dia ${Fmt.date(sel)}" else "Todos os itens de ${Fmt.month(month)}")
            InfoCard {
                if (list.isEmpty()) Text("Nenhum item.", style = MaterialTheme.typography.bodyMedium)
                list.forEach { item ->
                    DueItemRow(item, onClick = { open(item) }, onQuickAction = { actions.open(item) }, showDate = sel == null)
                    HorizontalDivider()
                }
            }
            if (overdueOutside.isNotEmpty()) {
                SectionTitle("Vencidos de meses anteriores")
                InfoCard {
                    overdueOutside.forEach { item ->
                        DueItemRow(item, onClick = { open(item) }, onQuickAction = { actions.open(item) })
                        HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    DueItemDialogs(vm, actions)
    billDetail?.let { d ->
        BillOccurrenceDialog(vm, d, onPay = { billDetail = null; actions.open(d) },
            onEditTemplate = { tid -> billDetail = null; nav.navigate(Routes.bill(tid)) }) { billDetail = null }
    }
}

@Composable
private fun DayCell(date: LocalDate, items: List<DueItem>, isToday: Boolean, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fin = LocalFinanceColors.current
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val colors: List<Color> = items.map {
        when {
            it.kind == DueKind.INCOME && it.status == ItemStatus.PAGA -> fin.income
            it.kind == DueKind.INCOME -> tertiary
            it.status == ItemStatus.PAGA -> fin.income
            it.status == ItemStatus.VENCIDA -> fin.expense
            else -> primary
        }
    }.distinct().take(4)
    val pending = items.filter { it.kind != DueKind.INCOME }.sumOf { it.remaining }
    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .then(if (isToday) Modifier.border(1.5.dp, primary, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Dia ${date.dayOfMonth}: ${items.size} item(ns)" + if (pending > 0) ", ${Money.format(pending)} a pagar" else ""
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                colors.forEach { Box(Modifier.size(6.dp).clip(CircleShape).background(it)) }
            }
            if (colors.isEmpty()) Spacer(Modifier.height(6.dp).width(6.dp))
        }
    }
}
