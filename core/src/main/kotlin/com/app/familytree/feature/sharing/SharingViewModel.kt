package com.app.familytree.feature.sharing

import com.app.familytree.core.data.*
import com.app.familytree.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SharingViewState(
    val available: Boolean = false,
    val setupMessage: String? = null,
    val inputUrl: String = "",
    val shareUrl: String? = null,
    val people: List<Person> = emptyList(),
    val links: List<SyncLink> = emptyList(),
    val draft: ImportDraft? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val openRequest: Int = 0
)
class SharingViewModel(
    private val repository: FamilyRepository,
    private val remote: SharingRepository,
    private val scope: CoroutineScope,
    private val selectedId: () -> String?,
    private val newId: () -> String,
    private val now: () -> Long
) {
    private val mutableState = MutableStateFlow(SharingViewState(remote.available, remote.setupMessage))
    val state = mutableState.asStateFlow()
    private val watches = mutableMapOf<String, Job>()
    private var lastPublished: TreeSnapshot? = null
    private var publishing: Job? = null
    init {
        scope.launch { repository.state.collect { source -> source.data?.let { data ->
            mutableState.value = state.value.copy(people = data.tree.people, links = data.syncLinks,
                shareUrl = if (remote.available) data.shareToken?.let(remote::url) else null)
            val live = if (data.session.signedIn && remote.available) data.syncLinks.filter { it.liveSync }.associateBy { it.treeId } else emptyMap()
            watches.keys.toList().filter { it !in live }.forEach { watches.remove(it)?.cancel() }
            live.forEach { (id, link) -> if (id !in watches) watches[id] = scope.launch { watch(link) } }
            val token = data.shareToken
            if (remote.available && data.session.signedIn && token != null) {
                val snapshot = TreeIntegration.snapshot(data)
                if (lastPublished != snapshot) {
                    publishing?.cancel()
                    publishing = scope.launch {
                        delay(500)
                        try { remote.publish(token, snapshot); lastPublished = snapshot }
                        catch (e: Exception) { if (e is CancellationException) throw e; report("Could not update your shared tree: ${e.message}") }
                    }
                }
            } else {
                publishing?.cancel()
                publishing = null
            }
        } } }
    }
    private fun report(error: String) { mutableState.value = state.value.copy(error = error) }
    fun input(url: String) { mutableState.value = state.value.copy(inputUrl = url, error = null, message = null) }
    fun openLink(url: String) { input(url); mutableState.value = state.value.copy(openRequest = state.value.openRequest + 1) }
    fun dismiss() { if (!state.value.busy) mutableState.value = state.value.copy(draft = null) }
    fun scannerError(message: String) = report(message)
    fun createLink() = work {
        val data = checkNotNull(repository.state.value.data)
        require(remote.available) { remote.setupMessage.orEmpty() }
        val token = data.shareToken ?: newId().replace("-", "")
        val snapshot = TreeIntegration.snapshot(data)
        remote.publish(token, snapshot)
        lastPublished = snapshot
        val saved = repository.update { it.copy(shareToken = token) }
        if (saved.isFailure && data.shareToken == null) runCatching { remote.revoke(token) }
        saved.getOrThrow()
        mutableState.value = state.value.copy(message = "Your link is ready. Anyone with this link can view the shared tree.")
    }
    fun revoke() = work {
        val token = repository.state.value.data?.shareToken ?: return@work
        remote.revoke(token)
        lastPublished = null
        repository.update { it.copy(shareToken = null) }.getOrThrow()
        mutableState.value = state.value.copy(message = "Sharing link revoked.")
    }
    fun receive(url: String = state.value.inputUrl) = work {
        val token = remote.tokenFromUrl(url.trim())
        val snapshot = remote.fetch(token); TreeIntegration.validate(snapshot)
        val data = checkNotNull(repository.state.value.data)
        require(snapshot.treeId != data.treeId) { "This link belongs to your own tree." }
        require(data.syncLinks.none { it.treeId == snapshot.treeId }) { "This tree is already linked. Check for updates below." }
        mutableState.value = state.value.copy(inputUrl = url, draft = ImportDraft(token, snapshot, selectedId()?.takeIf { id -> data.tree.people.any { it.id == id } } ?: data.tree.people.first { it.isCurrentUser }.id))
    }
    fun options(overwrite: Boolean, live: Boolean, relationship: RelationshipType) {
        mutableState.value = state.value.copy(draft = state.value.draft?.copy(overwriteMatching = overwrite, liveSync = live, relationship = relationship, previewReady = false), error = null)
    }
    fun match(sourceId: String, localId: String?) {
        val draft = state.value.draft ?: return
        val map = draft.personMapping.toMutableMap(); if (localId == null) map.remove(sourceId) else map[sourceId] = localId
        mutableState.value = state.value.copy(draft = draft.copy(personMapping = map, previewReady = false), error = null)
    }
    fun preview() {
        try {
            val data = checkNotNull(repository.state.value.data); val draft = checkNotNull(state.value.draft)
            val (tree, _) = TreeIntegration.initial(data, draft)
            mutableState.value = state.value.copy(draft = draft.copy(preview = ChangeHistory.treeDetails(data.tree, tree), previewReady = true, previewBaseTree = data.tree), error = null)
        } catch (e: Exception) { report(e.message ?: "Could not preview import.") }
    }
    fun importTree() = work {
        val draft = checkNotNull(state.value.draft)
        require(draft.previewReady) { "Preview your changes first." }
        repository.update { data ->
            require(data.tree == draft.previewBaseTree) { "Your tree changed since the preview. Preview the import again before confirming." }
            val (tree, link) = TreeIntegration.initial(data, draft)
            data.copy(tree = tree, syncLinks = data.syncLinks + link, logs = data.logs + ChangeLog(newId(), now(), draft.snapshot.ownerName, "Imported ${draft.snapshot.ownerName}'s tree", ChangeHistory.treeDetails(data.tree, tree) + "${if (draft.liveSync) "Live sync enabled: future changes require review" else "One-time import; live sync disabled"}", sourceTreeId = draft.snapshot.treeId))
        }.getOrThrow()
        mutableState.value = state.value.copy(draft = null, message = "Tree imported. Changes are recorded in Logs.")
    }
    private suspend fun watch(link: SyncLink) {
        try { remote.observe(link.token).collect { incoming -> queue(link.treeId, incoming) } }
        catch (e: Exception) { if (e is CancellationException) throw e; report("Sync with ${link.ownerName} stopped: ${e.message}. Use Check updates to retry.") }
    }
    private suspend fun queue(treeId: String, snapshot: TreeSnapshot) {
        TreeIntegration.validate(snapshot)
        repository.update { data ->
            val link = data.syncLinks.find { it.treeId == treeId && it.liveSync } ?: return@update data
            require(snapshot.treeId == treeId) { "Shared tree identity changed." }
            if (snapshot.revision <= link.baseline.revision || data.logs.any { it.sourceTreeId == treeId && it.proposal?.revision == snapshot.revision && it.status != LogStatus.SUPERSEDED }) return@update data
            if (data.logs.any { it.sourceTreeId == treeId && (it.proposal?.revision ?: -1) > snapshot.revision }) return@update data
            val baselineTree = FamilyTree(link.baseline.people, link.baseline.relationships)
            val remoteTree = FamilyTree(snapshot.people, snapshot.relationships)
            val details = ChangeHistory.treeDetails(baselineTree, remoteTree)
            if (details.isEmpty()) return@update data.copy(syncLinks = data.syncLinks.map { if (it.treeId == treeId) it.copy(baseline = snapshot, ownerName = snapshot.ownerName) else it })
            data.copy(logs = data.logs.map { if (it.sourceTreeId == treeId && it.status == LogStatus.PENDING) it.copy(status = LogStatus.SUPERSEDED) else it } +
                ChangeLog(newId(), now(), snapshot.ownerName, "Changes by ${snapshot.ownerName}", details, LogStatus.PENDING, treeId, snapshot))
        }.getOrThrow()
    }
    fun checkUpdates(treeId: String) = work {
        val link = checkNotNull(repository.state.value.data?.syncLinks?.find { it.treeId == treeId })
        require(link.liveSync) { "Enable live sync before checking updates." }
        queue(treeId, remote.fetch(link.token))
        watches.remove(treeId)?.cancel()
        watches[treeId] = scope.launch { watch(link) }
        mutableState.value = state.value.copy(message = "Checked updates from ${link.ownerName}. Open Logs to review pending changes.")
    }
    fun sync(treeId: String, enabled: Boolean) = work {
        require(remote.available || !enabled) { remote.setupMessage.orEmpty() }
        repository.update { data ->
            val link = data.syncLinks.first { it.treeId == treeId }
            val actor = data.tree.people.first { it.isCurrentUser }.name
            data.copy(syncLinks = data.syncLinks.map { if (it.treeId == treeId) it.copy(liveSync = enabled) else it },
                logs = (if (enabled) data.logs else data.logs.map { if (it.sourceTreeId == treeId && it.status == LogStatus.PENDING) it.copy(status = LogStatus.SUPERSEDED) else it }) +
                    ChangeLog(newId(), now(), actor, "${if (enabled) "Enabled" else "Paused"} live sync with ${link.ownerName}", listOf("${if (enabled) "Future changes will wait for review" else "Pending reviews were closed; imported relatives remain"}."), sourceTreeId = treeId))
        }.getOrThrow()
    }
    fun disconnect(treeId: String) = work {
        repository.update { data ->
            val link = data.syncLinks.first { it.treeId == treeId }
            data.copy(syncLinks = data.syncLinks.filterNot { it.treeId == treeId }, logs = data.logs.map { if (it.sourceTreeId == treeId && it.status == LogStatus.PENDING) it.copy(status = LogStatus.SUPERSEDED) else it } +
                ChangeLog(newId(), now(), data.tree.people.first { it.isCurrentUser }.name, "Disconnected ${link.ownerName}'s tree", listOf("Imported relatives remain; future updates will not be received."), sourceTreeId = treeId))
        }.getOrThrow()
        mutableState.value = state.value.copy(message = "Tree disconnected. Imported relatives remain in your tree.")
    }
    private fun work(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, error = null, message = null)
        scope.launch {
            try { action() }
            catch (e: Exception) { if (e is CancellationException) throw e; report(e.message ?: "Sharing failed. Try again.") }
            finally { mutableState.value = state.value.copy(busy = false) }
        }
    }
}
