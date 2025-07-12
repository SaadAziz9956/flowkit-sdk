package io.flowkit.network.example

import io.flowkit.core.UiState
import io.flowkit.network.asUiState
import io.flowkit.network.authenticatedNetworkFlow

suspend fun authenticatedExample() {
    val authFlow = authenticatedNetworkFlow {
        // Simulate authenticated API call
        User("current_user", "Authenticated User", "auth@example.com")
    }

    authFlow.asUiState().collect { uiState ->
        when (uiState) {
            is UiState.Loading -> println("🔐 Authenticating...")
            is UiState.Success -> println("✅ Authenticated user: ${uiState.data.name}")
            is UiState.Error -> println("❌ Authentication failed: ${uiState.message}")
        }
    }
}