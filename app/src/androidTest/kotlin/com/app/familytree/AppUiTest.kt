package com.app.familytree

import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.app.familytree.core.data.*
import com.app.familytree.core.theme.HeritageTheme
import com.app.familytree.navigation.FamilyTreeApp
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class AppUiTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private class TestStorage : Storage {
        var json: String? = null
        override suspend fun read() = json
        override suspend fun write(value: String) { json = value }
    }
    @Before fun setup() {
        compose.setContent {
            val owner = remember { AppOwner(LocalFamilyRepository(TestStorage())).also { store.put("app", it) } }
            HeritageTheme { FamilyTreeApp(owner) }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Our living tree").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() } }

    @Test fun tabsAndNodeSelectionShowSingleBadgeAndFourActions() {
        compose.onNodeWithContentDescription("Select John Doe").performClick()
        compose.onAllNodesWithTag("addBadge").assertCountEquals(1)
        val avatar = compose.onNodeWithContentDescription("Select John Doe").fetchSemanticsNode().boundsInRoot
        val badge = compose.onNodeWithTag("addBadge").fetchSemanticsNode().boundsInRoot
        assertTrue(badge.center.x > avatar.center.x && badge.top < avatar.top)
        assertTrue(!badge.contains(avatar.center))
        compose.onNodeWithContentDescription("Add relative").performClick()
        val actions = listOf("Add Parent", "Add Child", "Add Sibling", "Add Partner")
        actions.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Add Child").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Sharing").performClick()
        compose.onNodeWithText("Receive a tree").assertExists()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Account Profile").assertIsDisplayed()
    }
    @Test fun graphPanAndFitRestorePresentation() {
        val before = compose.onNodeWithContentDescription("Select John Doe").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("treeGraph").performTouchInput { swipe(androidx.compose.ui.geometry.Offset(20f, 20f), androidx.compose.ui.geometry.Offset(80f, 20f)) }
        val moved = compose.onNodeWithContentDescription("Select John Doe").fetchSemanticsNode().boundsInRoot
        assertTrue(moved.left > before.left)
        compose.onNodeWithText("Fit tree").performClick()
        val fitted = compose.onNodeWithContentDescription("Select John Doe").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(fitted.left - before.left) < 2f)
    }
    @Test fun addRelativeUpdatesTree() {
        compose.onNodeWithContentDescription("Add relative").performClick()
        compose.onNodeWithText("Add Child").performClick()
        compose.onNodeWithText("Name").performTextInput("Alex Doe")
        compose.onNodeWithText("Add relative").performClick()
        compose.onNodeWithContentDescription("Select Alex Doe").assertExists()
        compose.onNodeWithText("12 people · 4 generations · Private").assertExists()
    }
    @Test fun sharingReplacesDiscoveryAndLogsTrackLocalChanges() {
        compose.onNodeWithText("Find Others").assertDoesNotExist()
        compose.onNodeWithText("Sharing").performClick()
        compose.onNodeWithText("Share your tree").assertExists()
        compose.onNodeWithText("Scan QR").assertExists()
        compose.onNodeWithText("Logs").performClick()
        compose.onNodeWithText("No changes yet. Add a relative or import a tree to start your history.").assertExists()
        compose.onNodeWithText("My Tree").performClick()
        compose.onNodeWithContentDescription("Add relative").performClick()
        compose.onNodeWithText("Add Child").performClick()
        compose.onNodeWithText("Name").performTextInput("Logged child")
        compose.onNodeWithText("Add relative").performClick()
        compose.onNodeWithText("Logs").performClick()
        compose.onNodeWithText("• Added Logged child").assertExists()
    }
    @Test fun settingsSaveAndLogoutResumeKeepTree() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Account Profile").performClick()
        compose.onNodeWithText("Name").performTextReplacement("John D.")
        compose.onNodeWithText("Save profile").performClick()
        compose.onNodeWithText("Log Out").performClick()
        compose.onNodeWithText("Continue as John D.").assertIsDisplayed().performClick()
        compose.onNodeWithText("My Tree").performClick()
        compose.onNodeWithContentDescription("Select John D.").assertExists()
    }
}
