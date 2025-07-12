package io.flowkit.network.example

import io.flowkit.core.UiState
import kotlinx.coroutines.flow.Flow

class UserViewModel(private val repository: UserRepository) {

    /**
     * Load user with caching
     */
    fun loadUser(userId: String): Flow<UiState<User>> {
        return repository.getUserCached(userId)
    }

    /**
     * Search posts with debouncing
     */
    fun searchPosts(query: String): Flow<UiState<SearchResult>> {
        return repository.searchPosts(query)
    }

    /**
     * Create new post
     */
    fun createPost(title: String, content: String, userId: String): Flow<UiState<Post>> {
        val post = Post("", title, content, userId)
        return repository.createPost(post)
    }
}
