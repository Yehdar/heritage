package com.app.familytree.core.data

import com.app.familytree.core.model.*

object TreeIntegration {
    fun snapshot(data: AppData, revision: Long = 0): TreeSnapshot {
        fun id(local: String) = if (local.contains(":")) local else "${data.treeId}:$local"
        val owner = data.tree.people.first { it.isCurrentUser }
        return TreeSnapshot(treeId = data.treeId, revision = revision, ownerName = owner.name, ownerPersonId = id(owner.id),
            people = data.tree.people.map { it.copy(id = id(it.id), avatarUrl = it.avatarUrl?.takeUnless { url -> url.startsWith("asset:") }, isCurrentUser = false) },
            relationships = data.tree.relationships.map { it.copy(fromId = id(it.fromId), toId = id(it.toId)) })
    }
    fun validate(snapshot: TreeSnapshot) {
        require(snapshot.version == 1 && snapshot.treeId.isNotBlank() && snapshot.ownerName.isNotBlank() && snapshot.revision >= 0) { "Invalid shared tree." }
        require(snapshot.people.size in 1..500 && snapshot.relationships.size <= 2000) { "Shared tree exceeds supported size." }
        require(snapshot.people.all { it.id.matches(Regex("[A-Za-z0-9_-]{1,128}:[A-Za-z0-9_-]{1,128}")) && !it.isCurrentUser }) { "Invalid shared person identity." }
        require(snapshot.people.all { it.name.length in 1..256 && (it.avatarUrl == null || it.avatarUrl.length <= 2048 && it.avatarUrl.startsWith("https://")) }) { "Shared names or avatar URLs are invalid." }
        require(snapshot.people.any { it.id == snapshot.ownerPersonId }) { "Shared profile is missing." }
        Relationships.validate(FamilyTree(snapshot.people.map { it.copy(isCurrentUser = it.id == snapshot.ownerPersonId) }, snapshot.relationships))
    }
    private fun mapping(snapshot: TreeSnapshot, chosen: Map<String, String>) = snapshot.people.associate { it.id to (chosen[it.id] ?: it.id) }
    fun initial(data: AppData, draft: ImportDraft): Pair<FamilyTree, SyncLink> {
        validate(draft.snapshot)
        require(draft.snapshot.treeId != data.treeId) { "This is your own tree." }
        require(data.syncLinks.none { it.treeId == draft.snapshot.treeId }) { "This tree is already linked. Use its sync controls instead." }
        val map = mapping(draft.snapshot, draft.personMapping)
        require(map.values.toSet().size == map.size) { "Two shared people cannot match the same local person." }
        val localIds = data.tree.people.map { it.id }.toSet()
        require(draft.personMapping.keys.all { key -> draft.snapshot.people.any { it.id == key } } && draft.personMapping.values.all { it in localIds }) { "Invalid person match." }
        var result = mergePeopleAndEdges(data.tree, draft.snapshot, map, draft.overwriteMatching)
        val ownerId = map.getValue(draft.snapshot.ownerPersonId)
        if (ownerId != draft.anchorId && draft.snapshot.ownerPersonId !in draft.personMapping) {
            result = Relationships.add(result, draft.anchorId, result.people.first { it.id == ownerId }, draft.relationship)
        }
        Relationships.validate(result)
        val originalKeys = data.tree.relationships.map(ChangeHistory::relationshipKey).toSet()
        val ownedKeys = draft.snapshot.relationships.map { mappedEdge(it, map) }.map(ChangeHistory::relationshipKey).toSet() - originalKeys
        val link = SyncLink(draft.token, draft.snapshot.treeId, draft.snapshot.ownerName, draft.snapshot, map, localIds, ownedKeys, draft.overwriteMatching, draft.liveSync, draft.anchorId, if (draft.snapshot.ownerPersonId in draft.personMapping) null else draft.relationship)
        return result to link
    }
    private fun mappedEdge(edge: Relationship, map: Map<String, String>) = edge.copy(fromId = map.getValue(edge.fromId), toId = map.getValue(edge.toId))
    private fun mergePeopleAndEdges(tree: FamilyTree, source: TreeSnapshot, map: Map<String, String>, overwrite: Boolean): FamilyTree {
        val people = tree.people.associateBy { it.id }.toMutableMap()
        source.people.forEach { remote ->
            val id = map.getValue(remote.id); val existing = people[id]
            if (existing == null || overwrite) people[id] = remote.copy(id = id, isCurrentUser = existing?.isCurrentUser ?: false)
        }
        val edges = tree.relationships.associateBy(ChangeHistory::relationshipKey).toMutableMap()
        source.relationships.forEach { remote ->
            val edge = mappedEdge(remote, map); val key = ChangeHistory.relationshipKey(edge)
            if (key !in edges || overwrite) edges[key] = edge
        }
        return tree.copy(people = people.values.toList(), relationships = edges.values.toList())
    }
    /** Three-way application: never silently replace edits made locally since the baseline. */
    fun update(tree: FamilyTree, link: SyncLink, incoming: TreeSnapshot): Pair<FamilyTree, SyncLink> {
        validate(incoming)
        require(incoming.treeId == link.treeId && incoming.revision > link.baseline.revision) { "This revision has already been reviewed." }
        val map = link.personMapping + mapping(incoming, link.personMapping)
        val oldMap = link.personMapping
        val before = link.baseline.people.associateBy { it.id }
        val after = incoming.people.associateBy { it.id }
        val local = tree.people.associateBy { it.id }.toMutableMap()
        val baselineEdges = link.baseline.relationships.map { mappedEdge(it, oldMap) }.associateBy(ChangeHistory::relationshipKey)
        val remoteEdges = incoming.relationships.map { mappedEdge(it, map) }.associateBy(ChangeHistory::relationshipKey)
        val edges = tree.relationships.associateBy(ChangeHistory::relationshipKey).toMutableMap()
        val deletedPeople = mutableSetOf<String>()
        before.forEach { (sourceId, oldPerson) ->
            val id = oldMap.getValue(sourceId); val localPerson = local[id] ?: return@forEach
            val next = after[sourceId]
            val oldLocal = oldPerson.copy(id = id, isCurrentUser = localPerson.isCurrentUser)
            if (next == null && id !in link.protectedPersonIds) {
                require(localPerson == oldLocal) { "${localPerson.name} was edited locally. Reject this update or resolve their edits before accepting removal." }
                require(edges.values.filter { it.fromId == id || it.toId == id }.all { edge ->
                    val key = ChangeHistory.relationshipKey(edge)
                    key in link.ownedRelationshipKeys && baselineEdges[key] == edge || isBridge(edge, link, id)
                }) { "${localPerson.name} has local relationships. Removal would affect your own changes." }
                deletedPeople += id
            } else if (next != null && next.copy(isCurrentUser = false) != oldPerson.copy(isCurrentUser = false)) {
                val desired = next.copy(id = id, isCurrentUser = localPerson.isCurrentUser)
                if (id !in link.protectedPersonIds || link.overwriteMatching) {
                    require(localPerson == oldLocal || localPerson == desired) { "${localPerson.name} has conflicting local edits. Reject this update or resolve the edits first." }
                    local[id] = desired
                }
            }
        }
        baselineEdges.forEach { (key, old) ->
            if (key !in remoteEdges && key in link.ownedRelationshipKeys) {
                require(edges[key] == null || edges[key] == old) { "A relationship was edited locally and cannot be removed by this update." }
                edges.remove(key)
            }
        }
        remoteEdges.forEach { (key, remote) ->
            val old = baselineEdges[key]; val existing = edges[key]
            if (old == null) {
                require(existing == null || existing == remote) { "An incoming relationship conflicts with a local relationship." }
                edges[key] = remote
            } else if (old != remote && (key in link.ownedRelationshipKeys || link.overwriteMatching)) {
                require(existing == old || existing == remote) { "A relationship has conflicting local edits." }
                edges[key] = remote
            }
        }
        incoming.people.filter { it.id !in before }.forEach { remote ->
            val id = map.getValue(remote.id)
            require(id !in local || local[id] == remote.copy(id = id, isCurrentUser = false)) { "An incoming person conflicts with an existing person." }
            local[id] = remote.copy(id = id, isCurrentUser = false)
        }
        deletedPeople.forEach(local::remove)
        val result = tree.copy(people = local.values.toList(), relationships = edges.values.filter { it.fromId !in deletedPeople && it.toId !in deletedPeople })
        Relationships.validate(result)
        val addedKeys = remoteEdges.keys - baselineEdges.keys - tree.relationships.map(ChangeHistory::relationshipKey).toSet()
        return result to link.copy(baseline = incoming, ownerName = incoming.ownerName, personMapping = map, ownedRelationshipKeys = (link.ownedRelationshipKeys + addedKeys).intersect(remoteEdges.keys))
    }
    private fun isBridge(edge: Relationship, link: SyncLink, ownerId: String): Boolean {
        if (ownerId != link.personMapping[link.baseline.ownerPersonId]) return false
        return when (link.relationship) {
            RelationshipType.PARTNER -> edge.type == RelationshipType.PARTNER && setOf(edge.fromId, edge.toId) == setOf(ownerId, link.anchorId)
            RelationshipType.PARENT -> edge.type == RelationshipType.PARENT && edge.fromId == ownerId && edge.toId == link.anchorId
            RelationshipType.CHILD -> edge.type == RelationshipType.PARENT && edge.fromId == link.anchorId && edge.toId == ownerId
            else -> false
        }
    }
}
