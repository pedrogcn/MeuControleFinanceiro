package br.com.meucontrole.financeiro.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.meucontrole.financeiro.domain.calc.Fmt
import br.com.meucontrole.financeiro.domain.calc.ItemStatus
import br.com.meucontrole.financeiro.domain.calc.MonthFlow
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.presentation.theme.LocalFinanceColors
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

// ------------------------------- Texto e cards -------------------------------

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun SummaryCard(
    label: String,
    value: Long,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    hint: String? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(Money.format(value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Linha "rótulo .......... valor" para tabelas de resumo. */
@Composable
fun ValueRow(label: String, value: Long, color: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false, prefix: String = "") {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(prefix + Money.format(value), style = MaterialTheme.typography.bodyMedium, color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun TextRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(12.dp)); action() }
    }
}

@Composable
fun StatusBadge(status: ItemStatus) {
    val fin = LocalFinanceColors.current
    val (bg, fg) = when (status) {
        ItemStatus.PAGA -> fin.income.copy(alpha = 0.15f) to fin.income
        ItemStatus.VENCIDA -> fin.expense.copy(alpha = 0.15f) to fin.expense
        ItemStatus.PARCIAL -> fin.warning.copy(alpha = 0.15f) to fin.warning
        ItemStatus.PENDENTE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(status.label, color = fg, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun ProgressLine(progress: Float, label: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label: ${(progress * 100).toInt()}%" }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
    }
}

// ------------------------------- Seletor de mês -------------------------------

@Composable
fun MonthSelector(month: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Mês anterior") }
        TextButton(onClick = { onChange(YearMonth.now()) }) {
            Text(Fmt.month(month), style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = { onChange(month.plusMonths(1)) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Próximo mês") }
    }
}

// ------------------------------- Campos de formulário -------------------------------

/** Campo de valor em reais. [text] é o texto digitado; o valor é interpretado com [Money.parse]. */
@Composable
fun MoneyField(
    label: String,
    text: String,
    onText: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = true,
    supporting: String? = null
) {
    val parsed = Money.parse(text)
    val error = when {
        text.isBlank() && required -> null
        text.isBlank() -> null
        parsed == null -> "Valor inválido (ex.: 1.234,56)"
        else -> null
    }
    OutlinedTextField(
        value = text,
        onValueChange = { new -> onText(new.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label) },
        prefix = { Text("R$ ") },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { msg -> @Composable { Text(msg) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

fun LocalDate.toPickerMillis(): Long = toEpochDay() * 86_400_000L
fun Long.fromPickerMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
fun DateField(label: String, date: LocalDate?, onDate: (LocalDate?) -> Unit, modifier: Modifier = Modifier, allowClear: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = date?.let { Fmt.date(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        // Camada transparente para abrir o seletor ao tocar em qualquer ponto do campo
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now()).toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { onDate(it.fromPickerMillis()) }; open = false }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (allowClear) TextButton(onClick = { onDate(null); open = false }) { Text("Limpar") }
                    TextButton(onClick = { open = false }) { Text("Cancelar") }
                }
            }
        ) { DatePicker(state = state) }
    }
}

@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(optionLabel(opt)) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

/** Campo de texto com sugestões (permite categoria personalizada). */
@Composable
fun SuggestField(label: String, value: String, onValue: (String) -> Unit, suggestions: List<String>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = if (value.isBlank() || value in suggestions) suggestions else suggestions.filter { it.contains(value, ignoreCase = true) }
    ExposedDropdownMenuBox(expanded = expanded && filtered.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValue(it); expanded = true },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded && filtered.isNotEmpty(), onDismissRequest = { expanded = false }) {
            filtered.forEach { opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { onValue(opt); expanded = false }) }
        }
    }
}

// ------------------------------- Diálogos -------------------------------

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Confirmar",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmText, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ------------------------------- Gráficos simples -------------------------------

/** Barras agrupadas: receitas (verde) x despesas (vermelho) por mês. */
@Composable
fun IncomeExpenseBars(flows: List<MonthFlow>, modifier: Modifier = Modifier) {
    val fin = LocalFinanceColors.current
    val max = (flows.maxOfOrNull { maxOf(it.income, it.expenses) } ?: 0L).coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            flows.forEach { f ->
                Row(
                    Modifier.fillMaxHeight().semantics(mergeDescendants = true) {
                        contentDescription = "${Fmt.shortMonth(f.month)}: recebido ${Money.format(f.income)}, pago ${Money.format(f.expenses)}"
                    },
                    verticalAlignment = Alignment.Bottom
                ) {
                    Bar(f.income.toFloat() / max, fin.income)
                    Spacer(Modifier.width(3.dp))
                    Bar(f.expenses.toFloat() / max, fin.expense)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            flows.forEach { Text(Fmt.shortMonth(it.month), style = MaterialTheme.typography.labelSmall) }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(fin.income, "Recebido"); Spacer(Modifier.width(16.dp)); Legend(fin.expense, "Pago")
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(
        Modifier.width(12.dp)
            .fillMaxHeight(fraction.coerceIn(0.01f, 1f))
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .background(color)
    )
}

@Composable
fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

/** Linha do saldo acumulado mês a mês. */
@Composable
fun BalanceLine(flows: List<MonthFlow>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val zeroColor = MaterialTheme.colorScheme.outline
    val values = flows.map { it.cumulative }
    if (values.isEmpty()) return
    val min = minOf(values.min(), 0L)
    val max = maxOf(values.max(), 0L)
    val range = (max - min).coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().height(120.dp).semantics {
                contentDescription = "Saldo acumulado: " + flows.joinToString { "${Fmt.shortMonth(it.month)} ${Money.format(it.cumulative)}" }
            }
        ) {
            val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
            fun y(v: Long) = size.height - ((v - min).toFloat() / range) * size.height
            drawLine(zeroColor, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = 1f)
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = if (values.size > 1) i * stepX else size.width / 2
                if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
            }
            drawPath(path, lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
            values.forEachIndexed { i, v ->
                val x = if (values.size > 1) i * stepX else size.width / 2
                drawCircle(lineColor, radius = 7f, center = Offset(x, y(v)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            flows.forEach { Text(Fmt.shortMonth(it.month), style = MaterialTheme.typography.labelSmall) }
        }
    }
}
