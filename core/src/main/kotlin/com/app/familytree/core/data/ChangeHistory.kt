package com.app.familytree.core.data

import com.app.familytree.core.model.*

object ChangeHistory {
    fun relationshipKey(edge: Relationship): String {
        val ids = if (edge.type == RelationshipType.PARTNER) listOf(edge.fromId, edge.toId).sorted() else listOf(edge.fromId, edge.toId)
        return "${edge.type}:${ids[0]}:${ids[1]}"
    }
    fun treeDetails(before: FamilyTree, after: FamilyTree): List<String> {
        val old = before.people.associateBy { it.id }; val next = after.people.associateBy { it.id }
        fun name(id: String) = next[id]?.name ?: old[id]?.name ?: id
        return buildList {
            next.forEach { (id, person) ->
                when {
                    id !in old -> add("Added ${person.name}")
                    old[id] != person -> add("Updated ${old[id]!!.name}: name ${old[id]!!.name} → ${person.name}; avatar ${if (old[id]!!.avatarUrl != person.avatarUrl) "changed" else "unchanged"}")
                }
            }
            old.filterKeys { it !in next }.values.forEach { add("Removed ${it.name}") }
            val oldEdges = before.relationships.associateBy(::relationshipKey)
            val newEdges = after.relationships.associateBy(::relationshipKey)
            newEdges.forEach { (key, edge) -> if (oldEdges[key] != edge) add("${if (key in oldEdges) "Updated" else "Added"} ${edge.type.name.lowercase()} link: ${name(edge.fromId)} → ${name(edge.toId)}${edge.marriageYear?.let { " (m. $it)" }.orEmpty()}") }
            oldEdges.filterKeys { it !in newEdges }.values.forEach { add("Removed ${it.type.name.lowercase()} link: ${name(it.fromId)} → ${name(it.toId)}") }
        }
    }
    fun localDetails(before: AppData, after: AppData): List<String> = buildList {
        addAll(treeDetails(before.tree, after.tree))
        if (before.settings.isPrivate != after.settings.isPrivate) add("Tree privacy changed to ${if (after.settings.isPrivate) "private" else "shared"}")
        if (before.settings.discoverable != after.settings.discoverable) add("Profile discoverability changed")
        if (before.settings.notifications != after.settings.notifications) add("Notification preference changed")
        if (before.session != after.session) add(if (after.session.signedIn) "Signed in" else "Logged out")
        if (before.shareToken != after.shareToken) add(if (after.shareToken == null) "Sharing link revoked" else "Created a sharing link")
        before.syncLinks.forEach { link ->
            val updated = after.syncLinks.find { it.treeId == link.treeId }
            if (updated == null) add("Disconnected ${link.ownerName}'s tree")
            else if (updated.liveSync != link.liveSync) add("${if (updated.liveSync) "Enabled" else "Paused"} live sync with ${link.ownerName}")
        }
    }
}
