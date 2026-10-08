package com.app.familytree.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.app.familytree.core.ui.*
import com.app.familytree.core.theme.*

@Composable fun SettingsScreen(state: SettingsViewState, onOpen: (SettingsPage?) -> Unit, onEdit: (String, String) -> Unit, onSave: () -> Unit, onPrivacy: (Boolean, Boolean) -> Unit, onNotifications: (Boolean) -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeading("Settings")
        ErrorMessage(state.error)
        if (state.page == null) {
            Surface(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 1.dp) {
                Column {
                    SettingsRow("Account Profile", Icons.Outlined.Person) { onOpen(SettingsPage.PROFILE) }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Beige)
                    SettingsRow("Privacy & Security", Icons.Outlined.Lock) { onOpen(SettingsPage.PRIVACY) }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Beige)
                    SettingsRow("Tree Access", Icons.Outlined.AccountTree) { onOpen(SettingsPage.ACCESS) }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Beige)
                    SettingsRow("Notifications", Icons.Outlined.Notifications) { onOpen(SettingsPage.NOTIFICATIONS) }
                }
            }
            Surface(Modifier.padding(24.dp).fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 1.dp) { SettingsRow("Log Out", Icons.AutoMirrored.Outlined.Logout, chevron = false, enabled = !state.saving, onClick = onLogout) }
        } else {
            TextButton(onClick = { onOpen(null) }, modifier = Modifier.padding(start = 12.dp), enabled = !state.saving) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null); Text("Back to Settings") }
            Surface(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (state.page) {
                        SettingsPage.PROFILE -> {
                            Text("Account Profile", style = MaterialTheme.typography.headlineMedium)
                            OutlinedTextField(state.profileName, { onEdit(it, state.profileAvatar) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), enabled = !state.saving)
                            OutlinedTextField(state.profileAvatar, { onEdit(state.profileName, it) }, label = { Text("Avatar URL (optional)") }, modifier = Modifier.fillMaxWidth(), enabled = !state.saving)
                            Button(onClick = onSave, enabled = !state.saving) { Text("Save profile") }
                        }
                        SettingsPage.PRIVACY -> {
                            Text("Privacy & Security", style = MaterialTheme.typography.headlineMedium)
                            Text("Local demo preferences. Your tree is stored on this device; no online sharing or access enforcement is enabled.", color = WarmGrey)
                            ToggleRow("Private tree", state.settings.isPrivate, !state.saving) { onPrivacy(it, state.settings.discoverable) }
                            ToggleRow("Discoverable profile", state.settings.discoverable, !state.saving) { onPrivacy(state.settings.isPrivate, it) }
                        }
                        SettingsPage.ACCESS -> {
                            Text("Tree Access", style = MaterialTheme.typography.headlineMedium)
                            Text("Demo tree code: ${state.treeCode}", style = MaterialTheme.typography.titleMedium)
                            Text("Use this code in Find Others. This local member list does not grant online access.", color = WarmGrey)
                            state.people.forEach { person -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Avatar(person, Modifier.size(40.dp)); Text(person.name) } }
                        }
                        SettingsPage.NOTIFICATIONS -> {
                            Text("Notifications", style = MaterialTheme.typography.headlineMedium)
                            Text("Local demo preference. Notification delivery will require a future service integration.", color = WarmGrey)
                            ToggleRow("Family updates", state.settings.notifications, !state.saving, onNotifications)
                        }
                        null -> Unit
                    }
                }
            }
        }
    }
}
@Composable private fun SettingsRow(title: String, icon: ImageVector, chevron: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(20.dp).heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, tint = SoftBrown); Text(title, Modifier.weight(1f)); if (chevron) Icon(Icons.Outlined.ChevronRight, null, tint = WarmGrey)
    }
}
@Composable private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onChange, enabled = enabled) }
}
