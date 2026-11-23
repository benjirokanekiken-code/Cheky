package ua.cheky.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TranslateScreen(vm: AppViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Перекладач", "Німецька → українська: назви товарів, етикетки, склад продукту")
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            label = { Text("Текст німецькою") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.translate(text) }, enabled = text.isNotBlank() && !vm.translating) {
                if (vm.translating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Перекласти")
            }
            TextButton(onClick = { text = "" }) { Text("Очистити") }
        }
        if (vm.translation.isNotBlank()) {
            SectionCard("Переклад") {
                SelectionContainer { Text(vm.translation, style = MaterialTheme.typography.bodyLarge) }
            }
        }
        Text(
            if (vm.preciseMode) "Точний режим: перекладає Claude, пояснює терміни і позначає алергени."
            else "Офлайн-переклад на телефоні. Для пояснень і алергенів увімкніть точний режим у налаштуваннях.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
