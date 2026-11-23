package ua.cheky.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var key by remember { mutableStateOf(vm.apiKey) }
    var saved by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Налаштування")

        SectionCard("Офлайн-режим (безкоштовно)") {
            Text(
                "Текст чека розпізнається прямо на телефоні, переклад — вбудованим словником Google ML Kit. " +
                    "Словник (~30 МБ) завантажується один раз, далі все працює без інтернету.",
            )
            FilledTonalButton(onClick = vm::downloadModel) { Text("Завантажити словник зараз") }
            vm.modelStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }

        SectionCard("Точний режим (Claude)") {
            Text(
                "Якщо вказати власний ключ Anthropic API, фото чека надсилатиметься в Claude: " +
                    "краще розшифровує скорочення, знижки й Pfand, перекладає природніше. " +
                    "Оплачується за вашим API-акаунтом (зазвичай кілька центів за чек).",
            )
            OutlinedTextField(
                value = key, onValueChange = { key = it; saved = false },
                label = { Text("API-ключ (sk-ant-…)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { vm.saveApiKey(key); saved = true }) {
                Text(if (saved) "✓ Збережено" else "Зберегти ключ")
            }
            Text(
                if (vm.preciseMode) "Зараз увімкнено: точний режим." else "Зараз увімкнено: офлайн-режим. Залиште поле порожнім, щоб не використовувати Claude.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Дані") {
            Text("Збережено чеків: ${vm.receipts.size}. Дані зберігаються лише на цьому телефоні.")
            OutlinedButton(onClick = { if (confirmWipe) { vm.deleteAll(); confirmWipe = false } else { confirmWipe = true } }) {
                Text(
                    if (confirmWipe) "Натисніть ще раз, щоб видалити всі" else "Видалити всі чеки",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text("Чеки 1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
