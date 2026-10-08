package com.app.familytree.feature.tree

import com.app.familytree.core.data.*
import com.app.familytree.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class TreeViewState(val graph: TreeGraph = TreeGraph(emptyList(), emptyList(), emptyList()), val selectedNodeId: String? = "john", val isAddMenuExpanded: Boolean = false, val form: RelationForm? = null, val subtitle: String = "", val loading: Boolean = true, val error: String? = null)
class TreeViewModel(private val repository: FamilyRepository, private val scope: CoroutineScope, private val newId: () -> String) {
    private val mutableState = MutableStateFlow(TreeViewState())
    val state = mutableState.asStateFlow()
    init { scope.launch { repository.state.collect { source ->
        val graph = source.data?.tree?.let(TreeLayout::graph) ?: state.value.graph
        mutableState.value = state.value.copy(graph = graph, loading = source.loading, error = source.error,
            subtitle = "${graph.nodes.size} people · ${TreeLayout.generations(graph)} generations · ${if (source.data?.settings?.isPrivate != false) "Private" else "Shared locally"}")
    } } }
    fun select(id: String) { mutableState.value = state.value.copy(selectedNodeId = id, isAddMenuExpanded = false) }
    fun toggleMenu() { mutableState.value = state.value.copy(isAddMenuExpanded = !state.value.isAddMenuExpanded) }
    fun dismiss() { if (state.value.form?.saving != true) mutableState.value = state.value.copy(isAddMenuExpanded = false, form = null) }
    fun startAdd(type: RelationshipType) { state.value.selectedNodeId?.let { mutableState.value = state.value.copy(isAddMenuExpanded = false, form = RelationForm(it, type)) } }
    fun editForm(name: String, avatar: String, year: String) { mutableState.value = state.value.copy(form = state.value.form?.copy(name = name, avatarUrl = avatar, year = year, error = null)) }
    fun save() {
        val form = state.value.form ?: return
        if (form.saving) return
        val name = form.name.trim(); val avatar = form.avatarUrl.trim()
        val error = when {
            name.isEmpty() -> "Enter a name."
            avatar.isNotEmpty() && !avatar.startsWith("https://") -> "Avatar URL must start with https://."
            form.type == RelationshipType.PARTNER && form.year.isNotBlank() && form.year.toIntOrNull() !in 1..9999 -> "Enter a year between 1 and 9999."
            else -> null
        }
        if (error != null) { mutableState.value = state.value.copy(form = form.copy(error = error)); return }
        mutableState.value = state.value.copy(form = form.copy(saving = true))
        scope.launch {
            val person = Person(newId(), name, avatar.ifEmpty { null })
            val result = repository.update { it.copy(tree = Relationships.add(it.tree, form.anchorId, person, form.type, if (form.type == RelationshipType.PARTNER) form.year.toIntOrNull() else null)) }
            mutableState.value = if (result.isSuccess) state.value.copy(form = null, selectedNodeId = person.id) else state.value.copy(form = form.copy(error = result.exceptionOrNull()?.message))
        }
    }
    fun retry() { scope.launch { repository.load() } }
}
