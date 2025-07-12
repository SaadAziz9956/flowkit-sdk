package io.flowkit.network.example

import io.flowkit.core.UiState

class NetworkMviExample {

    data class AppState(
        val user: User? = null,
        val posts: List<Post> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )

    sealed class AppIntent {
        data class LoadUser(val userId: String) : AppIntent()
        data class SearchPosts(val query: String) : AppIntent()
        data object RefreshData : AppIntent()
    }

    /**
     * Show how network flows integrate with MVI
     */
    suspend fun mviIntegrationExample() {
        val repository = UserRepository()

        // In your MVI reducer, you'd handle intents like this:
        val userFlow = repository.getUserCached("123")

        userFlow.collect { uiState ->
            when (uiState) {
                is UiState.Loading -> {
                    // Update MVI state to show loading
                    println("MVI: Setting loading state")
                }

                is UiState.Success -> {
                    // Update MVI state with user data
                    println("MVI: User loaded - ${uiState.data.name}")
                }

                is UiState.Error -> {
                    // Update MVI state with error
                    println("MVI: Error state - ${uiState.message}")
                }
            }
        }
    }
}