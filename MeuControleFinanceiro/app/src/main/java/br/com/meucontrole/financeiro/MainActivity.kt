package br.com.meucontrole.financeiro

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.meucontrole.financeiro.domain.model.ThemeMode
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.navigation.AppRoot
import br.com.meucontrole.financeiro.presentation.screens.LockScreen
import br.com.meucontrole.financeiro.presentation.theme.MeuControleTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels { MainViewModel.Factory }
    private val locked = mutableStateOf(false)
    private var backgroundAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) locked.value = vm.security.hasPin
        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            val theme = ui?.data?.settings?.themeMode ?: ThemeMode.SISTEMA
            MeuControleTheme(theme) {
                if (locked.value && vm.security.hasPin) {
                    LockScreen(security = vm.security, onUnlocked = { locked.value = false })
                } else {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Bloqueia novamente se o app ficou mais de 2 minutos em segundo plano.
        // (Abrir o seletor de arquivos do backup não bloqueia, pois costuma durar menos que isso.)
        if (vm.security.hasPin && backgroundAt > 0 && SystemClock.elapsedRealtime() - backgroundAt > LOCK_AFTER_MS) {
            locked.value = true
        }
        vm.refresh()
    }

    override fun onStop() {
        super.onStop()
        backgroundAt = SystemClock.elapsedRealtime()
    }

    companion object {
        private const val LOCK_AFTER_MS = 120_000L
    }
}
