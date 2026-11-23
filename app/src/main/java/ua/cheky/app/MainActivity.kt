package ua.cheky.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import ua.cheky.app.ui.AppViewModel
import ua.cheky.app.ui.ChekyTheme
import ua.cheky.app.ui.ScanScreen
import ua.cheky.app.ui.SettingsScreen
import ua.cheky.app.ui.SummaryScreen
import ua.cheky.app.ui.TranslateScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)

        setContent {
            ChekyTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val tabs = listOf("🧾" to "Чек", "📊" to "Підсумок", "🔤" to "Переклад", "⚙️" to "Налаштування")
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar {
                            tabs.forEachIndexed { i, (icon, label) ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Text(icon) },
                                    label = { Text(label, maxLines = 1) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (tab) {
                            0 -> ScanScreen(vm)
                            1 -> SummaryScreen(vm, onOpen = { tab = 0 })
                            2 -> TranslateScreen(vm)
                            else -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }

    /** A receipt photo shared from the gallery or another app. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        vm.addPhotos(listOf(uri))
    }
}
