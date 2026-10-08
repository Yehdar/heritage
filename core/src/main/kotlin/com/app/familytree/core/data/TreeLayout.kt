package com.app.familytree.core.data

import com.app.familytree.core.model.*

object TreeLayout {
    fun graph(tree: FamilyTree): TreeGraph {
        val parents = tree.relationships.filter { it.type == RelationshipType.PARENT }
        val partners = tree.relationships.filter { it.type == RelationshipType.PARTNER }
        // Partner components share a generation. Longest parent path sets each component's row.
        val group = tree.people.associate { it.id to it.id }.toMutableMap()
        partners.forEach { edge -> val old = group.getValue(edge.toId); val target = group.getValue(edge.fromId); group.keys.toList().forEach { if (group[it] == old) group[it] = target } }
        val levels = group.values.distinct().associateWith { 0 }.toMutableMap()
        repeat(tree.people.size) { parents.forEach { edge ->
            val from = group.getValue(edge.fromId); val to = group.getValue(edge.toId)
            if (from != to) levels[to] = maxOf(levels.getValue(to), levels.getValue(from) + 1)
        } }
        val rows = tree.people.groupBy { levels.getValue(group.getValue(it.id)) }.toSortedMap()
        val width = rows.values.maxOfOrNull { it.size } ?: 1
        val nodes = rows.flatMap { (level, people) ->
            val ordered = people.sortedWith(compareBy<Person> { group.getValue(it.id) }.thenBy { it.id })
            ordered.mapIndexed { index, person -> TreeNode(person, (width - ordered.size) * 65f + index * 130f + 65f, level * 150f + 55f) }
        }
        return TreeGraph(nodes, parents.map { it.fromId to it.toId }, partners.map { MarriageConnection(it.fromId, it.toId, it.marriageYear?.let { year -> "m. $year" } ?: "Partner") })
    }
    fun generations(graph: TreeGraph) = graph.nodes.map { it.positionY }.distinct().size
}
