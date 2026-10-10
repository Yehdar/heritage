package com.app.familytree.core.data

import com.app.familytree.core.model.*

object DemoData {
    private fun person(id: String, name: String) = Person(id, name, "asset:portrait_${id.hashCode().and(3)}", id == "john")
    fun initial(): AppData {
        val people = listOf("arthur" to "Arthur Doe", "rose" to "Rose Doe", "henry" to "Henry Doe", "mary" to "Mary Doe", "james" to "James Doe", "anne" to "Anne Doe", "john" to "John Doe", "jane" to "Jane Doe", "emma" to "Emma Doe", "oliver" to "Oliver Doe", "lucy" to "Lucy Doe").map { person(it.first, it.second) }
        fun parent(a: String, b: String) = Relationship(a, b, RelationshipType.PARENT)
        fun partner(a: String, b: String, year: Int) = Relationship(a, b, RelationshipType.PARTNER, year)
        return AppData(tree = FamilyTree(people, listOf(partner("arthur", "rose", 1948), parent("arthur", "henry"), parent("rose", "henry"), parent("arthur", "james"), parent("rose", "james"), partner("henry", "mary", 1974), partner("james", "anne", 1978), parent("henry", "john"), parent("mary", "john"), partner("john", "jane", 1999), parent("john", "emma"), parent("jane", "emma"), parent("john", "oliver"), parent("jane", "oliver"), parent("james", "lucy"), parent("anne", "lucy"))))
    }
}
