package br.com.meucontrole.financeiro.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import br.com.meucontrole.financeiro.presentation.components.AppScaffold
import br.com.meucontrole.financeiro.presentation.navigation.Routes

@Composable
fun MoreScreen(nav: NavController) {
    AppScaffold(title = "Mais", topLevel = true) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Entry(Icons.Filled.AttachMoney, "Salário e receitas", "Fontes de renda, previstos e recebidos") { nav.navigate(Routes.INCOMES) }
            Entry(Icons.Filled.Savings, "Quanto posso gastar?", "Saldo realizado e projetado") { nav.navigate(Routes.BUDGET) }
            Entry(Icons.Filled.BarChart, "Relatórios", "Mensais e históricos, exportação CSV e PDF") { nav.navigate(Routes.REPORTS) }
            Entry(Icons.Filled.Backup, "Backup e restauração", "Exportar e importar seus dados") { nav.navigate(Routes.BACKUP) }
            Entry(Icons.Filled.History, "Histórico de alterações", "Edições, estornos e renegociações") { nav.navigate(Routes.CHANGELOG) }
            Entry(Icons.Filled.Settings, "Configurações", "Saldo inicial, lembretes, PIN e tema") { nav.navigate(Routes.SETTINGS) }
            Text(
                "Meu Controle Financeiro funciona 100% offline. Seus dados ficam apenas neste aparelho e não são enviados a ninguém.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun Entry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}
