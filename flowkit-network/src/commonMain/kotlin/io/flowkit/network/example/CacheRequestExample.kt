package io.flowkit.network.example

import io.flowkit.network.cachedNetworkFlow
import io.flowkit.network.fold
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.minutes

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