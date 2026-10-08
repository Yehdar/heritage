package com.app.familytree.feature.findothers

import com.app.familytree.core.data.*
import com.app.familytree.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class FindOthersViewState(val searchQuery: String = "", val suggestions: List<Person> = DemoData.suggestions, val connectedUserIds: Set<String> = emptySet(), val form: RelationForm? = null, val anchorName: String = "", val error: String? = null)
class FindOthersViewModel(private val repository: FamilyRepository, private val scope: CoroutineScope, private val selectedId: () -> String?) {
    private val mutableState = MutableStateFlow(FindOthersViewState())
    val state = mutableState.asStateFlow()
    init { scope.launch { repository.state.collect { refresh() } } }
    private fun refresh() {
        val data = repository.state.value.data
        val query = state.value.searchQuery.trim()
        mutableState.value = state.value.copy(suggestions = DemoData.suggestions.filter { query.isEmpty() || it.name.contains(query, true) || data?.tree?.code.equals(query, true) }, connectedUserIds = data?.connectedUserIds.orEmpty(), error = repository.state.value.error)
    }
    fun search(query: String) { mutableState.value = state.value.copy(searchQuery = query); refresh() }
    fun connect(person: Person) {
        val data = repository.state.value.data ?: return
        val anchor = data.tree.people.find { it.id == selectedId() } ?: data.tree.people.first { it.isCurrentUser }
        mutableState.value = state.value.copy(form = RelationForm(anchor.id, existingPerson = person, name = person.name), anchorName = anchor.name)
    }
    fun choose(type: RelationshipType) { mutableState.value = state.value.copy(form = state.value.form?.copy(type = type, error = null)) }
    fun dismiss() { if (state.value.form?.saving != true) mutableState.value = state.value.copy(form = null) }
    fun save() {
        val form = state.value.form ?: return
        if (form.saving) return
        val person = form.existingPerson ?: return
        mutableState.value = state.value.copy(form = form.copy(saving = true))
        scope.launch {
            val result = repository.update {
                require(person.id !in it.connectedUserIds) { "This person is already connected." }
                it.copy(tree = Relationships.add(it.tree, form.anchorId, person, form.type), connectedUserIds = it.connectedUserIds + person.id)
            }
            mutableState.value = state.value.copy(form = if (result.isSuccess) null else form.copy(error = result.exceptionOrNull()?.message))
        }
    }
}
