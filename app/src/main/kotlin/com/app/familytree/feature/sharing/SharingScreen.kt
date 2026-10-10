package com.app.familytree.feature.sharing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.familytree.core.model.*
import com.app.familytree.core.ui.*
import com.app.familytree.core.theme.*

@Composable fun SharingScreen(
    state: SharingViewState,
    onInput: (String) -> Unit,
    onCreateLink: () -> Unit,
    onShare: (String) -> Unit,
    onScan: () -> Unit,
    onReceive: () -> Unit,
    onRevoke: () -> Unit,
    onOptions: (Boolean, Boolean, RelationshipType) -> Unit,
    onMatch: (String, String?) -> Unit,
    onPreview: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
    onSync: (String, Boolean) -> Unit,
    onCheck: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    qrContent: @Composable (String) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeading("Sharing", subtitle = "Bring your family's stories together")
        ErrorMessage(state.error)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            state.setupMessage?.let { Text(it, color = WarmGrey) }
            state.message?.let { Text(it, color = SoftBrown) }
            if (state.draft == null) {
                SharingCard {
                    Text("Share your tree", style = MaterialTheme.typography.headlineMedium)
                    Text("Anyone with your link can view this tree. You can revoke the link at any time.", color = WarmGrey)
                    Button(onClick = onCreateLink, enabled = state.available && !state.busy) { Text(if (state.shareUrl == null) "Create share link" else "Refresh shared tree") }
                    state.shareUrl?.let { url ->
                        qrContent(url)
                        Text(url, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { onShare(url) }, enabled = !state.busy) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("Share URL") }
                        TextButton(onClick = onRevoke, enabled = !state.busy) { Text("Revoke link") }
                    }
                }
                SharingCard {
                    Text("Receive a tree", style = MaterialTheme.typography.headlineMedium)
                    OutlinedTextField(state.inputUrl, onInput, Modifier.fillMaxWidth(), label = { Text("Tree URL") }, singleLine = true, enabled = !state.busy)
                    Button(onClick = onReceive, enabled = state.available && !state.busy && state.inputUrl.isNotBlank()) { Text("Open shared tree") }
                    OutlinedButton(onClick = onScan, enabled = state.available && !state.busy) { Icon(Icons.Outlined.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Scan QR") }
                }
                if (state.links.isNotEmpty()) Text("LINKED TREES", style = MaterialTheme.typography.labelSmall)
                state.links.forEach { link -> SharingCard {
                    Text(link.ownerName, style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Live sync", Modifier.weight(1f).padding(top = 12.dp))
                        Switch(link.liveSync, { onSync(link.treeId, it) }, enabled = state.available && !state.busy)
                    }
                    Text("Changes wait for approval in Logs. Disconnecting keeps imported relatives.", color = WarmGrey)
                    TextButton(onClick = { onCheck(link.treeId) }, enabled = state.available && link.liveSync && !state.busy) { Text("Check updates") }
                    TextButton(onClick = { onDisconnect(link.treeId) }, enabled = !state.busy) { Text("Disconnect") }
                } }
            } else {
                val draft = state.draft
                if (draft != null) SharingCard {
                    Text("${draft.snapshot.ownerName}'s tree", style = MaterialTheme.typography.headlineMedium)
                    Text("${draft.snapshot.people.size} people · revision ${draft.snapshot.revision}", color = WarmGrey)
                    Text("Do you want their tree to overwrite existing parts of yours?", style = MaterialTheme.typography.titleMedium)
                    Text("Matching is explicit below. Replacing updates matching names, photos and relationship details; local-only relatives stay.", color = WarmGrey)
                    listOf(false to "Keep my existing details", true to "Replace matching details with theirs").forEach { (overwrite, title) ->
                        Row { RadioButton(draft.overwriteMatching == overwrite, { onOptions(overwrite, draft.liveSync, draft.relationship) }, enabled = !state.busy); Text(title, Modifier.padding(top = 12.dp)) }
                    }
                    Text("Do you want to live-sync with their tree?", style = MaterialTheme.typography.titleMedium)
                    Row { Switch(draft.liveSync, { onOptions(draft.overwriteMatching, it, draft.relationship) }, enabled = !state.busy); Text("Review future additions and removals in Logs", Modifier.padding(start = 12.dp, top = 8.dp)) }
                    Text("Match existing relatives (optional)", style = MaterialTheme.typography.titleMedium)
                    Text("People with the same name remain separate unless you match them. Choose a local relative for each shared person, or leave them as a new person.", color = WarmGrey)
                    draft.snapshot.people.forEach { remote ->
                        key(remote.id) { MatchPicker(remote, state.people, draft.personMapping[remote.id], !state.busy) { onMatch(remote.id, it) } }
                    }
                    if (draft.snapshot.ownerPersonId !in draft.personMapping) {
                        val anchor = state.people.firstOrNull { it.id == draft.anchorId }?.name.orEmpty()
                        Text("Their relationship to $anchor", style = MaterialTheme.typography.titleMedium)
                        RelationshipType.entries.forEach { type -> Row { RadioButton(draft.relationship == type, { onOptions(draft.overwriteMatching, draft.liveSync, type) }, enabled = !state.busy); Text(type.name.lowercase().replaceFirstChar { it.uppercase() }, Modifier.padding(top = 12.dp)) } }
                    }
                    Button(onClick = onPreview, enabled = !state.busy) { Text("Preview changes") }
                    if (draft.previewReady) {
                        Text("IMPORT PREVIEW", style = MaterialTheme.typography.labelSmall)
                        draft.preview.ifEmpty { listOf("No tree details will change.") }.forEach { Text("• $it") }
                        Button(onClick = onImport, enabled = !state.busy) { Text("Confirm import") }
                    }
                    TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Cancel import") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
@Composable private fun MatchPicker(remote: Person, locals: List<Person>, selectedId: String?, enabled: Boolean, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(remote.name, style = MaterialTheme.typography.titleMedium)
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text(locals.firstOrNull { it.id == selectedId }?.let { "Matches ${it.name}" } ?: "Add as a new person") }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("Add as a new person") }, onClick = { onSelect(null); expanded = false })
            locals.forEach { person -> DropdownMenuItem(text = { Text(person.name) }, onClick = { onSelect(person.id); expanded = false }) }
        }
    }
}
@Composable private fun SharingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 1.dp) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}
