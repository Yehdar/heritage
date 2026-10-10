package com.app.familytree.feature.logs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.familytree.core.model.*
import com.app.familytree.core.ui.*
import com.app.familytree.core.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun LogsScreen(state: LogsViewState, onReview: (String, Boolean) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeading("Logs", subtitle = if (state.pendingCount > 0) "${state.pendingCount} updates waiting for review" else "Your family's change history")
        ErrorMessage(state.error)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.entries.isEmpty()) item { Text("No changes yet. Add a relative or import a tree to start your history.", color = WarmGrey) }
            items(state.entries, key = { it.id }) { entry ->
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 1.dp) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(entry.summary, style = MaterialTheme.typography.titleMedium)
                        Text("${entry.actorName} · ${DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.timestampMillis))}", style = MaterialTheme.typography.bodyMedium, color = WarmGrey)
                        Text(entry.status.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = SoftBrown)
                        entry.details.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        if (entry.status == LogStatus.PENDING) {
                            Text("Review these additions and removals before changing your tree.", color = WarmGrey)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onReview(entry.id, true) }, enabled = !state.busy) { Text("Accept") }
                                OutlinedButton(onClick = { onReview(entry.id, false) }, enabled = !state.busy) { Text("Reject") }
                            }
                        }
                    }
                }
            }
        }
    }
}
