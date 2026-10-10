package com.app.familytree.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.familytree.AppOwner
import com.app.familytree.core.theme.*
import com.app.familytree.core.ui.ErrorMessage
import com.app.familytree.feature.tree.*
import com.app.familytree.feature.sharing.*
import com.app.familytree.feature.logs.*
import com.app.familytree.sharing.ShareQr
import com.app.familytree.feature.settings.*

@Composable fun FamilyTreeApp(owner: AppOwner, onShare: (String) -> Unit = {}, onScan: () -> Unit = {}) {
    val tree by owner.tree.state.collectAsStateWithLifecycle()
    val sharing by owner.sharing.state.collectAsStateWithLifecycle()
    val logs by owner.logs.state.collectAsStateWithLifecycle()
    val settings by owner.settings.state.collectAsStateWithLifecycle()
    val repository by owner.repository.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var viewport by remember { mutableStateOf(GraphViewport()) }
    LaunchedEffect(sharing.openRequest) { if (sharing.openRequest > 0) tab = 1 }
    val modalOpen = when (tab) { 0 -> tree.form != null || tree.isAddMenuExpanded || tree.deleteNodeId != null; 1 -> sharing.draft != null; 2 -> settings.page != null; else -> false }
    BackHandler(modalOpen || tab != 0) {
        when {
            modalOpen && tab == 0 -> owner.tree.dismiss()
            modalOpen && tab == 1 -> owner.sharing.dismiss()
            modalOpen -> owner.settings.open(null)
            else -> tab = 0
        }
    }
    if (repository.data == null) {
        Surface(Modifier.fillMaxSize(), color = Beige) { Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Heritage", style = MaterialTheme.typography.headlineLarge)
            if (repository.loading) { Spacer(Modifier.height(24.dp)); CircularProgressIndicator() }
            ErrorMessage(repository.error, owner.tree::retry)
        } }
        return
    }
    if (!settings.signedIn) {
        Surface(Modifier.fillMaxSize(), color = Beige) { Column(Modifier.safeDrawingPadding().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Our stories, connected.", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp)); Text("Welcome to your local family tree. Your saved relatives are ready when you are.", color = WarmGrey)
            Spacer(Modifier.height(32.dp)); Button(onClick = { owner.settings.session(true) }, enabled = !settings.saving) { Text("Continue as ${settings.profileName}") }
            ErrorMessage(settings.error)
        } }
        return
    }
    Scaffold(containerColor = Beige, bottomBar = {
        NavigationBar(containerColor = Beige) {
            val labels = listOf("My Tree", "Sharing", "Settings", "Logs")
            val icons = listOf(Icons.Outlined.AccountTree, Icons.Outlined.QrCode, Icons.Outlined.Settings, Icons.Outlined.History)
            labels.forEachIndexed { index, label -> NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], null) }, label = { Text(label) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Beige, selectedIconColor = Espresso, selectedTextColor = Espresso, unselectedIconColor = WarmGrey, unselectedTextColor = WarmGrey)) }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            when (tab) {
                0 -> TreeScreen(tree, viewport, { pan, zoom -> viewport = viewport.copy(zoom = (viewport.zoom * zoom).coerceIn(0.5f, 8f), pan = viewport.pan + pan) }, { viewport = GraphViewport() }, owner.tree::select, owner.tree::toggleMenu, owner.tree::dismiss, owner.tree::startAdd, owner.tree::editForm, owner.tree::save, owner.tree::retry, owner.tree::requestDelete, owner.tree::confirmDelete)
                1 -> SharingScreen(sharing, owner.sharing::input, owner.sharing::createLink, onShare, onScan, { owner.sharing.receive() }, owner.sharing::revoke, owner.sharing::options, owner.sharing::match, owner.sharing::preview, owner.sharing::importTree, owner.sharing::dismiss, owner.sharing::sync, owner.sharing::checkUpdates, owner.sharing::disconnect) { ShareQr(it) }
                2 -> SettingsScreen(settings, owner.settings::open, owner.settings::editProfile, owner.settings::saveProfile, owner.settings::privacy, owner.settings::notifications, { owner.settings.session(false) })
                3 -> LogsScreen(logs, owner.logs::review)
            }
        }
    }
}
