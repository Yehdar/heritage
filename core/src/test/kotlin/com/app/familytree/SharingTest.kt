package com.app.familytree

import com.app.familytree.core.data.*
import com.app.familytree.core.model.*
import com.app.familytree.feature.logs.*
import com.app.familytree.feature.sharing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharingTest {
    private class Memory(var raw: String? = null, var failing: Boolean = false) : Storage {
        override suspend fun read() = raw
        override suspend fun write(value: String) { check(!failing) { "Disk full" }; raw = value }
    }
    private class Remote(var snapshot: TreeSnapshot) : SharingRepository {
        val updates = MutableSharedFlow<TreeSnapshot>(replay = 1).also { it.tryEmit(snapshot) }
        override val available = true
        override val setupMessage: String? = null
        override fun url(token: String) = "https://example.test/tree/$token"
        override fun tokenFromUrl(url: String): String { require(url == "https://example.test/tree/token"); return "token" }
        override suspend fun publish(token: String, snapshot: TreeSnapshot) = snapshot
        override suspend fun fetch(token: String) = snapshot
        override fun observe(token: String) = updates
        override suspend fun revoke(token: String) = Unit
    }
    private fun source(revision: Long = 1): TreeSnapshot = TreeSnapshot(treeId = "remote", revision = revision, ownerName = "Sarah", ownerPersonId = "remote:sarah", people = listOf(Person("remote:sarah", "Sarah", null), Person("remote:child", "Alex", null)), relationships = listOf(Relationship("remote:sarah", "remote:child", RelationshipType.PARENT)))
    private fun local() = DemoData.initial().copy(treeId = "local")
    private fun draft(snapshot: TreeSnapshot = source(), overwrite: Boolean = false, map: Map<String, String> = emptyMap(), live: Boolean = true) = ImportDraft("token", snapshot, "john", RelationshipType.PARTNER, overwrite, live, map)

    @Test fun importSeparatesSameNamesAndKeepsLocalIdentityAndOnlyExplicitMatchesOverwrite() {
        val initial = local()
        val remote = source().copy(people = source().people.map { if (it.id == "remote:sarah") it.copy(name = "John Doe") else it })
        val (tree, _) = TreeIntegration.initial(initial, draft(remote))
        assertEquals(13, tree.people.size)
        assertEquals(2, tree.people.count { it.name == "John Doe" })
        assertEquals("john", tree.people.single { it.isCurrentUser }.id)
        val (keep, _) = TreeIntegration.initial(initial, draft(map = mapOf("remote:sarah" to "john")))
        assertEquals("John Doe", keep.people.first { it.id == "john" }.name)
        val (replace, _) = TreeIntegration.initial(initial, draft(overwrite = true, map = mapOf("remote:sarah" to "john")))
        assertEquals("Sarah", replace.people.first { it.id == "john" }.name)
        assertTrue(replace.people.first { it.id == "john" }.isCurrentUser)
        assertEquals(12, replace.people.size)
    }
    @Test fun remoteAdditionsRemovalsAndLocalConflictAreHandledWithoutDeletingLocalWork() {
        val (tree, link) = TreeIntegration.initial(local(), draft())
        val removed = source(2).copy(people = listOf(source().people.first()), relationships = emptyList())
        val (updated, nextLink) = TreeIntegration.update(tree, link, removed)
        assertFalse(updated.people.any { it.id == "remote:child" })
        assertEquals(12, updated.people.size)
        assertEquals(2L, nextLink.baseline.revision)
        val edited = tree.copy(people = tree.people.map { if (it.id == "remote:child") it.copy(name = "Local edit") else it })
        assertTrue(runCatching { TreeIntegration.update(edited, link, removed) }.isFailure)
        val locallyConnected = Relationships.add(tree, "remote:child", Person("new-local", "Local child", null), RelationshipType.CHILD)
        assertTrue(runCatching { TreeIntegration.update(locallyConnected, link, removed) }.isFailure)
        val added = source(2).copy(people = source().people + Person("remote:new", "New child", null), relationships = source().relationships + Relationship("remote:sarah", "remote:new", RelationshipType.PARENT))
        val (grown, _) = TreeIntegration.update(tree, link, added)
        assertEquals(14, grown.people.size)
    }
    @Test fun matchedLocalPeopleAndRelationshipsAreNeverRemovedBySourceDeletion() {
        val (tree, link) = TreeIntegration.initial(local(), draft(map = mapOf("remote:child" to "emma")))
        val incoming = source(2).copy(people = listOf(source().people.first()), relationships = emptyList())
        val (result, _) = TreeIntegration.update(tree, link, incoming)
        assertTrue(result.people.any { it.id == "emma" })
        assertTrue(result.relationships.contains(Relationship("john", "emma", RelationshipType.PARENT)))
    }
    @Test fun invalidImportsAndCyclesAreRejected() {
        assertTrue(runCatching { TreeIntegration.initial(local(), draft(source().copy(treeId = "local"))) }.isFailure)
        assertTrue(runCatching { TreeIntegration.initial(local(), draft(map = mapOf("remote:sarah" to "john", "remote:child" to "john"))) }.isFailure)
        assertTrue(runCatching { TreeIntegration.initial(local(), draft(map = mapOf("remote:sarah" to "emma", "remote:child" to "john"))) }.isFailure)
        assertTrue(runCatching { TreeIntegration.validate(source().copy(people = source().people + source().people.first())) }.isFailure)
    }
    @Test fun migrationHistoryAndAtomicFailuresPersistTogether() = runTest {
        val memory = Memory(Json.encodeToString(DemoData.initial().copy(version = 1)))
        var ids = 0
        val repo = LocalFamilyRepository(memory, { "id-${++ids}" }, { 1234 }); repo.load()
        assertEquals(2, repo.state.value.data!!.version)
        assertTrue(repo.state.value.data!!.treeId.isNotBlank())
        repo.update { it.copy(settings = it.settings.copy(notifications = false)) }
        assertEquals(1, repo.state.value.data!!.logs.size)
        assertEquals(1234L, repo.state.value.data!!.logs.single().timestampMillis)
        val before = repo.state.value.data; val serialized = memory.raw
        memory.failing = true; repo.update { it.copy(session = Session(false)) }
        assertEquals(before, repo.state.value.data); assertEquals(serialized, memory.raw)
        memory.failing = false
        val restored = LocalFamilyRepository(memory); restored.load()
        assertEquals(before, restored.state.value.data)
    }
    @Test fun cancelledImportReviewRejectDedupAndSupersedingArePersistent() = runTest {
        val memory = Memory(); var ids = 0
        val repo = LocalFamilyRepository(memory, { "id-${++ids}" }, { 1 }); repo.load()
        val remote = Remote(source()); val vm = SharingViewModel(repo, remote, backgroundScope, { "john" }, { "event-${++ids}" }, { 2 })
        runCurrent()
        vm.input(remote.url("token")); vm.receive(); runCurrent(); vm.dismiss()
        assertEquals(11, repo.state.value.data!!.tree.people.size)
        vm.receive(); runCurrent(); vm.preview(); assertTrue(vm.state.value.draft!!.previewReady)
        vm.importTree(); runCurrent()
        assertEquals(13, repo.state.value.data!!.tree.people.size)
        val next = source(2).copy(people = source().people.map { if (it.id == "remote:child") it.copy(name = "Alex Changed") else it })
        remote.updates.emit(next); runCurrent()
        assertEquals("Alex", repo.state.value.data!!.tree.people.first { it.id == "remote:child" }.name)
        assertEquals(1, repo.state.value.data!!.logs.count { it.status == LogStatus.PENDING })
        remote.updates.emit(next); runCurrent(); assertEquals(1, repo.state.value.data!!.logs.count { it.status == LogStatus.PENDING })
        val logs = LogsViewModel(repo, backgroundScope, { "decision-${++ids}" }, { 3 }); runCurrent()
        logs.review(logs.state.value.entries.first { it.status == LogStatus.PENDING }.id, false); runCurrent()
        assertEquals("Alex", repo.state.value.data!!.tree.people.first { it.id == "remote:child" }.name)
        remote.updates.emit(next); runCurrent(); assertEquals(0, logs.state.value.pendingCount)
        remote.updates.emit(next.copy(revision = 3)); runCurrent()
        remote.updates.emit(next.copy(revision = 4)); runCurrent()
        assertEquals(1, logs.state.value.pendingCount)
        assertTrue(logs.state.value.entries.any { it.status == LogStatus.SUPERSEDED })
        logs.review(logs.state.value.entries.first { it.status == LogStatus.PENDING }.id, true); runCurrent()
        assertEquals("Alex Changed", repo.state.value.data!!.tree.people.first { it.id == "remote:child" }.name)
        val restored = LocalFamilyRepository(memory); restored.load()
        assertEquals(repo.state.value.data, restored.state.value.data)
    }
    @Test fun importRequiresFreshPreviewAfterLocalChanges() = runTest {
        val repo = LocalFamilyRepository(Memory()); repo.load()
        val vm = SharingViewModel(repo, Remote(source()), backgroundScope, { "john" }, { "log" }, { 1 }); runCurrent()
        vm.input("https://example.test/tree/token"); vm.receive(); runCurrent(); vm.preview()
        repo.update { data -> data.copy(tree = data.tree.copy(people = data.tree.people.map { if (it.id == "john") it.copy(name = "John Updated") else it })) }
        runCurrent(); vm.importTree(); runCurrent()
        assertEquals(11, repo.state.value.data!!.tree.people.size)
        assertTrue(vm.state.value.error!!.contains("changed since the preview"))
        vm.preview(); vm.importTree(); runCurrent()
        assertEquals(13, repo.state.value.data!!.tree.people.size)
    }
    @Test fun oneTimeImportDoesNotQueueUpdatesAndDisconnectKeepsRelatives() = runTest {
        val repo = LocalFamilyRepository(Memory()); repo.load(); val remote = Remote(source())
        val vm = SharingViewModel(repo, remote, backgroundScope, { "john" }, { "log" }, { 1 }); runCurrent()
        vm.input(remote.url("token")); vm.receive(); runCurrent()
        vm.options(false, false, RelationshipType.PARTNER); vm.preview(); vm.importTree(); runCurrent()
        remote.updates.emit(source(2)); runCurrent()
        assertFalse(repo.state.value.data!!.logs.any { it.status == LogStatus.PENDING })
        vm.disconnect("remote"); runCurrent()
        assertTrue(repo.state.value.data!!.syncLinks.isEmpty()); assertEquals(13, repo.state.value.data!!.tree.people.size)
    }
}
