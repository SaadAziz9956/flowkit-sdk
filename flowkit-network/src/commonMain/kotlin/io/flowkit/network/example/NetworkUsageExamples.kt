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