package io.flowkit.codegen.annotations

import kotlin.reflect.KClass

/**
 * Primary annotation for MVI code generation with full cross-platform support.
 *
 * This annotation tells FlowKit to generate a complete MVI infrastructure for your state class.
 * The generated code will be pure Kotlin that works on both Android and iOS platforms.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class FlowKitMvi(
    /**
     * Generate the core MVI container implementation.
     * The container manages state transitions and side effects in a thread-safe manner.
     */
    val generateContainer: Boolean = true,

    /**
     * Generate platform-specific ViewModel wrappers.
     * On Android: Creates a proper ViewModel that integrates with the Android lifecycle
     * On iOS: Creates a view model class that follows iOS patterns
     */
    val generateViewModel: Boolean = true,

    /**
     * Generate comprehensive test suites for the generated code.
     * Includes unit tests for reducers, integration tests for containers,
     * and property-based tests for state transitions.
     */
    val generateTesting: Boolean = true,

    /**
     * Enable debug logging in the generated code.
     * When enabled, the generated container will log state transitions,
     * intent processing, and side effect emissions for debugging.
     */
    val enableLogging: Boolean = false,

    /**
     * Target platforms for code generation.
     * This allows you to generate different implementations for different platforms
     * while sharing the core business logic.
     */
    val targetPlatforms: Array<TargetPlatform> = [TargetPlatform.ALL],

    /**
     * Dependency injection framework to integrate with.
     * FlowKit can generate code that works with various DI frameworks
     * or can generate manual dependency injection code.
     */
    val dependencyInjection: DependencyInjection = DependencyInjection.AUTO_DETECT,

    /**
     * Configuration options for customizing the generated code.
     */
    val config: FlowKitConfig = FlowKitConfig()
)

/**
 * Configuration options for fine-tuning FlowKit code generation.
 * This annotation allows you to customize names, packages, and behavior
 * of the generated code to fit your project's conventions.
 */
annotation class FlowKitConfig(
    /**
     * Custom name for the generated MVI container.
     * If empty, FlowKit will derive the name from your state class name.
     * Example: "UserScreenState" -> "UserScreenMviContainer"
     */
    val containerName: String = "",

    /**
     * Custom name for the generated ViewModel.
     * If empty, FlowKit will derive the name from your state class name.
     * Example: "UserScreenState" -> "UserScreenViewModel"
     */
    val viewModelName: String = "",

    /**
     * Custom package name for generated files.
     * If empty, generated files will be placed in the same package as your state class.
     */
    val packageName: String = "",

    /**
     * Whether to generate code that integrates with Compose.
     * When enabled, generates Composable functions and Compose-specific optimizations.
     */
    val composeIntegration: Boolean = true,

    /**
     * Whether to generate coroutine-safe implementations.
     * When enabled (default), all generated code will be safe for concurrent access.
     */
    val coroutineSafe: Boolean = true
)

/**
 * Target platforms for FlowKit code generation.
 * This enum allows you to control which platforms receive generated code,
 * enabling platform-specific optimizations while maintaining shared business logic.
 */
enum class TargetPlatform {
    /**
     * Generate code for all supported platforms.
     * This creates a common implementation that works everywhere,
     * plus platform-specific optimizations where beneficial.
     */
    ALL,

    /**
     * Generate Android-specific code with Android lifecycle integration,
     * ViewModel support, and Android-specific optimizations.
     */
    ANDROID,

    /**
     * Generate iOS-specific code with iOS lifecycle patterns,
     * memory management optimizations, and Swift interop considerations.
     */
    IOS,

    /**
     * Generate JVM/Desktop-specific code for desktop applications
     * using Compose Desktop or other JVM UI frameworks.
     */
    JVM,

    /**
     * Generate Web-specific code for Kotlin/JS applications
     * using Compose for Web or other web frameworks.
     */
    WEB
}

/**
 * Dependency injection framework integration options.
 * FlowKit can generate code that integrates seamlessly with various
 * dependency injection frameworks or provide manual dependency injection.
 */
enum class DependencyInjection {
    /**
     * Automatically detect the DI framework based on available dependencies.
     * FlowKit will scan your project dependencies and generate appropriate code.
     */
    AUTO_DETECT,

    /**
     * Generate code for Koin dependency injection.
     * Koin is cross-platform compatible and works on both Android and iOS.
     */
    KOIN,

    /**
     * Generate code for Hilt dependency injection.
     * Note: Hilt is Android-only, so this will only generate Android code.
     */
    HILT,

    /**
     * Generate code for Kodein dependency injection.
     * Kodein is cross-platform compatible like Koin.
     */
    KODEIN,

    /**
     * Generate code without any dependency injection framework.
     * Dependencies will be passed through constructor parameters.
     */
    MANUAL
}

/**
 * Marks Intent classes for FlowKit processing.
 *
 * Intent classes represent user actions or system events that can change the state.
 * FlowKit uses this annotation to understand the structure of your intents
 * and generate appropriate handling code.
 *
 * Usage example:
 * ```kotlin
 * @FlowKitIntent
 * sealed class UserScreenIntent : MviIntent {
 *     data object LoadUsers : UserScreenIntent()
 *     data class SearchUsers(val query: String) : UserScreenIntent()
 *     data class SelectUser(val userId: String) : UserScreenIntent()
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class FlowKitIntent

/**
 * Marks SideEffect classes for FlowKit processing.
 *
 * SideEffect classes represent one-time events that the UI should handle,
 * such as navigation, showing dialogs, or displaying toasts.
 *
 * Usage example:
 * ```kotlin
 * @FlowKitSideEffect
 * sealed class UserScreenSideEffect : MviSideEffect {
 *     data class NavigateToDetail(val userId: String) : UserScreenSideEffect()
 *     data class ShowError(val message: String) : UserScreenSideEffect()
 *     data class ShowToast(val message: String) : UserScreenSideEffect()
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class FlowKitSideEffect

/**
 * Marks functions or classes that require custom reduction logic.
 *
 * When applied to an Intent class or specific intent, FlowKit will generate
 * a delegate method that you can implement with custom business logic.
 * This allows you to handle complex state transitions while still benefiting
 * from FlowKit's generated infrastructure.
 *
 * Usage example:
 * ```kotlin
 * sealed class UserScreenIntent : MviIntent {
 *     data object LoadUsers : UserScreenIntent()
 *
 *     @CustomReduction
 *     data class ComplexBusinessLogic(val params: ComplexParams) : UserScreenIntent()
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class CustomReduction

/**
 * Marks a reducer class that FlowKit should use instead of generating one.
 *
 * This annotation allows you to provide your own reducer implementation
 * while still benefiting from FlowKit's container and ViewModel generation.
 *
 * Usage example:
 * ```kotlin
 * @CustomReducer
 * class UserScreenReducer : MviReducer<UserScreenState, UserScreenIntent, UserScreenSideEffect> {
 *     override suspend fun reduce(
 *         currentState: UserScreenState,
 *         intent: UserScreenIntent
 *     ): ReducerResult<UserScreenState, UserScreenSideEffect> {
 *         // Your custom reduction logic here
 *     }
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class CustomReducer

/**
 * Configures middleware to be applied to the generated MVI container.
 *
 * Middleware allows you to intercept and potentially modify intents,
 * state changes, and side effects. This is useful for logging, analytics,
 * debugging, or cross-cutting concerns.
 *
 * Usage example:
 * ```kotlin
 * @FlowKitMvi(
 *     middlewares = [LoggingMiddleware::class, AnalyticsMiddleware::class]
 * )
 * data class UserScreenState(/* ... */) : MviState
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class FlowKitMiddleware(
    /**
     * Array of middleware classes to apply to the generated container.
     * Middleware will be applied in the order specified.
     */
    val middlewares: Array<KClass<*>>
)