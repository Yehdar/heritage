package com.app.familytree.core.model

import kotlinx.serialization.Serializable

@Serializable data class TreeSnapshot(
    val version: Int = 1,
    val treeId: String,
    val revision: Long,
    val ownerName: String,
    val ownerPersonId: String,
    val people: List<Person>,
    val relationships: List<Relationship>
)
@Serializable data class SyncLink(
    val token: String,
    val treeId: String,
    val ownerName: String,
    val baseline: TreeSnapshot,
    val personMapping: Map<String, String>,
    val protectedPersonIds: Set<String>,
    val ownedRelationshipKeys: Set<String>,
    val overwriteMatching: Boolean,
    val liveSync: Boolean,
    val anchorId: String,
    val relationship: RelationshipType?
)
@Serializable enum class LogStatus { APPLIED, PENDING, REJECTED, SUPERSEDED }
@Serializable data class ChangeLog(
    val id: String,
    val timestampMillis: Long,
    val actorName: String,
    val summary: String,
    val details: List<String>,
    val status: LogStatus = LogStatus.APPLIED,
    val sourceTreeId: String? = null,
    val proposal: TreeSnapshot? = null
)
data class ImportDraft(
    val token: String,
    val snapshot: TreeSnapshot,
    val anchorId: String,
    val relationship: RelationshipType = RelationshipType.PARTNER,
    val overwriteMatching: Boolean = false,
    val liveSync: Boolean = true,
    val personMapping: Map<String, String> = emptyMap(),
    val preview: List<String> = emptyList(),
    val previewReady: Boolean = false,
    val previewBaseTree: FamilyTree? = null
)
