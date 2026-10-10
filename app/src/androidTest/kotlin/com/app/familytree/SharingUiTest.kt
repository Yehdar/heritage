package com.app.familytree

import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.app.familytree.core.data.*
import com.app.familytree.core.model.*
import com.app.familytree.core.theme.HeritageTheme
import com.app.familytree.navigation.FamilyTreeApp
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*

class SharingUiTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val snapshot = TreeSnapshot(treeId = "remote", revision = 1, ownerName = "Sarah", ownerPersonId = "remote:sarah", people = listOf(Person("remote:sarah", "Sarah", null), Person("remote:alex", "Alex", null)), relationships = listOf(Relationship("remote:sarah", "remote:alex", RelationshipType.PARENT)))
    private val updates = MutableStateFlow(snapshot)
    private val remote = object : SharingRepository {
        override val available = true
        override val setupMessage: String? = null
        override fun url(token: String) = "https://test.example/tree/token"
        override fun tokenFromUrl(url: String) = "token"
        override suspend fun publish(token: String, snapshot: TreeSnapshot) = snapshot
        override suspend fun fetch(token: String) = updates.value
        override fun observe(token: String) = updates
        override suspend fun revoke(token: String) = Unit
    }
    @Before fun setup() {
        compose.setContent {
            val owner = remember { AppOwner(LocalFamilyRepository(object : Storage {
                var saved: String? = null
                override suspend fun read() = saved
                override suspend fun write(value: String) { saved = value }
            }), remote).also { store.put("app", it) } }
            HeritageTheme { FamilyTreeApp(owner) }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Our living tree").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() } }
    private fun receive() {
        compose.onNodeWithText("Sharing").performClick()
        compose.onNodeWithText("Tree URL").performScrollTo().performTextInput("https://test.example/tree/token")
        compose.onNodeWithText("Open shared tree").performScrollTo().performClick()
        compose.onNodeWithText("Do you want their tree to overwrite existing parts of yours?").assertExists()
        compose.onNodeWithText("Do you want to live-sync with their tree?").assertExists()
    }
    @Test fun importRequiresPreviewAndConfirmationAndUpdatesWaitForReview() {
        receive()
        compose.onNodeWithText("Confirm import").assertDoesNotExist()
        compose.onNodeWithText("Preview changes").performScrollTo().performClick()
        compose.onNodeWithText("Confirm import").performScrollTo().performClick()
        compose.onNodeWithText("My Tree").performClick()
        compose.onNodeWithContentDescription("Select Sarah").assertExists()
        compose.runOnIdle { updates.value = snapshot.copy(revision = 2, people = snapshot.people.map { if (it.name == "Alex") it.copy(name = "Alexander") else it }) }
        compose.onNodeWithText("Logs").performClick()
        compose.onNodeWithText("Changes by Sarah").assertExists()
        compose.onNodeWithText("Accept").performScrollTo().performClick()
        compose.onNodeWithText("My Tree").performClick()
        compose.onNodeWithContentDescription("Select Alexander").assertExists()
    }
    @Test fun cancellingAnImportLeavesTreeUntouched() {
        receive()
        compose.onNodeWithText("Cancel import").performScrollTo().performClick()
        compose.onNodeWithText("My Tree").performClick()
        compose.onNodeWithText("11 people · 4 generations · Private").assertExists()
        compose.onNodeWithContentDescription("Select Sarah").assertDoesNotExist()
    }
}
