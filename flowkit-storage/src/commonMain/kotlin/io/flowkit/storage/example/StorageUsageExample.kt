package io.flowkit.storage.examples

import io.flowkit.core.UiState
import io.flowkit.core.dataOrNull
import io.flowkit.core.fold
import io.flowkit.core.onSuccess
import io.flowkit.storage.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

// Sample data models
data class User(val id: String, val name: String, val email: String, val settings: UserSettings)
data class UserSettings(val theme: String, val notifications: Boolean, val language: String)
data class Post(val id: String, val title: String, val content: String, val authorId: String)

/**
 * Examples demonstrating FlowKit Storage usage patterns
 */

/**
 * User preferences repository using type-safe preferences
 */
class UserPreferencesRepository(private val storage: KeyValueStorage) {

    private val prefs = storage.preferences { }

    // Type-safe preference definitions
    private val themePreference = prefs.stringPreference("user_theme", "system")
    private val notificationsPreference = prefs.booleanPreference("notifications_enabled", true)
    private val languagePreference = prefs.stringPreference("user_language", "en")
    private val lastLoginPreference = prefs.longPreference("last_login_time", 0L)

    // Reactive preference access
    fun observeTheme(): Flow<String> = themePreference.observe()
    fun observeNotifications(): Flow<Boolean> = notificationsPreference.observe()
    fun observeLanguage(): Flow<String> = languagePreference.observe()

    // Preference setters
    suspend fun setTheme(theme: String) = themePreference.set(theme)
    suspend fun setNotifications(enabled: Boolean) = notificationsPreference.set(enabled)
    suspend fun setLanguage(language: String) = languagePreference.set(language)
    @OptIn(ExperimentalTime::class)
    suspend fun updateLastLogin() = lastLoginPreference.set(Clock.System.now().toEpochMilliseconds())

    // Combined settings flow
    fun observeUserSettings(): Flow<UserSettings> = combine(
        observeTheme(),
        observeNotifications(),
        observeLanguage()
    ) { theme, notifications, language ->
        UserSettings(theme, notifications, language)
    }

    // Bulk operations
    suspend fun resetToDefaults() {
        setTheme("system")
        setNotifications(true)
        setLanguage("en")
    }
}

/**
 * Data repository with caching and offline support
 */
class PostRepository(
    override val storage: ReactiveStorage,
    override val cacheStorage: CacheStorage
) : StorageRepository(storage, cacheStorage) {

    /**
     * Load posts with smart caching
     */
    fun loadPosts(): Flow<UiState<List<Post>>> {
        return loadCached("posts_list", {
            // Simulate API call
            listOf(
                Post("1", "First Post", "Content 1", "user1"),
                Post("2", "Second Post", "Content 2", "user2")
            )
        }, ttl = 10.minutes)
    }

    /**
     * Load specific post with caching
     */
    fun loadPost(postId: String): Flow<UiState<Post>> {
        return loadCached("post_$postId", {
            // Simulate API call
            Post(postId, "Post $postId", "Content for post $postId", "author")
        }, ttl = 5.minutes)
    }

    /**
     * Save post locally
     */
    suspend fun savePost(post: Post): StorageResult<Unit> {
        return save("post_${post.id}", post)
    }

    /**
     * Observe post changes
     */
    fun observePost(postId: String): Flow<Post?> {
        return observe("post_$postId", null)
    }

    /**
     * Search posts with caching
     */
    fun searchPosts(query: String): Flow<UiState<List<Post>>> {
        return cachedStorageFlow(
            key = "search_$query",
            operation = {
                // Simulate search API call
                listOf(
                    Post("search1", "Search Result 1", "Content matching $query", "user1"),
                    Post("search2", "Search Result 2", "More content for $query", "user2")
                )
            },
            ttl = 2.minutes,
            cacheStorage = cacheStorage
        ).asUiState()
    }

    /**
     * Clear all cached data
     */
    suspend fun clearCache(): StorageResult<Unit> {
        return clearAll()
    }
}

/**
 * User data repository with two-way binding
 */
class UserRepository(private val storage: ReactiveStorage) {

    /**
     * Create two-way binding for user data
     */
    fun createUserBinding(userId: String): StorageBinding<User?> {
        return storage.createBinding("user_$userId", null)
    }

    /**
     * Load user with fallback
     */
    fun loadUser(userId: String): Flow<UiState<User>> {
        return storageUiFlow {
            source {
                // Simulate API call that might fail
                if (userId == "error") {
                    throw StorageError.KeyNotFound("User not found")
                }
                User(
                    id = userId,
                    name = "User $userId",
                    email = "$userId@example.com",
                    settings = UserSettings("dark", true, "en")
                )
            }
            retryOnFailure(maxAttempts = 3)
            fallbackTo(
                User(
                    id = userId,
                    name = "Unknown User",
                    email = "unknown@example.com",
                    settings = UserSettings("system", false, "en")
                )
            )
            enableLogging("UserRepository")
        }
    }

    /**
     * Save user data
     */
    suspend fun saveUser(user: User): StorageResult<Unit> {
        return storage.put("user_${user.id}", user)
    }

    /**
     * Observe user changes
     */
    fun observeUser(userId: String): Flow<User?> {
        return storage.observeKeySafe("user_$userId", null)
    }
}

/**
 * Usage examples for different scenarios
 */
object StorageUsageExamples {

    /**
     * Basic key-value storage example
     */
    suspend fun basicStorageExample() {
        val storage = SimpleMemoryStorage()

        // Simple put/get operations
        storage.putString("username", "john_doe")
        val username = storage.getString("username").dataOrNull()
        println("Username: $username")

        // Observe changes
        storage.observeString("username").collect { value ->
            println("Username changed to: $value")
        }
    }

    /**
     * Preferences example
     */
    suspend fun preferencesExample() {
        val storage = SimpleMemoryStorage()
        val prefsRepo = UserPreferencesRepository(storage)

        // Set preferences
        prefsRepo.setTheme("dark")
        prefsRepo.setNotifications(false)
        prefsRepo.setLanguage("es")

        // Observe combined settings
        prefsRepo.observeUserSettings().collect { settings ->
            println("Theme: ${settings.theme}")
            println("Notifications: ${settings.notifications}")
            println("Language: ${settings.language}")
        }
    }

    /**
     * Repository pattern example
     */
    suspend fun repositoryExample() {
        val storage = SimpleMemoryStorage()
        val cacheStorage = SimpleCacheStorage()
        val postRepo = PostRepository(storage, cacheStorage)

        // Load posts with caching
        postRepo.loadPosts().collect { uiState ->
            when (uiState) {
                is UiState.Loading -> println("Loading posts...")
                is UiState.Success -> {
                    println("Loaded ${uiState.data.size} posts:")
                    uiState.data.forEach { post ->
                        println("- ${post.title}")
                    }
                }
                is UiState.Error -> println("Error: ${uiState.message}")
            }
        }

        // Search posts
        postRepo.searchPosts("kotlin").collect { searchResults ->
            when (searchResults) {
                is UiState.Success -> {
                    println("Search results:")
                    searchResults.data.forEach { post ->
                        println("- ${post.title}")
                    }
                }
                else -> { /* Handle loading/error */ }
            }
        }
    }

    /**
     * Two-way binding example
     */
    suspend fun bindingExample() {
        val storage = SimpleMemoryStorage()
        val userRepo = UserRepository(storage)

        val userBinding = userRepo.createUserBinding("123")

        // Observe value changes
        userBinding.observeValue().collect { user ->
            println("User binding changed: ${user?.name}")
        }

        // Update value through binding
        val newUser = User("123", "John Doe", "john@example.com",
            UserSettings("light", true, "en"))
        userBinding.setValue(newUser)
    }

    /**
     * Caching and TTL example
     */
    suspend fun cachingExample() {
        val cacheStorage = SimpleCacheStorage()

        // Store data with TTL
        cacheStorage.putWithTtl("temp_data", "expires_soon", 1.minutes)

        // Check if data is still valid
        val isExpired = cacheStorage.isExpired("temp_data").dataOrNull() ?: true
        if (!isExpired) {
            val data = cacheStorage.getIfValid<String>("temp_data").dataOrNull()
            println("Cached data: $data")
        }

        // Cleanup expired entries
        val cleanedCount = cacheStorage.cleanupExpired().dataOrNull() ?: 0
        println("Cleaned up $cleanedCount expired entries")
    }

    /**
     * Error handling and fallbacks example
     */
    suspend fun errorHandlingExample() {
        val storage = SimpleMemoryStorage()

        // Storage operation with fallback
        val dataFlow = storageFlow<String> {
            source {
                // Simulate operation that might fail
                if (Random.nextDouble() < 0.5) {
                    throw StorageError.DatabaseError("Random failure")
                }
                "Success data"
            }
            retryOnFailure(maxAttempts = 3)
            fallbackTo("Fallback data")
            enableLogging("ErrorHandling")
        }

        dataFlow.collect { result ->
            result.fold(
                onFailure = { error -> println("Error: ${error.message}") },
                onSuccess = { data -> println("Data: $data") },
                onLoading = { println("Loading...") }
            )
        }
    }

    /**
     * Flow extensions example
     */
    suspend fun flowExtensionsExample() {
        val storage = SimpleMemoryStorage()

        // Pre-populate some data
        storage.put("number", 5)

        // Use flow extensions
        storage.observe<Int>("number")
            .mapStorageData { it?.times(2) ?: 0 } // Transform data
            .onStorageSuccess { data ->
                println("Double value: $data")
            }
            .onStorageError { error ->
                println("Storage error: ${error.message}")
            }
            .storageOrElse { 0 } // Provide fallback
            .asUiState() // Convert to UI state
            .collect { uiState ->
                when (uiState) {
                    is UiState.Success -> println("UI State: ${uiState.data}")
                    is UiState.Error -> println("UI Error: ${uiState.message}")
                    is UiState.Loading -> println("UI Loading...")
                }
            }
    }

    /**
     * Advanced caching patterns
     */
    @OptIn(ExperimentalTime::class)
    suspend fun advancedCachingExample() {
        val storage = SimpleMemoryStorage()
        val cacheStorage = SimpleCacheStorage()

        // Cache expensive operation results
        val expensiveDataFlow = cachedStorageFlow(
            key = "expensive_operation",
            operation = {
                // Simulate expensive computation
                kotlinx.coroutines.delay(2000)
                "Expensive result: ${Clock.System.now().toEpochMilliseconds()}"
            },
            ttl = 1.hours
        )

        // First call - will compute and cache
        println("First call:")
        expensiveDataFlow.first().onSuccess { data ->
            println("Result: $data")
        }

        // Second call - will use cache
        println("Second call (should be cached):")
        expensiveDataFlow.first().onSuccess { data ->
            println("Cached result: $data")
        }
    }

    /**
     * Storage synchronization example
     */
    suspend fun syncExample() {
        val localStorage = SimpleMemoryStorage()
        val remoteStorage = SimpleMemoryStorage() // Simulate remote storage

        // Sync data between storages
        val syncFlow = localStorage.observe<String>("sync_key")
            .syncWith(remoteStorage, "sync_key")

        syncFlow.collect { result ->
            result.onSuccess { data ->
                println("Synced data: $data")
            }
        }

        // Update local storage - will sync to remote
        localStorage.put("sync_key", "synced_value")
    }
}