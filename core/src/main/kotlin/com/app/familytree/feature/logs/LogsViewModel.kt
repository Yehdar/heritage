package com.app.familytree.feature.logs

import com.app.familytree.core.data.*
import com.app.familytree.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LogsViewState(val entries: List<ChangeLog> = emptyList(), val pendingCount: Int = 0, val busy: Boolean = false, val error: String? = null)
class LogsViewModel(private val repository: FamilyRepository, private val scope: CoroutineScope, private val newId: () -> String, private val now: () -> Long) {
    private val mutableState = MutableStateFlow(LogsViewState())
    val state = mutableState.asStateFlow()
    init { scope.launch { repository.state.collect { source ->
        val entries = source.data?.logs.orEmpty().asReversed()
        mutableState.value = state.value.copy(entries = entries, pendingCount = entries.count { it.status == LogStatus.PENDING })
    } } }
    fun review(id: String, accept: Boolean) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, error = null)
        scope.launch {
            val result = repository.update { data ->
                val entry = data.logs.first { it.id == id }
                require(entry.status == LogStatus.PENDING) { "This change has already been reviewed or superseded." }
                val snapshot = checkNotNull(entry.proposal)
                val link = data.syncLinks.firstOrNull { it.treeId == entry.sourceTreeId && it.liveSync } ?: error("This tree is no longer linked for live sync.")
                val (tree, updatedLink) = if (accept) TreeIntegration.update(data.tree, link, snapshot) else data.tree to link
                val actor = data.tree.people.first { it.isCurrentUser }.name
                val decision = ChangeLog(newId(), now(), actor, "${if (accept) "Accepted" else "Rejected"} changes by ${entry.actorName}", if (accept) ChangeHistory.treeDetails(data.tree, tree).ifEmpty { listOf("No local tree changes were needed.") } else listOf("Revision ${snapshot.revision} was rejected; your tree was preserved."), sourceTreeId = entry.sourceTreeId)
                data.copy(tree = tree, syncLinks = data.syncLinks.map { if (it.treeId == link.treeId) updatedLink else it }, logs = data.logs.map { if (it.id == id) it.copy(status = if (accept) LogStatus.APPLIED else LogStatus.REJECTED) else it } + decision)
            }
            mutableState.value = state.value.copy(busy = false, error = result.exceptionOrNull()?.message)
        }
    }
}
