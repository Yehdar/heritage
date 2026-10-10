package com.app.familytree.core.model

import kotlinx.serialization.Serializable

@Serializable data class Person(val id: String, val name: String, val avatarUrl: String?, val isCurrentUser: Boolean = false)
@Serializable enum class RelationshipType { PARENT, CHILD, SIBLING, PARTNER }
data class MarriageConnection(val person1Id: String, val person2Id: String, val yearLabel: String = "m. 1999")
data class TreeNode(val person: Person, val positionX: Float, val positionY: Float)
data class TreeGraph(val nodes: List<TreeNode>, val descentLines: List<Pair<String, String>>, val marriages: List<MarriageConnection>)
/** PARENT is canonical: fromId is parent and toId is child. PARTNER is symmetric. */
@Serializable data class Relationship(val fromId: String, val toId: String, val type: RelationshipType, val marriageYear: Int? = null)
@Serializable data class FamilyTree(val people: List<Person>, val relationships: List<Relationship>, val code: String = "DOE-1999")
data class SearchResult(val person: Person, val isConnected: Boolean)
@Serializable data class AppSettings(val isPrivate: Boolean = true, val discoverable: Boolean = false, val notifications: Boolean = true)
@Serializable data class Session(val signedIn: Boolean = true)
@Serializable data class AppData(val version: Int = 2, val tree: FamilyTree, val connectedUserIds: Set<String> = emptySet(), val settings: AppSettings = AppSettings(), val session: Session = Session(), val treeId: String = "", val shareToken: String? = null, val syncLinks: List<SyncLink> = emptyList(), val logs: List<ChangeLog> = emptyList())
data class RelationForm(val anchorId: String, val type: RelationshipType = RelationshipType.CHILD, val existingPerson: Person? = null, val name: String = "", val avatarUrl: String = "", val year: String = "", val error: String? = null, val saving: Boolean = false)
