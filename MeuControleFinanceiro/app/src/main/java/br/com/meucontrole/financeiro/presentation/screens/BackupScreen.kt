package br.com.meucontrole.financeiro.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import br.com.meucontrole.financeiro.backup.BackupCodec
import br.com.meucontrole.financeiro.backup.RestoreBackupUseCase
import br.com.meucontrole.financeiro.backup.RestoreMode
import br.com.meucontrole.financeiro.backup.RestorePreview
import br.com.meucontrole.financeiro.domain.calc.Fmt
import br.com.meucontrole.financeiro.domain.calc.ReportBuilder
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.components.AppScaffold
import br.com.meucontrole.financeiro.presentation.components.InfoCard
import br.com.meucontrole.financeiro.presentation.components.SectionTitle
import br.com.meucontrole.financeiro.presentation.theme.LocalFinanceColors
import br.com.meucontrole.financeiro.util.FileExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun BackupScreen(vm: MainViewModel, nav: NavController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<RestorePreview?>(null) }
    var errors by remember { mutableStateOf<List<String>?>(null) }
    var confirmMode by remember { mutableStateOf<RestoreMode?>(null) }
    val fin = LocalFinanceColors.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            working = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val data = vm.repository.snapshot()
                    val json = BackupCodec.encode(data, System.currentTimeMillis())
                    FileExport.writeText(context, uri, json)
                    // Verificação pós-gravação: relê o arquivo e valida
                    val check = BackupCodec.decode(FileExport.readText(context, uri))
                    if (check !is BackupCodec.Result.Success) error("O arquivo gravado não passou na verificação.")
                }
            }
            working = false
            vm.message(if (result.isSuccess) "Backup exportado e verificado." else "Falha no backup: ${result.exceptionOrNull()?.message}")
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    FileExport.writeText(context, uri, ReportBuilder.debtsCsv(vm.repository.snapshot(), LocalDate.now()), withBom = true)
                }
            }
            vm.message(if (result.isSuccess) "CSV exportado." else "Falha: ${result.exceptionOrNull()?.message}")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            working = true
            // Apenas lê e valida. Nada é alterado até a confirmação explícita.
            val text = runCatching { withContext(Dispatchers.IO) { FileExport.readText(context, uri) } }
            if (text.isFailure) {
                errors = listOf(text.exceptionOrNull()?.message ?: "Não foi possível ler o arquivo.")
            } else when (val r = withContext(Dispatchers.Default) { vm.restore.preview(text.getOrThrow()) }) {
                is RestoreBackupUseCase.PreviewResult.Invalid -> errors = r.errors
                is RestoreBackupUseCase.PreviewResult.Valid -> preview = r.preview
            }
            working = false
        }
    }

    AppScaffold(title = "Backup e restauração", onBack = { nav.popBackStack() }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoCard {
                Text("Seus dados existem apenas neste aparelho", fontWeight = FontWeight.SemiBold, color = fin.warning)
                Text(
                    "O app não usa internet nem nuvem. Se o celular for perdido, formatado ou o app for desinstalado, os dados não exportados serão perdidos. " +
                        "Exporte um backup regularmente e guarde-o em outro lugar (computador, pen drive, e-mail para você mesmo).",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            SectionTitle("Exportar")
            Button(onClick = { exportLauncher.launch("meu-controle-backup-${LocalDate.now()}.json") }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                Text("Exportar backup completo (JSON)")
            }
            OutlinedButton(onClick = { csvLauncher.launch("dividas-e-pagamentos-${LocalDate.now()}.csv") }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                Text("Exportar CSV de dívidas e pagamentos")
            }
            Text("O backup contém dívidas, parcelas, pagamentos e estornos, receitas, contas, configurações e histórico de alterações. O PIN não é incluído.",
                style = MaterialTheme.typography.bodySmall)
            SectionTitle("Restaurar")
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                enabled = !working, modifier = Modifier.fillMaxWidth()) {
                Text(if (working) "Processando…" else "Importar backup")
            }
            Text("O arquivo é validado (formato, versão, checksum e integridade) antes de qualquer alteração. Você verá um resumo e escolherá entre substituir ou mesclar.",
                style = MaterialTheme.typography.bodySmall)
            val count = ui?.data?.let { it.debts.size + it.billTemplates.size + it.incomeSources.size } ?: 0
            Text("Dados atuais: ${ui?.data?.debts?.size ?: 0} dívida(s), ${ui?.data?.billTemplates?.size ?: 0} conta(s), ${ui?.data?.incomeSources?.size ?: 0} receita(s), ${ui?.data?.payments?.size ?: 0} pagamento(s).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (count == 0) Text("Como não há dados, a restauração por substituição é a opção indicada.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
    }

    errors?.let { list ->
        AlertDialog(
            onDismissRequest = { errors = null },
            title = { Text("Backup inválido") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Nenhum dado foi alterado. Problemas encontrados:")
                    list.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { errors = null }) { Text("OK") } }
        )
    }

    preview?.let { p ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Backup válido") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (p.exportedAt > 0) Text("Exportado em ${Fmt.date(Instant.ofEpochMilli(p.exportedAt).atZone(ZoneId.systemDefault()).toLocalDate())}")
                    p.counts.forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(6.dp))
                    Text("Se mesclar:", fontWeight = FontWeight.SemiBold)
                    Text("${p.merge.insertedCount} registro(s) novos serão adicionados.", style = MaterialTheme.typography.bodySmall)
                    if (p.merge.duplicates.isNotEmpty()) {
                        Text("Duplicados ignorados:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        p.merge.duplicates.take(10).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        if (p.merge.duplicates.size > 10) Text("… e mais ${p.merge.duplicates.size - 10}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (p.merge.conflicts.isNotEmpty()) {
                        Text("Conflitos (mantidos os dados atuais):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = fin.warning)
                        p.merge.conflicts.take(10).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { confirmMode = RestoreMode.MESCLAR }, modifier = Modifier.fillMaxWidth()) { Text("Mesclar com os dados atuais") }
                    OutlinedButton(onClick = { confirmMode = RestoreMode.SUBSTITUIR }, modifier = Modifier.fillMaxWidth()) {
                        Text("Substituir todos os dados", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancelar") } }
        )
    }

    confirmMode?.let { mode ->
        val p = preview
        AlertDialog(
            onDismissRequest = { confirmMode = null },
            title = { Text(if (mode == RestoreMode.SUBSTITUIR) "Substituir todos os dados?" else "Mesclar dados?") },
            text = {
                Text(
                    if (mode == RestoreMode.SUBSTITUIR) "TODOS os dados atuais serão apagados e trocados pelo conteúdo do backup. Recomendamos exportar um backup dos dados atuais antes. Esta ação não pode ser desfeita."
                    else "Os registros novos do backup serão adicionados. Itens duplicados serão ignorados. Nada será apagado."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmMode = null
                    if (p == null) return@TextButton
                    scope.launch {
                        working = true
                        val r = runCatching { vm.restore.apply(p, mode) }
                        working = false
                        preview = null
                        vm.refresh()
                        vm.message(if (r.isSuccess) "Restauração concluída." else "Falha na restauração. Nenhum dado foi alterado: ${r.exceptionOrNull()?.message}")
                    }
                }) { Text("Confirmar", color = if (mode == RestoreMode.SUBSTITUIR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { confirmMode = null }) { Text("Cancelar") } }
        )
    }
}
