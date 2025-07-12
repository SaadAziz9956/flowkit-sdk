package io.flowkit.network.example

import io.flowkit.core.UiState
import io.flowkit.network.AuthTokenProvider
import io.flowkit.network.CacheStrategy
import io.flowkit.network.MockConnectivityMonitor
import io.flowkit.network.NetworkError
import io.flowkit.network.SimpleNetworkCache
import io.flowkit.network.asUiState
import io.flowkit.network.fold
import io.flowkit.network.networkFlow
import io.flowkit.network.simpleNetworkFlow
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object NetworkUsageExamples {

    /**
     * Simple network request
     */
    suspend fun basicExample() {
        val userFlow = simpleNetworkFlow {
            // Your API call here
            User("123", "John Doe", "john@example.com")
        }

        userFlow.collect { result ->
            result.fold(
                onFailure = { error -> println("Error: ${error.message}") },
                onSuccess = { user -> println("User loaded: $user") },
                onLoading = { println("Loading user...") }
            )
        }
    }

    /**
     * Advanced network request with all features
     */
    suspend fun advancedExample() {
        val userFlow = networkFlow<User> {
            source {
                // Simulate network delay and potential failure
                if (Random.nextDouble() < 0.3) {
                    throw NetworkError.TimeoutError("Simulated timeout")
                }
                User("123", "Jane Doe", "jane@example.com")
            }

            // Configure caching
            cache("user_123", CacheStrategy.STALE_WHILE_REVALIDATE, ttl = 10.minutes)

            // Configure retry behavior
            retryOnFailure(
                maxAttempts = 3,
                initialDelay = 1.seconds
            ) { error ->
                // Only retry on timeout and connection errors
                error is NetworkError.TimeoutError || error is NetworkError.NoInternetConnection
            }

            // Set timeout
            timeout(30.seconds)

            // Enable offline support
            offlineFirst()

            // Require authentication
            requireAuth()

            // Enable debug logging
            enableLogging("UserLoader")

            // Inject dependencies (usually done by DI framework)
            withDependencies(
                cache = SimpleNetworkCache(),
                connectivityMonitor = MockConnectivityMonitor(),
                authProvider = object : AuthTokenProvider {
                    override suspend fun getToken(): String? = "sample_token"
                    override suspend fun refreshToken(): String? = "refreshed_token"
                    override suspend fun clearToken() {}
                    override suspend fun isTokenExpired(): Boolean = false
                }
            )
        }

        // Convert to UiState for UI consumption
        userFlow.asUiState().collect { uiState ->
            when (uiState) {
                is UiState.Loading -> println("🔄 Loading user...")
                is UiState.Success -> println("✅ User loaded: ${uiState.data}")
                is UiState.Error -> println("❌ Error loading user: ${uiState.message}")
            }
        }

    }
}