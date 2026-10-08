package com.app.familytree.feature.settings

import com.app.familytree.core.data.*
import com.app.familytree.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class SettingsPage { PROFILE, PRIVACY, ACCESS, NOTIFICATIONS }
data class SettingsViewState(val settings: AppSettings = AppSettings(), val page: SettingsPage? = null, val profileName: String = "John Doe", val profileAvatar: String = "", val people: List<Person> = emptyList(), val treeCode: String = "", val signedIn: Boolean = true, val error: String? = null, val saving: Boolean = false)
class SettingsViewModel(private val repository: FamilyRepository, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(SettingsViewState())
    val state = mutableState.asStateFlow()
    init { scope.launch { repository.state.collect { source -> source.data?.let { data ->
        val person = data.tree.people.first { it.isCurrentUser }
        mutableState.value = state.value.copy(settings = data.settings, people = data.tree.people, treeCode = data.tree.code, signedIn = data.session.signedIn, error = source.error,
            profileName = if (state.value.page == SettingsPage.PROFILE) state.value.profileName else person.name,
            profileAvatar = if (state.value.page == SettingsPage.PROFILE) state.value.profileAvatar else person.avatarUrl?.takeUnless { it.startsWith("asset:") }.orEmpty())
    } } } }
    fun open(page: SettingsPage?) {
        if (state.value.saving) return
        val person = repository.state.value.data?.tree?.people?.firstOrNull { it.isCurrentUser }
        mutableState.value = state.value.copy(page = page, error = null, profileName = person?.name ?: "John Doe", profileAvatar = person?.avatarUrl?.takeUnless { it.startsWith("asset:") }.orEmpty())
    }
    fun editProfile(name: String, avatar: String) { mutableState.value = state.value.copy(profileName = name, profileAvatar = avatar, error = null) }
    private fun update(transform: (AppData) -> AppData, close: Boolean = false) {
        if (state.value.saving) return
        mutableState.value = state.value.copy(saving = true)
        scope.launch {
            val result = repository.update(transform)
            mutableState.value = state.value.copy(saving = false, error = result.exceptionOrNull()?.message, page = if (close && result.isSuccess) null else state.value.page)
        }
    }
    fun saveProfile() {
        val name = state.value.profileName.trim(); val avatar = state.value.profileAvatar.trim()
        if (name.isEmpty() || avatar.isNotEmpty() && !avatar.startsWith("https://")) {
            mutableState.value = state.value.copy(error = "Enter a name and an HTTPS avatar URL (or leave it blank)."); return
        }
        update({ data -> data.copy(tree = data.tree.copy(people = data.tree.people.map { if (it.isCurrentUser) it.copy(name = name, avatarUrl = avatar.ifEmpty { it.avatarUrl?.takeIf { url -> url.startsWith("asset:") } }) else it })) }, close = true)
    }
    fun privacy(private: Boolean, discoverable: Boolean) { update({ it.copy(settings = it.settings.copy(isPrivate = private, discoverable = discoverable)) }) }
    fun notifications(enabled: Boolean) { update({ it.copy(settings = it.settings.copy(notifications = enabled)) }) }
    fun session(signedIn: Boolean) { update({ it.copy(session = Session(signedIn)) }, close = true) }
}
