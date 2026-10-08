package com.app.familytree

import com.app.familytree.core.data.*
import com.app.familytree.core.model.*
import com.app.familytree.feature.tree.*
import com.app.familytree.feature.findothers.*
import com.app.familytree.feature.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreTest {
    private class MemoryStorage(var value: String? = null, var fail: Boolean = false) : Storage {
        override suspend fun read() = value
        override suspend fun write(value: String) { check(!fail) { "Disk unavailable" }; this.value = value }
    }
    private fun person(id: String) = Person(id, "New Person", null)
    private fun rejects(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }

    @Test fun allRelationshipActionsUseCanonicalLinks() {
        val tree = DemoData.initial().tree
        val parent = Relationships.add(tree, "john", person("new-parent"), RelationshipType.PARENT)
        assertTrue(parent.relationships.contains(Relationship("new-parent", "john", RelationshipType.PARENT)))
        val child = Relationships.add(tree, "john", person("new-child"), RelationshipType.CHILD)
        assertTrue(child.relationships.contains(Relationship("john", "new-child", RelationshipType.PARENT)))
        val sibling = Relationships.add(tree, "john", person("new-sibling"), RelationshipType.SIBLING)
        assertEquals(setOf("henry", "mary"), sibling.relationships.filter { it.toId == "new-sibling" }.map { it.fromId }.toSet())
        val partner = Relationships.add(tree, "john", person("new-partner"), RelationshipType.PARTNER, 2024)
        assertEquals("m. 2024", TreeLayout.graph(partner).marriages.last().yearLabel)
        rejects { Relationships.add(tree, "arthur", person("sibling"), RelationshipType.SIBLING) }
    }
    @Test fun rejectsInvalidAndCyclicRelationships() {
        val tree = DemoData.initial().tree
        rejects { Relationships.add(tree, "john", tree.people.first { it.id == "john" }, RelationshipType.CHILD) }
        rejects { Relationships.add(tree, "john", tree.people.first { it.id == "henry" }, RelationshipType.CHILD) }
        rejects { Relationships.add(tree, "john", tree.people.first { it.id == "jane" }, RelationshipType.PARTNER) }
        rejects { Relationships.add(tree, "john", Person("blank", " ", null), RelationshipType.CHILD) }
        rejects { Relationships.add(tree, "john", person("bad-year"), RelationshipType.PARTNER, 0) }
        rejects { Relationships.add(tree, "missing", person("orphan"), RelationshipType.CHILD) }
    }
    @Test fun layoutIsStableAndHasFourGenerations() {
        val tree = DemoData.initial().tree
        val graph = TreeLayout.graph(tree)
        assertEquals(graph, TreeLayout.graph(tree))
        assertEquals(11, graph.nodes.size)
        assertEquals(4, TreeLayout.generations(graph))
        assertEquals(graph.nodes.size, graph.nodes.map { it.positionX to it.positionY }.toSet().size)
    }
    @Test fun repositoryRoundTripAndFailedSavePreservesState() = runTest {
        val storage = MemoryStorage(); val repo = LocalFamilyRepository(storage); repo.load()
        assertTrue(repo.update { it.copy(session = Session(false), settings = it.settings.copy(notifications = false), connectedUserIds = setOf("sarah")) }.isSuccess)
        val restored = LocalFamilyRepository(storage); restored.load()
        assertEquals(repo.state.value.data, restored.state.value.data)
        val old = restored.state.value.data; val oldJson = storage.value
        storage.fail = true
        assertTrue(restored.update { it.copy(session = Session(true)) }.isFailure)
        assertEquals(old, restored.state.value.data); assertEquals(oldJson, storage.value)
    }
    @Test fun corruptOrFutureDataIsNotOverwritten() = runTest {
        val storage = MemoryStorage("{bad json"); val repo = LocalFamilyRepository(storage); repo.load()
        assertNull(repo.state.value.data); assertNotNull(repo.state.value.error); assertEquals("{bad json", storage.value)
        storage.value = null; repo.load()
        storage.value = storage.value!!.replace("\"version\":1", "\"version\":99")
        val raw = storage.value; val future = LocalFamilyRepository(storage); future.load()
        assertNull(future.state.value.data); assertEquals(raw, storage.value)
    }
    @Test fun searchConnectCancelAndDuplicatePrevention() = runTest {
        val repo = LocalFamilyRepository(MemoryStorage()); repo.load()
        val vm = FindOthersViewModel(repo, backgroundScope) { "john" }; runCurrent()
        vm.search("SARAH"); assertEquals(listOf("sarah"), vm.state.value.suggestions.map { it.id })
        vm.search(" doe-1999 "); assertEquals(3, vm.state.value.suggestions.size)
        vm.search("absent"); assertTrue(vm.state.value.suggestions.isEmpty())
        val sarah = DemoData.suggestions.first()
        vm.connect(sarah); vm.dismiss(); assertFalse("sarah" in repo.state.value.data!!.connectedUserIds)
        vm.connect(sarah); vm.choose(RelationshipType.SIBLING); vm.save(); runCurrent()
        assertTrue("sarah" in vm.state.value.connectedUserIds)
        assertTrue(repo.state.value.data!!.tree.people.any { it.id == "sarah" })
        val before = repo.state.value.data
        vm.connect(sarah); vm.save(); runCurrent()
        assertNotNull(vm.state.value.form!!.error); assertEquals(before, repo.state.value.data)
    }
    @Test fun treeFormValidationAndSaveAndSettingsSession() = runTest {
        val repo = LocalFamilyRepository(MemoryStorage()); repo.load()
        val tree = TreeViewModel(repo, backgroundScope) { "test-child" }; runCurrent()
        tree.startAdd(RelationshipType.CHILD); tree.save(); assertNotNull(tree.state.value.form!!.error)
        tree.editForm("Alex Doe", "", ""); tree.save(); runCurrent()
        assertNull(tree.state.value.form); assertEquals("test-child", tree.state.value.selectedNodeId)
        assertEquals(12, tree.state.value.graph.nodes.size)
        val settings = SettingsViewModel(repo, backgroundScope); runCurrent()
        settings.open(SettingsPage.PROFILE); settings.editProfile("John D.", ""); settings.saveProfile(); runCurrent()
        assertEquals("John D.", repo.state.value.data!!.tree.people.first { it.isCurrentUser }.name)
        settings.privacy(false, true); runCurrent(); settings.notifications(false); runCurrent(); settings.session(false); runCurrent()
        assertFalse(settings.state.value.signedIn); assertFalse(settings.state.value.settings.notifications)
        settings.session(true); runCurrent(); assertTrue(settings.state.value.signedIn)
        assertEquals(12, repo.state.value.data!!.tree.people.size)
    }
}
