package br.com.meucontrole.financeiro.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow

/**
 * Scaffold padrão das telas.
 * [topLevel] = tela com barra inferior: a barra já cuida da borda inferior do sistema.
 */
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    topLevel: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = actions
            )
        },
        floatingActionButton = fab,
        contentWindowInsets = if (topLevel) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        content = content
    )
}
