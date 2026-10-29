package com.kaan.play.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Test

class CsRepoOwnershipGraphTest {
    @Test
    fun deletingParentReturnsItsExclusiveNestedRepositoriesOnly() {
        val edges = mapOf(
            "https://parent.example/repo.json" to listOf("https://child.example/repo.json"),
            "https://child.example/repo.json" to listOf("https://grandchild.example/repo.json"),
            "https://unrelated.example/repo.json" to listOf("https://unrelated-child.example/repo.json"),
        )

        val plan = CsRepoOwnershipGraph.removeOwnedDescendants(edges, "https://parent.example/repo.json")

        assertEquals(
            listOf("https://child.example/repo.json", "https://grandchild.example/repo.json"),
            plan.descendants,
        )
        assertEquals(
            mapOf("https://unrelated.example/repo.json" to listOf("https://unrelated-child.example/repo.json")),
            plan.remainingEdges,
        )
    }

    @Test
    fun deletingParentKeepsSharedChildAndItsDescendants() {
        val edges = mapOf(
            "https://parent.example/repo.json" to listOf("https://shared.example/repo.json"),
            "https://other-parent.example/repo.json" to listOf("https://shared.example/repo.json"),
            "https://shared.example/repo.json" to listOf("https://shared-child.example/repo.json"),
        )

        val plan = CsRepoOwnershipGraph.removeOwnedDescendants(edges, "https://parent.example/repo.json")

        assertEquals(emptyList<String>(), plan.descendants)
        assertEquals(
            mapOf(
                "https://other-parent.example/repo.json" to listOf("https://shared.example/repo.json"),
                "https://shared.example/repo.json" to listOf("https://shared-child.example/repo.json"),
            ),
            plan.remainingEdges,
        )
    }

    @Test
    fun deletingParentRemovesOnlyExclusiveBranches() {
        val edges = mapOf(
            "https://parent.example/repo.json" to listOf("https://owned.example/repo.json", "https://shared.example/repo.json"),
            "https://owned.example/repo.json" to listOf("https://leaf.example/repo.json"),
            "https://other-parent.example/repo.json" to listOf("https://shared.example/repo.json"),
            "https://shared.example/repo.json" to listOf("https://shared-leaf.example/repo.json"),
        )

        val plan = CsRepoOwnershipGraph.removeOwnedDescendants(edges, "https://parent.example/repo.json")

        assertEquals(listOf("https://owned.example/repo.json", "https://leaf.example/repo.json"), plan.descendants)
        assertEquals(
            mapOf(
                "https://other-parent.example/repo.json" to listOf("https://shared.example/repo.json"),
                "https://shared.example/repo.json" to listOf("https://shared-leaf.example/repo.json"),
            ),
            plan.remainingEdges,
        )
    }
}
