package com.app.familytree.core.data

import com.app.familytree.core.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CancellationException

interface Storage { suspend fun read(): String?; suspend fun write(value: String) }
data class RepositoryState(val data: AppData? = null, val loading: Boolean = true, val error: String? = null)
interface FamilyRepository {
    val state: StateFlow<RepositoryState>
    suspend fun load()
    suspend fun update(transform: (AppData) -> AppData): Result<Unit>
}
class LocalFamilyRepository(private val storage: Storage, private val newId: () -> String = { kotlin.random.Random.nextBytes(16).joinToString("") { "%02x".format(it) } }, private val now: () -> Long = { System.currentTimeMillis() }) : FamilyRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(RepositoryState())
    override val state = mutableState.asStateFlow()
    override suspend fun load() = mutex.withLock {
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        try {
            val raw = storage.read()
            val saved = if (raw == null) DemoData.initial() else json.decodeFromString<AppData>(raw)
            require(saved.version in 1..2) { "Unsupported saved-data version. Your file has been preserved." }
            val data = saved.copy(version = 2, treeId = saved.treeId.ifBlank { newId() })
            Relationships.validate(data.tree)
            if (raw == null || saved != data) storage.write(json.encodeToString(data))
            mutableState.value = RepositoryState(data, loading = false)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mutableState.value = mutableState.value.copy(loading = false, error = e.message ?: "Could not load saved data.")
        }
    }
    override suspend fun update(transform: (AppData) -> AppData): Result<Unit> = mutex.withLock {
        try {
            val old = checkNotNull(state.value.data) { "Load your saved data before making changes." }
            var next = transform(old)
            require(next.treeId == old.treeId && next.version == 2) { "Local tree identity cannot change." }
            Relationships.validate(next.tree)
            val details = ChangeHistory.localDetails(old, next)
            if (details.isNotEmpty() && next.logs == old.logs) {
                val actor = old.tree.people.first { it.isCurrentUser }.name
                next = next.copy(logs = next.logs + ChangeLog(newId(), now(), actor, if (details.size == 1) details.first() else "${details.size} changes by $actor", details))
            }
            storage.write(json.encodeToString(next))
            mutableState.value = RepositoryState(next, false)
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mutableState.value = mutableState.value.copy(error = e.message ?: "Could not save. Try again.")
            Result.failure(e)
        }
    }
}
