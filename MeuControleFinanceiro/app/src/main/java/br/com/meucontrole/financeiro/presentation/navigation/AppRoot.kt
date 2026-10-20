package br.com.meucontrole.financeiro.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.screens.BackupScreen
import br.com.meucontrole.financeiro.presentation.screens.BillFormScreen
import br.com.meucontrole.financeiro.presentation.screens.BillsScreen
import br.com.meucontrole.financeiro.presentation.screens.BudgetScreen
import br.com.meucontrole.financeiro.presentation.screens.CalendarScreen
import br.com.meucontrole.financeiro.presentation.screens.ChangeLogScreen
import br.com.meucontrole.financeiro.presentation.screens.DebtDetailScreen
import br.com.meucontrole.financeiro.presentation.screens.DebtFormScreen
import br.com.meucontrole.financeiro.presentation.screens.DebtsScreen
import br.com.meucontrole.financeiro.presentation.screens.HomeScreen
import br.com.meucontrole.financeiro.presentation.screens.IncomeFormScreen
import br.com.meucontrole.financeiro.presentation.screens.IncomesScreen
import br.com.meucontrole.financeiro.presentation.screens.MoreScreen
import br.com.meucontrole.financeiro.presentation.screens.ReportsScreen
import br.com.meucontrole.financeiro.presentation.screens.SettingsScreen

object Routes {
    const val HOME = "inicio"
    const val DEBTS = "dividas"
    const val BILLS = "contas"
    const val CALENDAR = "calendario"
    const val MORE = "mais"
    const val DEBT_DETAIL = "divida/{id}"
    const val DEBT_FORM = "divida_form?editId={editId}&renegotiate={renegotiate}"
    const val BILL_FORM = "conta_form?id={id}"
    const val INCOMES = "receitas"
    const val INCOME_FORM = "receita_form?id={id}"
    const val BUDGET = "orcamento"
    const val REPORTS = "relatorios"
    const val BACKUP = "backup"
    const val SETTINGS = "configuracoes"
    const val CHANGELOG = "historico"

    fun debt(id: Long) = "divida/$id"
    fun newDebt() = "divida_form"
    fun editDebt(id: Long) = "divida_form?editId=$id"
    fun renegotiate(id: Long) = "divida_form?renegotiate=$id"
    fun bill(id: Long = 0) = "conta_form?id=$id"
    fun income(id: Long = 0) = "receita_form?id=$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Início", Icons.Filled.Home),
    Tab(Routes.DEBTS, "Dívidas", Icons.Filled.CreditCard),
    Tab(Routes.BILLS, "Contas", Icons.Filled.Receipt),
    Tab(Routes.CALENDAR, "Calendário", Icons.Filled.CalendarMonth),
    Tab(Routes.MORE, "Mais", Icons.Filled.MoreHoriz)
)

@Composable
fun AppRoot(vm: MainViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        // As telas internas têm seus próprios Scaffolds/TopAppBars, que tratam as bordas do sistema.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = { nav.navigateTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) { HomeScreen(vm, nav) }
            composable(Routes.DEBTS) { DebtsScreen(vm, nav) }
            composable(Routes.BILLS) { BillsScreen(vm, nav) }
            composable(Routes.CALENDAR) { CalendarScreen(vm, nav) }
            composable(Routes.MORE) { MoreScreen(nav) }
            composable(Routes.DEBT_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                DebtDetailScreen(vm, nav, it.arguments?.getLong("id") ?: 0)
            }
            composable(
                Routes.DEBT_FORM,
                arguments = listOf(
                    navArgument("editId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("renegotiate") { type = NavType.LongType; defaultValue = 0L }
                )
            ) {
                DebtFormScreen(vm, nav, editId = it.arguments?.getLong("editId") ?: 0, renegotiateId = it.arguments?.getLong("renegotiate") ?: 0)
            }
            composable(Routes.BILL_FORM, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })) {
                BillFormScreen(vm, nav, it.arguments?.getLong("id") ?: 0)
            }
            composable(Routes.INCOMES) { IncomesScreen(vm, nav) }
            composable(Routes.INCOME_FORM, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })) {
                IncomeFormScreen(vm, nav, it.arguments?.getLong("id") ?: 0)
            }
            composable(Routes.BUDGET) { BudgetScreen(vm, nav) }
            composable(Routes.REPORTS) { ReportsScreen(vm, nav) }
            composable(Routes.BACKUP) { BackupScreen(vm, nav) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
            composable(Routes.CHANGELOG) { ChangeLogScreen(vm, nav) }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
