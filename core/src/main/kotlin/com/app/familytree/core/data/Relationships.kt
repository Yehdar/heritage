package com.app.familytree.core.data

import com.app.familytree.core.model.*

object Relationships {
    fun add(tree: FamilyTree, anchorId: String, person: Person, type: RelationshipType, year: Int? = null): FamilyTree {
        require(tree.people.any { it.id == anchorId }) { "Choose a family member first." }
        require(person.id != anchorId) { "A person cannot be related to themselves." }
        require(person.name.isNotBlank()) { "Enter a name." }
        require(year == null || year in 1..9999) { "Enter a year between 1 and 9999." }
        val edges = when (type) {
            RelationshipType.PARENT -> listOf(Relationship(person.id, anchorId, RelationshipType.PARENT))
            RelationshipType.CHILD -> listOf(Relationship(anchorId, person.id, RelationshipType.PARENT))
            RelationshipType.PARTNER -> listOf(Relationship(anchorId, person.id, RelationshipType.PARTNER, year))
            RelationshipType.SIBLING -> {
                val parents = tree.relationships.filter { it.type == RelationshipType.PARENT && it.toId == anchorId }
                require(parents.isNotEmpty()) { "Add a parent to this person before adding a sibling." }
                parents.map { Relationship(it.fromId, person.id, RelationshipType.PARENT) }
            }
        }
        require(edges.none { edge -> tree.relationships.any { same(it, edge) } }) { "This relationship already exists." }
        val result = tree.copy(people = if (tree.people.any { it.id == person.id }) tree.people else tree.people + person, relationships = tree.relationships + edges)
        validate(result)
        return result
    }
    private fun same(a: Relationship, b: Relationship) = a.type == b.type && (a.fromId == b.fromId && a.toId == b.toId || a.type == RelationshipType.PARTNER && a.fromId == b.toId && a.toId == b.fromId)
    fun validate(tree: FamilyTree) {
        val ids = tree.people.map { it.id }.toSet()
        require(ids.size == tree.people.size && tree.people.all { it.id.isNotBlank() && it.name.isNotBlank() }) { "Invalid people in saved tree." }
        require(tree.people.count { it.isCurrentUser } == 1 && tree.code.isNotBlank()) { "Saved tree must have one current user and a tree code." }
        val seen = mutableListOf<Relationship>()
        tree.relationships.forEach {
            require(it.fromId in ids && it.toId in ids && it.fromId != it.toId) { "Invalid relationship endpoints." }
            require(it.type == RelationshipType.PARENT || it.type == RelationshipType.PARTNER) { "Relationships must use canonical parent or partner links." }
            require(it.marriageYear == null || it.marriageYear in 1..9999) { "Invalid marriage year." }
            require(seen.none { previous -> same(previous, it) }) { "Duplicate relationship." }
            seen += it
        }
        val children = tree.relationships.filter { it.type == RelationshipType.PARENT }.groupBy({ it.fromId }, { it.toId })
        val visited = mutableSetOf<String>(); val active = mutableSetOf<String>()
        fun visit(id: String) {
            require(id !in active) { "This relationship would create an ancestry cycle." }
            if (id in visited) return
            active += id; children[id].orEmpty().forEach(::visit); active -= id; visited += id
        }
        ids.forEach(::visit)
        val groups = ids.associateWith { it }.toMutableMap()
        tree.relationships.filter { it.type == RelationshipType.PARTNER }.forEach { edge ->
            val old = groups.getValue(edge.toId); val target = groups.getValue(edge.fromId)
            ids.forEach { if (groups[it] == old) groups[it] = target }
        }
        val groupedChildren = mutableMapOf<String, MutableSet<String>>()
        tree.relationships.filter { it.type == RelationshipType.PARENT }.forEach { edge ->
            val a = groups.getValue(edge.fromId); val b = groups.getValue(edge.toId)
            require(a != b) { "Partners cannot also be ancestors of each other." }
            groupedChildren.getOrPut(a) { mutableSetOf() }.add(b)
        }
        visited.clear(); active.clear()
        fun visitGroup(id: String) {
            require(id !in active) { "This partnership conflicts with the tree's generation order." }
            if (id in visited) return
            active += id; groupedChildren[id].orEmpty().forEach(::visitGroup); active -= id; visited += id
        }
        groups.values.toSet().forEach(::visitGroup)
    }
}
