package io.flowkit.sdk

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform