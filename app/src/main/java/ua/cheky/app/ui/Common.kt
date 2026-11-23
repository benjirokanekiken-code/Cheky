package ua.cheky.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.cheky.app.model.eur
import kotlin.math.abs

@Composable
fun ChekyTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF4FB79A), secondary = Color(0xFFE9B73C), tertiary = Color(0xFF6E9BE0),
    ) else lightColorScheme(
        primary = Color(0xFF1F6F5C), secondary = Color(0xFFB8860B), tertiary = Color(0xFF2C5FA8),
        background = Color(0xFFF6F4EF), surface = Color(0xFFF6F4EF),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionCard(title: String? = null, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            content()
        }
    }
}

/** Horizontal bar chart: label – bar – amount. */
@Composable
fun Bars(data: Map<String, Double>) {
    val entries = data.entries.filter { abs(it.value) > 0.001 }.sortedByDescending { it.value }
    if (entries.isEmpty()) {
        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant); return
    }
    val maxV = entries.maxOf { abs(it.value) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { (label, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label, Modifier.width(128.dp), style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier.fillMaxHeight()
                            .fillMaxWidth((abs(v) / maxV).toFloat().coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (v < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    )
                }
                Text(
                    eur(v), Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
