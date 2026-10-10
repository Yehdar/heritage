package com.app.familytree

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.app.familytree.core.data.LocalFamilyRepository
import com.app.familytree.core.theme.HeritageTheme
import com.app.familytree.feature.tree.TreeViewModel
import com.app.familytree.feature.sharing.SharingViewModel
import com.app.familytree.feature.logs.LogsViewModel
import com.app.familytree.core.data.SharingRepository
import com.app.familytree.core.data.UnconfiguredSharingRepository
import com.app.familytree.sharing.FirebaseSharingRepository
import com.app.familytree.feature.settings.SettingsViewModel
import com.app.familytree.navigation.FamilyTreeApp
import kotlinx.coroutines.*
import java.util.UUID

class AppOwner(val repository: LocalFamilyRepository, remote: SharingRepository = UnconfiguredSharingRepository()) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val tree = TreeViewModel(repository, scope) { UUID.randomUUID().toString() }
    val sharing = SharingViewModel(repository, remote, scope, { tree.state.value.selectedNodeId }, { UUID.randomUUID().toString() }, { System.currentTimeMillis() })
    val logs = LogsViewModel(repository, scope, { UUID.randomUUID().toString() }, { System.currentTimeMillis() })
    val settings = SettingsViewModel(repository, scope)
    init { scope.launch { repository.load() } }
    override fun onCleared() { scope.cancel() }
}
class MainActivity : ComponentActivity() {
    private lateinit var owner: AppOwner
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        owner = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppOwner(LocalFamilyRepository(AndroidStorage(filesDir), { UUID.randomUUID().toString() }), FirebaseSharingRepository.create(applicationContext)) as T
        })[AppOwner::class.java]
        intent.dataString?.let(owner.sharing::openLink)
        setContent { HeritageTheme { FamilyTreeApp(owner, onShare = { url ->
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }, "Share your family tree"))
        }, onScan = {
            val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder().setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this, options).startScan()
                .addOnSuccessListener { barcode -> barcode.rawValue?.let { owner.sharing.openLink(it); owner.sharing.receive(it) } }
                .addOnFailureListener { owner.sharing.scannerError("Could not scan QR. Check Google Play services or paste the URL instead.") }
        }) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); intent.dataString?.let(owner.sharing::openLink) }
}
