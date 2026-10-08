package com.app.familytree

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.app.familytree.core.data.LocalFamilyRepository
import com.app.familytree.core.theme.HeritageTheme
import com.app.familytree.feature.tree.TreeViewModel
import com.app.familytree.feature.findothers.FindOthersViewModel
import com.app.familytree.feature.settings.SettingsViewModel
import com.app.familytree.navigation.FamilyTreeApp
import kotlinx.coroutines.*
import java.util.UUID

class AppOwner(val repository: LocalFamilyRepository) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val tree = TreeViewModel(repository, scope) { UUID.randomUUID().toString() }
    val find = FindOthersViewModel(repository, scope) { tree.state.value.selectedNodeId }
    val settings = SettingsViewModel(repository, scope)
    init { scope.launch { repository.load() } }
    override fun onCleared() { scope.cancel() }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val owner = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppOwner(LocalFamilyRepository(AndroidStorage(filesDir))) as T
        })[AppOwner::class.java]
        setContent { HeritageTheme { FamilyTreeApp(owner) } }
    }
}
