package io.flowkit.network.example

import io.flowkit.core.UiState
import io.flowkit.network.asUiState
import io.flowkit.network.searchNetworkFlow
import kotlin.time.Duration.Companion.seconds

suspend fun searchExample() {
    val searchFlow = searchNetworkFlow(
        request = {
            // Simulate search API call
            SearchResult(
                query = "kotlin",
                results = listOf(
                    Post("1", "Kotlin Basics", "Learn Kotlin fundamentals", "user1"),
                    Post("2", "Advanced Kotlin", "Advanced Kotlin concepts", "user2")
                )
            )
        },
        debounceTime = 300.seconds
    )

    searchFlow.asUiState().collect { uiState ->
        when (uiState) {
            is UiState.Loading -> println("🔍 Searching...")
            is UiState.Success -> {
                println("📋 Search results for '${uiState.data.query}':")
                uiState.data.results.forEach { post ->
                    println("  - ${post.title}")
                }
            }

            is UiState.Error -> println("❌ Search failed: ${uiState.message}")
        }
    }
}