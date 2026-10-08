package com.app.familytree.feature.findothers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.familytree.core.model.*
import com.app.familytree.core.ui.*
import com.app.familytree.core.theme.*

@Composable fun FindOthersScreen(state: FindOthersViewState, onSearch: (String) -> Unit, onConnect: (Person) -> Unit, onChoose: (RelationshipType) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeading("Find Others")
        OutlinedTextField(state.searchQuery, onSearch, Modifier.fillMaxWidth().padding(horizontal = 24.dp), placeholder = { Text("Search relatives or enter tree code...", style = MaterialTheme.typography.bodyMedium) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(28.dp), singleLine = true)
        Text("PEOPLE YOU MAY KNOW", Modifier.padding(start = 24.dp, top = 32.dp, bottom = 16.dp), style = MaterialTheme.typography.labelSmall, color = SoftBrown)
        ErrorMessage(state.error)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (state.suggestions.isEmpty()) item { Text("No relatives found. Try a name or DOE-1999.", color = WarmGrey) }
            items(state.suggestions, key = { it.id }) { person ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Avatar(person, Modifier.size(54.dp))
                    Text(person.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { onConnect(person) }, enabled = person.id !in state.connectedUserIds, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text(if (person.id in state.connectedUserIds) "Connected" else "Connect") }
                }
            }
        }
    }
    state.form?.let { form ->
        AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White,
            title = { Text("Connect ${form.name}", style = MaterialTheme.typography.headlineMedium) },
            text = { Column { Text("Choose their relationship to ${state.anchorName}.")
                RelationshipType.entries.forEach { type -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = form.type == type, onClick = { onChoose(type) }, enabled = !form.saving); Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) } }
                form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { Button(onClick = onSave, enabled = !form.saving) { Text(if (form.saving) "Saving…" else "Add to tree") } },
            dismissButton = { TextButton(onClick = onDismiss, enabled = !form.saving) { Text("Cancel") } })
    }
}
