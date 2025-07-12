package io.flowkit.network.example

import io.flowkit.core.UiState
import io.flowkit.network.asUiState
import io.flowkit.network.combineNetworkFlows
import io.flowkit.network.networkFlow

suspend fun combineExample() {
    val userFlow = networkFlow {
        source { User("123", "John Doe", "john@example.com") }
    }

    val postsFlow = networkFlow {
        source {
            listOf(
                Post("1", "John's Post 1", "Content 1", "123"),
                Post("2", "John's Post 2", "Content 2", "123")
            )
        }
    }

    val combinedFlow = combineNetworkFlows(userFlow, postsFlow) { user, posts ->
        "User ${user.name} has ${posts.size} posts"
    }

    combinedFlow.asUiState().collect { uiState ->
        when (uiState) {
            is UiState.Loading -> println("🔄 Loading user and posts...")
            is UiState.Success -> println("✅ ${uiState.data}")
            is UiState.Error -> println("❌ Failed to load: ${uiState.message}")
        }
    }
}
