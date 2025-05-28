package io.flowkit.network.example

import io.flowkit.core.UiState
import io.flowkit.network.AuthTokenProvider
import io.flowkit.network.CacheStrategy
import io.flowkit.network.MockConnectivityMonitor
import io.flowkit.network.NetworkError
import io.flowkit.network.SimpleNetworkCache
import io.flowkit.network.asUiState
import io.flowkit.network.authenticatedNetworkFlow
import io.flowkit.network.cachedNetworkFlow
import io.flowkit.network.combineNetworkFlows
import io.flowkit.network.fold
import io.flowkit.network.networkFlow
import io.flowkit.network.networkPolling
import io.flowkit.network.networkUiFlow
import io.flowkit.network.onNetworkError
import io.flowkit.network.onNetworkSuccess
import io.flowkit.network.searchNetworkFlow
import io.flowkit.network.simpleNetworkFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.random.Random
import kotlin.time.Clock.System
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * Examples demonstrating how to use FlowKit Network module
 * These show real-world patterns and best practices
 */

// Sample data models
data class User(val id: String, val name: String, val email: String)
data class Post(val id: String, val title: String, val content: String, val userId: String)
data class SearchResult(val query: String, val results: List<Post>)

/**
 * Example repository using FlowKit Network utilities
 */
class UserRepository {

    private val cache = SimpleNetworkCache()
    private val connectivityMonitor = MockConnectivityMonitor()

    /**
     * Simple API call with automatic loading/error states
     */
    fun getUser(userId: String): Flow<UiState<User>> {
        return networkUiFlow {
            source {
                // Simulate API call
                User(userId, "John Doe", "john@example.com")
            }
            retryOnFailure(maxAttempts = 3)
            timeout(30.seconds)
            enableLogging("UserRepository")
        }
    }

    /**
     * Cached API call with stale-while-revalidate strategy
     */
    fun getUserCached(userId: String): Flow<UiState<User>> {
        return networkUiFlow {
            source {
                // Simulate API call
                User(userId, "John Doe", "john@example.com")
            }
            cache("user_$userId", CacheStrategy.STALE_WHILE_REVALIDATE, ttl = 5.minutes)
            retryOnFailure(maxAttempts = 2)
            withDependencies(cache = cache)
            enableLogging("UserRepository")
        }
    }

    /**
     * Offline-first API call with fallback to cache
     */
    fun getUserOfflineFirst(userId: String): Flow<UiState<User>> {
        return networkUiFlow {
            source {
                User(userId, "John Doe", "john@example.com")
            }
            cache("user_offline_$userId", CacheStrategy.CACHE_FIRST)
            offlineFirst()
            retryOnFailure(maxAttempts = 2)
            withDependencies(
                cache = cache,
                connectivityMonitor = connectivityMonitor
            )
        }
    }

    /**
     * Search with debouncing (great for search-as-you-type)
     */
    fun searchPosts(query: String): Flow<UiState<SearchResult>> {
        return networkUiFlow {
            source {
                // Simulate search API call
                SearchResult(
                    query = query,
                    results = listOf(
                        Post("1", "Sample Post 1", "Content 1", "user1"),
                        Post("2", "Sample Post 2", "Content 2", "user2")
                    )
                )
            }
            debounce(300.seconds) // Wait 300ms after user stops typing
            retryOnFailure(maxAttempts = 2)
            enableLogging("SearchRepository")
        }
    }

    /**
     * Authenticated API call
     */
    @OptIn(ExperimentalTime::class)
    fun createPost(post: Post): Flow<UiState<Post>> {
        return networkUiFlow {
            source {
                // Simulate authenticated API call
                post.copy(id = "generated_id_${System.now().toEpochMilliseconds()}")
            }
            requireAuth()
            retryOnFailure(maxAttempts = 1) // Don't retry auth failures too much
            timeout(15.seconds)
        }
    }

    /**
     * Combine multiple network calls
     */
    fun getUserWithPosts(userId: String): Flow<UiState<Pair<User, List<Post>>>> {
        val userFlow = networkFlow {
            source { User(userId, "John Doe", "john@example.com") }
        }

        val postsFlow = networkFlow {
            source {
                listOf(
                    Post("1", "Post 1", "Content 1", userId),
                    Post("2", "Post 2", "Content 2", userId)
                )
            }
        }

        return combineNetworkFlows(userFlow, postsFlow) { user, posts ->
            user to posts
        }.asUiState()
    }
}

/**
 * Example ViewModel using the repository
 */
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

/**
 * Usage examples in different scenarios
 */
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

/**
 * Search with debouncing example
 */
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

/**
 * Cached request example
 */
suspend fun cachedExample() {
    val cachedFlow = cachedNetworkFlow(
        key = "posts_list",
        request = {
            // Simulate API call that takes time
            listOf(
                Post("1", "Cached Post 1", "Content 1", "user1"),
                Post("2", "Cached Post 2", "Content 2", "user2")
            )
        },
        ttl = 5.minutes
    )

    // First call - will fetch from network and cache
    println("First call:")
    cachedFlow.first().let { result ->
        result.fold(
            onFailure = { println("❌ Failed to load posts") },
            onSuccess = { posts -> println("✅ Posts loaded: ${posts.size} posts") },
            onLoading = { println("🔄 Loading...") }
        )
    }

    // Second call - will use cached data
    println("Second call (should use cache):")
    cachedFlow.first().let { result ->
        result.fold(
            onFailure = { println("❌ Failed to load posts") },
            onSuccess = { posts -> println("✅ Posts from cache: ${posts.size} posts") },
            onLoading = { println("🔄 Loading...") }
        )
    }
}

/**
 * Authenticated request example
 */
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

/**
 * Combining multiple network requests
 */
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

/**
 * Error handling example
 */
suspend fun errorHandlingExample() {
    val errorProneFlow = networkFlow<String> {
        source {
            // Simulate different types of errors
            when ((1..4).random()) {
                1 -> throw NetworkError.NoInternetConnection
                2 -> throw NetworkError.TimeoutError("Request timed out")
                3 -> throw NetworkError.HttpError(404, "Not found")
                else -> "Success!"
            }
        }
        retryOnFailure(maxAttempts = 3) { error ->
            // Only retry on timeout and connection errors
            error is NetworkError.TimeoutError || error is NetworkError.NoInternetConnection
        }
        enableLogging("ErrorExample")
    }

    errorProneFlow
        .onNetworkError { error ->
            println("🚨 Network error occurred: ${error.message}")
        }
        .onNetworkSuccess { data ->
            println("🎉 Success: $data")
        }
        .collect { result ->
            result.fold(
                onFailure = { error -> println("💥 Final error: ${error.message}") },
                onSuccess = { data -> println("✅ Final result: $data") },
                onLoading = { println("⏳ Trying...") }
            )
        }
}

/**
 * Polling example (periodic network requests)
 */
suspend fun pollingExample() {
    val pollingFlow = networkPolling(
        interval = 5.seconds,
        request = {
            // Simulate periodic data fetch (like notifications)
            "Notification count: ${(1..10).random()}"
        }
    )

    pollingFlow
        .onNetworkSuccess { data ->
            println("🔔 $data")
        }
        .collect { result ->
            result.fold(
                onFailure = { error -> println("❌ Polling failed: ${error.message}") },
                onSuccess = { /* Success handled above */ },
                onLoading = { println("🔄 Checking for updates...") }
            )
        }
}

/**
 * Integration with MVI pattern
 */
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