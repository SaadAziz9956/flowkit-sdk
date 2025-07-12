package io.flowkit.codegen.example

import io.flowkit.core.MviState
import io.flowkit.core.MviIntent
import io.flowkit.core.MviSideEffect
import io.flowkit.codegen.annotations.*
import io.flowkit.core.MviReducer
import io.flowkit.core.ReducerResult

/**
 * Example 1: Simple User List Screen
 *
 * This example shows the minimal setup required for a basic MVI screen
 * that works across Android and iOS platforms.
 */

@FlowKitMvi(
    targetPlatforms = [TargetPlatform.ALL],  // Generate for all platforms
    dependencyInjection = DependencyInjection.AUTO_DETECT  // Auto-detect DI framework
)
data class UserListState(
    val users: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = ""
) : MviState

@FlowKitIntent
sealed class UserListIntent : MviIntent {
    data object LoadUsers : UserListIntent()
    data object RefreshUsers : UserListIntent()
    data class SearchUsers(val query: String) : UserListIntent()
    data class SelectUser(val userId: String) : UserListIntent()
    data object ClearError : UserListIntent()
}

@FlowKitSideEffect
sealed class UserListSideEffect : MviSideEffect {
    data class NavigateToDetail(val userId: String) : UserListSideEffect()
    data class ShowError(val message: String) : UserListSideEffect()
    data class ShowToast(val message: String) : UserListSideEffect()
}

// Sample data model
data class User(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null
)

/*
🎉 GENERATED FILES:
- UserListMviContainer.kt (Core MVI container - works on all platforms)
- UserListViewModel.kt (Android ViewModel with lifecycle integration)
- UserListViewModelIos.kt (iOS ViewModel with Swift interop)
- UserListReducer.kt (Pure function reducer with business logic scaffolding)
- UserListMviContainerTest.kt (Comprehensive integration tests)
- UserListReducerTest.kt (Unit tests for reducer)
- UserListTestUtils.kt (Test utilities and helpers)
*/

/**
 * Example 2: Advanced Configuration with Platform-Specific Features
 *
 * This example demonstrates advanced configuration options including
 * custom naming, specific DI frameworks, and platform targeting.
 */

@FlowKitMvi(
    generateContainer = true,
    generateViewModel = true,
    generateTesting = true,
    enableLogging = true,  // Enable debug logging
    targetPlatforms = [TargetPlatform.ANDROID, TargetPlatform.IOS],
    dependencyInjection = DependencyInjection.KOIN,  // Use Koin for cross-platform DI
    config = FlowKitConfig(
        containerName = "ShoppingCartContainer",
        viewModelName = "ShoppingCartViewModel",
        packageName = "com.example.cart.generated",
        composeIntegration = true,
        coroutineSafe = true
    )
)
data class ShoppingCartState(
    val items: List<CartItem> = emptyList(),
    val totalAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val isLoading: Boolean = false,
    val error: CartError? = null,
    val checkoutStep: CheckoutStep = CheckoutStep.CART
) : MviState

@FlowKitIntent
sealed class ShoppingCartIntent : MviIntent {
    data object LoadCart : ShoppingCartIntent()
    data class AddItem(val product: Product, val quantity: Int = 1) : ShoppingCartIntent()
    data class RemoveItem(val itemId: String) : ShoppingCartIntent()
    data class UpdateQuantity(val itemId: String, val quantity: Int) : ShoppingCartIntent()
    data class ApplyDiscount(val discountCode: String) : ShoppingCartIntent()
    data object ProceedToCheckout : ShoppingCartIntent()
    data object ClearCart : ShoppingCartIntent()

    @CustomReduction  // This intent needs custom business logic
    data class CalculateTotals(val items: List<CartItem>) : ShoppingCartIntent()
}

@FlowKitSideEffect
sealed class ShoppingCartSideEffect : MviSideEffect {
    data class NavigateToCheckout(val cartId: String) : ShoppingCartSideEffect()
    data class ShowItemAdded(val itemName: String) : ShoppingCartSideEffect()
    data class ShowError(val error: CartError) : ShoppingCartSideEffect()
    data class UpdateBadgeCount(val count: Int) : ShoppingCartSideEffect()
    data object VibrateFeedback : ShoppingCartSideEffect()
}

// Supporting data models
data class CartItem(
    val id: String,
    val product: Product,
    val quantity: Int,
    val pricePerItem: Double
)

data class Product(
    val id: String,
    val name: String,
    val price: Double,
    val imageUrl: String
)

sealed class CartError {
    data object NetworkError : CartError()
    data object ItemOutOfStock : CartError()
    data class InvalidDiscount(val code: String) : CartError()
    data class Unknown(val message: String) : CartError()
}

enum class CheckoutStep {
    CART, SHIPPING, PAYMENT, CONFIRMATION
}

/**
 * Example 3: iOS-Specific Optimization
 *
 * This example shows how to create iOS-optimized MVI components
 * with memory management and Swift interop considerations.
 */

@FlowKitMvi(
    targetPlatforms = [TargetPlatform.IOS],  // iOS only
    dependencyInjection = DependencyInjection.KOIN,
    enableLogging = false,  // Disable logging for production
    config = FlowKitConfig(
        composeIntegration = false,  // No Compose on iOS
        coroutineSafe = true
    )
)
data class PhotoGalleryState(
    val photos: List<Photo> = emptyList(),
    val selectedPhoto: Photo? = null,
    val isLoading: Boolean = false,
    val loadingProgress: Float = 0f,
    val error: String? = null
) : MviState

@FlowKitIntent
sealed class PhotoGalleryIntent : MviIntent {
    data object LoadPhotos : PhotoGalleryIntent()
    data class SelectPhoto(val photoId: String) : PhotoGalleryIntent()
    data class DeletePhoto(val photoId: String) : PhotoGalleryIntent()
    data object ClearSelection : PhotoGalleryIntent()
}

@FlowKitSideEffect
sealed class PhotoGallerySideEffect : MviSideEffect {
    data class ShowPhotoDetail(val photo: Photo) : PhotoGallerySideEffect()
    data class ConfirmDeletion(val photoId: String) : PhotoGallerySideEffect()
    data object PhotoDeleted : PhotoGallerySideEffect()
}

data class Photo(
    val id: String,
    val url: String,
    val thumbnailUrl: String,
    val title: String,
    val createdAt: Long
)

/**
 * Example 4: Android-Specific with Hilt Integration
 *
 * This example demonstrates Android-specific configuration with
 * Hilt dependency injection and Compose integration.
 */

@FlowKitMvi(
    targetPlatforms = [TargetPlatform.ANDROID],  // Android only
    dependencyInjection = DependencyInjection.HILT,  // Use Hilt
    generateTesting = true,
    config = FlowKitConfig(
        composeIntegration = true,  // Enable Compose helpers
        coroutineSafe = true
    )
)
data class WeatherState(
    val currentWeather: Weather? = null,
    val forecast: List<Weather> = emptyList(),
    val isLoading: Boolean = false,
    val lastUpdated: Long? = null,
    val error: String? = null,
    val locationName: String = ""
) : MviState

@FlowKitIntent
sealed class WeatherIntent : MviIntent {
    data object LoadCurrentWeather : WeatherIntent()
    data object LoadForecast : WeatherIntent()
    data object RefreshWeather : WeatherIntent()
    data class ChangeLocation(val locationName: String) : WeatherIntent()
}

@FlowKitSideEffect
sealed class WeatherSideEffect : MviSideEffect {
    data class ShowLocationPicker(val currentLocation: String) : WeatherSideEffect()
    data class ShowWeatherAlert(val alertType: String) : WeatherSideEffect()
    data object RequestLocationPermission : WeatherSideEffect()
}

data class Weather(
    val temperature: Double,
    val condition: String,
    val humidity: Int,
    val windSpeed: Double,
    val date: Long
)

/**
 * Example 5: Custom Reducer Implementation
 *
 * This example shows how to use custom reducers for complex business logic
 * while still benefiting from FlowKit's generated infrastructure.
 */

@FlowKitMvi(
    generateContainer = true,
    generateViewModel = true,
    generateTesting = false,  // We'll write custom tests
    targetPlatforms = [TargetPlatform.ALL]
)
data class GameState(
    val score: Int = 0,
    val level: Int = 1,
    val lives: Int = 3,
    val isGameOver: Boolean = false,
    val isPaused: Boolean = false,
    val powerUps: List<PowerUp> = emptyList()
) : MviState

@FlowKitIntent
sealed class GameIntent : MviIntent {
    data object StartGame : GameIntent()
    data object PauseGame : GameIntent()
    data object ResumeGame : GameIntent()
    data object GameOver : GameIntent()

    @CustomReduction  // Complex game logic
    data class UpdateScore(val points: Int, val multiplier: Double) : GameIntent()

    @CustomReduction  // Complex collision detection
    data class ProcessCollision(val entityA: GameEntity, val entityB: GameEntity) : GameIntent()
}

@FlowKitSideEffect
sealed class GameSideEffect : MviSideEffect {
    data object PlaySoundEffect : GameSideEffect()
    data object ShowLevelComplete : GameSideEffect()
    data object TriggerHapticFeedback : GameSideEffect()
    data class SaveHighScore(val score: Int) : GameSideEffect()
}

// Custom reducer for complex game logic
@CustomReducer
class GameReducer : MviReducer<GameState, GameIntent, GameSideEffect> {

    override suspend fun reduce(
        currentState: GameState,
        intent: GameIntent
    ): ReducerResult<GameState, GameSideEffect> {
        return when (intent) {
            is GameIntent.UpdateScore -> {
                val newScore = currentState.score + (intent.points * intent.multiplier).toInt()
                val newLevel = calculateLevel(newScore)
                val sideEffects = mutableListOf<GameSideEffect>()

                if (newLevel > currentState.level) {
                    sideEffects.add(GameSideEffect.ShowLevelComplete)
                }

                if (newScore > getHighScore()) {
                    sideEffects.add(GameSideEffect.SaveHighScore(newScore))
                }

                ReducerResult(
                    newState = currentState.copy(score = newScore, level = newLevel),
                    sideEffects = sideEffects
                )
            }

            is GameIntent.ProcessCollision -> {
                // Complex collision logic here
                val newState = processCollisionLogic(currentState, intent.entityA, intent.entityB)
                ReducerResult(newState)
            }

            else -> {
                // Delegate other intents to generated reducer logic
                ReducerResult(currentState)
            }
        }
    }

    private fun calculateLevel(score: Int): Int = (score / 1000) + 1
    private fun getHighScore(): Int = 0 // Implement high score retrieval
    private fun processCollisionLogic(state: GameState, entityA: GameEntity, entityB: GameEntity): GameState = state
}

// Supporting classes for the game example
data class PowerUp(val type: String, val duration: Long)
data class GameEntity(val id: String, val x: Float, val y: Float)

/**
 * Usage in Android Compose
 *
 * Here's how you would use the generated ViewModels in your Android Compose UI:
 */

/*
@Composable
fun UserListScreen(
    viewModel: UserListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.processIntent(UserListIntent.LoadUsers)
    }
    
    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { sideEffect ->
            when (sideEffect) {
                is UserListSideEffect.NavigateToDetail -> {
                    // Navigate to detail screen
                }
                is UserListSideEffect.ShowError -> {
                    // Show error message
                }
                is UserListSideEffect.ShowToast -> {
                    // Show toast
                }
            }
        }
    }
    
    UserListContent(
        state = state,
        onIntent = viewModel::processIntent
    )
}

@Composable
private fun UserListContent(
    state: UserListState,
    onIntent: (UserListIntent) -> Unit
) {
    Column {
        if (state.isLoading) {
            CircularProgressIndicator()
        }
        
        LazyColumn {
            items(state.users) { user ->
                UserItem(
                    user = user,
                    onClick = { onIntent(UserListIntent.SelectUser(user.id)) }
                )
            }
        }
    }
}
*/

/**
 * Usage in iOS Swift
 *
 * Here's how you would use the generated iOS ViewModels in Swift:
 */

/**
 * Usage in iOS Swift
 *
 * Here's how you would use the generated iOS ViewModels in Swift:
 */

/*
class UserListViewController: UIViewController {
    private let viewModel = UserListViewModelIos(
        scope: CoroutineScopeKt.MainScope(),
        repository: DIContainer.shared.userRepository
    )
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        observeState()
        observeSideEffects()
        
        // Load initial data
        viewModel.processIntent(intent: UserListIntent.LoadUsers())
    }
    
    private func observeState() {
        viewModel.observeState { [weak self] state in
            DispatchQueue.main.async {
                self?.updateUI(with: state)
            }
        }
    }
    
    private func observeSideEffects() {
        viewModel.observeSideEffects { [weak self] sideEffect in
            DispatchQueue.main.async {
                self?.handleSideEffect(sideEffect)
            }
        }
    }
    
    private func updateUI(with state: UserListState) {
        if state.isLoading {
            showLoadingIndicator()
        } else {
            hideLoadingIndicator()
            updateUserList(users: state.users)
        }
        
        if let error = state.error {
            showError(message: error)
        }
    }
    
    private func handleSideEffect(_ sideEffect: UserListSideEffect) {
        switch sideEffect {
        case let navigateToDetail as UserListSideEffect.NavigateToDetail:
            navigateToUserDetail(userId: navigateToDetail.userId)
        case let showError as UserListSideEffect.ShowError:
            showErrorAlert(message: showError.message)
        case let showToast as UserListSideEffect.ShowToast:
            showToast(message: showToast.message)
        default:
            break
        }
    }
    
    deinit {
        viewModel.cleanup()
    }
}
*/

/**
 * Project Setup Guide
 *
 * To use FlowKit CodeGen in your KMP project:
 */

/*
1. Add FlowKit dependencies to your build.gradle.kts:

```kotlin
// In your shared module build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.flowkit:flowkit-core:0.1.0-alpha")
        }
    }
}

dependencies {
    // Add KSP processor for all targets
    add("kspCommonMainMetadata", "io.flowkit:flowkit-codegen:0.1.0-alpha")
    add("kspAndroid", "io.flowkit:flowkit-codegen:0.1.0-alpha")
    add("kspIosX64", "io.flowkit:flowkit-codegen:0.1.0-alpha")
    add("kspIosArm64", "io.flowkit:flowkit-codegen:0.1.0-alpha")
    add("kspIosSimulatorArm64", "io.flowkit:flowkit-codegen:0.1.0-alpha")
}
```

2. Configure KSP options (optional):

```kotlin
ksp {
    arg("flowkit.enableLogging", "true")
    arg("flowkit.generateTests", "true")
    arg("flowkit.packagePrefix", "com.yourapp.generated")
}
```

3. Create your MVI classes with FlowKit annotations:

```kotlin
@FlowKitMvi
data class YourState(...) : MviState

@FlowKitIntent
sealed class YourIntent : MviIntent { ... }

@FlowKitSideEffect  
sealed class YourSideEffect : MviSideEffect { ... }
```

4. Build your project:

```bash
./gradlew build
```

5. Find generated files in:
- `build/generated/ksp/commonMain/kotlin/`
- `build/generated/ksp/android/kotlin/`
- `build/generated/ksp/iosX64/kotlin/`
*/

/**
 * Advanced Configuration Examples
 */

// Example: Custom DI integration with manual dependency injection
@FlowKitMvi(
    dependencyInjection = DependencyInjection.MANUAL,
    config = FlowKitConfig(packageName = "com.example.manual")
)
data class ManualDIState(
    val data: String = ""
) : MviState

// Example: Web-only MVI component
@FlowKitMvi(
    targetPlatforms = [TargetPlatform.WEB],
    config = FlowKitConfig(composeIntegration = false)
)
data class WebOnlyState(
    val content: String = ""
) : MviState

// Example: Desktop-only with custom naming
@FlowKitMvi(
    targetPlatforms = [TargetPlatform.JVM],
    config = FlowKitConfig(
        containerName = "DesktopAppContainer",
        viewModelName = "DesktopAppController"
    )
)
data class DesktopAppState(
    val windowTitle: String = "FlowKit Desktop App"
) : MviState

/**
 * Testing Examples
 *
 * The generated test files provide comprehensive coverage:
 */

/*
class UserListReducerTest {
    @Test
    fun `should load users correctly`() = runTest {
        // Given
        val initialState = UserListState()
        val intent = UserListIntent.LoadUsers
        
        // When
        val result = reducer.reduce(initialState, intent)
        
        // Then
        assertThat(result.newState.isLoading).isTrue()
        assertThat(result.newState.error).isNull()
    }
}

class UserListMviContainerTest {
    @Test
    fun `should emit state changes`() = runTest {
        // Given
        val stateFlow = container.state.test {
            // When
            container.processIntent(UserListIntent.LoadUsers)
            
            // Then
            assertThat(awaitItem().isLoading).isTrue()
        }
    }
}
*/

/**
 * Performance Considerations
 *
 * FlowKit CodeGen is designed for optimal performance:
 */

/*
1. **Compile-time Generation**: All code is generated at compile time,
   resulting in zero runtime overhead.

2. **Memory Efficient**: Generated containers use optimized data structures
   and proper resource management.

3. **Coroutine-Safe**: All generated code is designed for concurrent access
   without performance penalties.

4. **Platform Optimized**: Different optimizations for each platform:
   - Android: ViewModel lifecycle integration
   - iOS: Memory management and Swift interop
   - Common: Pure Kotlin performance

5. **Minimal Dependencies**: Only depends on Kotlin stdlib and coroutines,
   keeping your app size small.
*/

/**
 * Migration Guide
 *
 * Migrating from manual MVI to FlowKit CodeGen:
 */

/*
Step 1: Add FlowKit dependencies and KSP configuration

Step 2: Annotate your existing state classes:
```kotlin
// Before
data class MyState(...)

// After  
@FlowKitMvi
data class MyState(...) : MviState
```

Step 3: Annotate your Intent and SideEffect classes:
```kotlin
@FlowKitIntent
sealed class MyIntent : MviIntent { ... }

@FlowKitSideEffect
sealed class MySideEffect : MviSideEffect { ... }
```

Step 4: Build and replace manual implementations:
- Replace manual containers with generated ones
- Replace manual ViewModels with generated ones
- Use generated tests as starting point

Step 5: Customize as needed:
- Add @CustomReduction for complex business logic
- Configure DI framework integration
- Adjust platform targeting
*/

/**
 * Troubleshooting Common Issues
 */

/*
Issue: "FlowKit processor not found"
Solution: Ensure KSP plugin is applied and processor is in dependencies

Issue: "Generated files not found"
Solution: Check that classes implement required interfaces (MviState, etc.)

Issue: "Compilation errors in generated code"
Solution: Verify annotation configuration and dependency injection setup

Issue: "Tests not compiling"
Solution: Add test dependencies (JUnit, MockK, Turbine) to your project

Issue: "iOS build failing"
Solution: Ensure Kotlin/Native targets are properly configured
*/

/**
 * Best Practices
 */

/*
1. **State Design**:
   - Keep state classes as data classes
   - Use immutable properties (val, not var)
   - Provide sensible default values
   
2. **Intent Design**:
   - Use sealed classes for type safety
   - Group related intents logically
   - Include all necessary data in intent parameters

3. **SideEffect Design**:
   - Use for one-time events only
   - Include all necessary data for UI handling
   - Keep side effects simple and focused

4. **Testing**:
   - Use generated tests as starting point
   - Add custom tests for complex business logic
   - Test state transitions thoroughly

5. **Performance**:
   - Minimize state class size
   - Use efficient data structures
   - Consider memory implications on iOS
*/