# 🎨 FlowKit Compose - Reactive UI for All Platforms!

The ultimate **Compose Multiplatform** integration for FlowKit SDK - where reactive programming meets modern cross-platform UI!

## ✨ Platform Support

🤖 **Android** - Full Material Design 3 support  
🍎 **iOS** - Native iOS integration with UIKit  
🖥️ **Desktop** - JVM Desktop applications  
🌐 **Web** - Kotlin/JS web applications

## ✨ Features

🎯 **Smart State Collection** - Automatic lifecycle-aware state collection  
🎭 **MVI Integration** - Seamless MVI pattern with Compose  
📝 **Reactive Forms** - Type-safe forms with automatic validation  
🧭 **Cross-Platform Navigation** - Reactive navigation with side effects  
⚡ **Platform-Native Side Effects** - Toasts, haptics, dialogs per platform  
🔄 **Loading States** - Beautiful loading/error/success patterns  
🎪 **Zero Boilerplate** - English-like DSL for common patterns  
🌍 **True Multiplatform** - One codebase, all platforms

## 🚀 Quick Start

### Basic State Collection (Works on all platforms!)

```kotlin
@Composable
fun UserScreen(userFlow: Flow<UiState<User>>) {
    userFlow.HandleUiState(
        onLoading = { LoadingSpinner() },
        onError = { error -> ErrorMessage(error.message) },
        onSuccess = { user -> UserProfile(user) }
    )
}
```

### MVI Pattern (Cross-platform)

```kotlin
@Composable
fun PostsScreen() {
    val container = rememberMviContainer(
        initialState = PostsState(),
        reducer = PostsReducer()
    )
    
    MviCompose(
        container = container,
        onSideEffect = { effect ->
            when (effect) {
                is NavigateToPost -> navigator.navigate("post/${effect.id}")
                is ShowMessage -> showPlatformMessage(effect.message)
            }
        }
    ) { state, onIntent ->
        LazyColumn {
            items(state.posts) { post ->
                PostCard(
                    post = post,
                    onClick = { onIntent(PostsIntent.SelectPost(post)) }
                )
            }
        }
    }
}
```

### Smart Forms (All platforms)

```kotlin
@Composable
fun LoginForm() {
    val form = rememberForm {
        emailField("email", required = true)
        passwordField("password", minLength = 6)
    }
    
    val emailField = form.getField<String>("email")!!
    val passwordField = form.getField<String>("password")!!
    
    Column {
        EmailTextField(emailField)
        PasswordTextField(passwordField)
        
        FormSubmitButton(form, "Login") {
            // Handle login - works on all platforms!
        }
    }
}
```

### Network Integration (Cross-platform)

```kotlin
@Composable
fun DataScreen() {
    val dataFlow = remember {
        networkUiFlow {
            source { apiService.getData() }
            cache("data_key", ttl = 5.minutes)
            retryOnFailure(maxAttempts = 3)
        }
    }
    
    val data = dataFlow.collectAsData(
        onError = { error -> ErrorCard(error) },
        onLoading = { LoadingCard() }
    )
    
    data?.let { DataContent(it) }
}
```

## 🌍 Cross-Platform Architecture

### Platform-Specific Implementations

FlowKit Compose automatically adapts to each platform:

**🤖 Android:**
- Material Design 3 components
- Android Navigation Component
- Toast messages
- Haptic feedback via Vibrator
- Android lifecycle integration

**🍎 iOS:**
- Native UIKit integration
- UINavigationController concepts
- UIAlertController for dialogs
- UIImpactFeedbackGenerator for haptics
- iOS-style toast overlays

**🖥️ Desktop:**
- Desktop-optimized components
- Keyboard navigation
- Desktop-specific UI patterns

**🌐 Web:**
- Browser-compatible implementations
- Web-specific navigation
- Progressive Web App support

### Platform Detection

```kotlin
// Automatic platform detection
@Composable
fun PlatformAwareComponent() {
    val messageHandler = rememberPlatformMessageHandler()
    val hapticHandler = rememberPlatformHapticHandler()
    val navigator = rememberNavigator()
    
    // Code automatically adapts to current platform!
}
```

## 🎭 Cross-Platform MVI

### Universal State Management

```kotlin
// Works identically on all platforms!
data class AppState(
    val user: User? = null,
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false
) : MviState

sealed class AppIntent : MviIntent {
    object LoadData : AppIntent()
    data class SelectPost(val post: Post) : AppIntent()
    object Refresh : AppIntent()
}

sealed class AppSideEffect : MviSideEffect {
    data class NavigateToPost(val id: String) : NavigationSideEffect()
    data class ShowMessage(val text: String) : UiSideEffect()
}

// Complete MVI integration - same code, all platforms!
@Composable
fun AppScreen() {
    val container = rememberMviContainer(
        initialState = AppState(),
        reducer = AppReducer(),
        enableLogging = true
    )
    
    MviWithSideEffects(
        container = container,
        onCustomSideEffect = { effect ->
            when (effect) {
                is AppSideEffect.NavigateToPost -> {
                    navigator.navigate("post/${effect.id}")
                }
                is AppSideEffect.ShowMessage -> {
                    showPlatformMessage(effect.text)
                }
            }
        }
    ) { state, onIntent ->
        AppContent(state, onIntent)
    }
}
```

## 📝 Cross-Platform Forms

### Universal Form Validation

```kotlin
@Composable
fun RegistrationForm() {
    val form = rememberForm {
        stringField("name", validator = FormValidation.required())
        emailField("email", required = true)
        passwordField("password", minLength = 8)
        stringField("confirmPassword", validator = FormValidation.combine(
            FormValidation.required(),
            FormValidation.matches(passwordField.value) { "Passwords don't match" }
        ))
    }
    
    // Same form components work on all platforms!
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FormTextField(form.getField("name")!!, "Full Name")
        EmailTextField(form.getField("email")!!)
        PasswordTextField(form.getField("password")!!)
        PasswordTextField(form.getField("confirmPassword")!!, "Confirm Password")
        
        FormSubmitButton(form, "Create Account") {
            // Validation and submission logic - universal!
            handleRegistration(
                name = form.getField<String>("name")!!.value.value,
                email = form.getField<String>("email")!!.value.value,
                password = form.getField<String>("password")!!.value.value
            )
        }
    }
}
```

## 🧭 Cross-Platform Navigation

### Universal Navigation System

```kotlin
// Define navigation events once, use everywhere!
sealed class NavigationSideEffect : MviSideEffect {
    data class NavigateToUser(val userId: String) : NavigationSideEffect()
    object NavigateToSettings : NavigationSideEffect()
    object NavigateBack : NavigationSideEffect()
}

@Composable
fun AppWithNavigation() {
    val navigator = rememberNavigator() // Platform-specific!
    val container = rememberMviContainer(/* ... */)
    
    MviNavigationCompose(
        container = container,
        navigator = navigator
    ) { state, onIntent, nav ->
        // Navigation handled automatically on all platforms!
        AppContent(state, onIntent)
    }
}
```

### Platform-Specific Navigation

**Android:** Uses Navigation Component
```kotlin
// In androidMain
class AndroidNavigator(private val navController: NavController) : Navigator {
    override fun navigate(route: String) {
        navController.navigate(route)
    }
}
```

**iOS:** Integrates with UINavigationController
```kotlin
// In iosMain  
class IOSNavigator : Navigator {
    override fun navigate(route: String) {
        // iOS-specific navigation implementation
        pushViewController(route)
    }
}
```

## ⚡ Platform-Native Side Effects

### Universal Side Effect System

```kotlin
// Define once, adapts to each platform automatically!
sealed class UiSideEffect : MviSideEffect {
    data class ShowMessage(val text: String, val type: MessageType) : UiSideEffect()
    data class HapticFeedback(val type: HapticType) : UiSideEffect()
    data class ShowDialog(val title: String, val message: String) : UiSideEffect()
}

// In your reducer
override suspend fun reduce(state: State, intent: Intent): ReducerResult<State, SideEffect> {
    return when (intent) {
        is ShowSuccess -> ReducerResult(
            newState = state,
            sideEffects = listOf(
                SideEffects.message("Success!", MessageType.Success),
                SideEffects.haptic(HapticType.Success)
            )
        )
    }
}
```

### Platform Adaptations

**🤖 Android:**
- Toast messages
- Vibrator haptic feedback
- Material dialogs

**🍎 iOS:**
- UIAlertController
- UIImpactFeedbackGenerator
- Custom toast overlays

**🖥️ Desktop:**
- Desktop notifications
- System sounds
- Desktop dialogs

## 🔄 Cross-Platform Loading States

### Universal Loading Patterns

```kotlin
@Composable
fun DataScreen() {
    val dataFlow = networkUiFlow { /* ... */ }
    
    // Works identically on all platforms!
    dataFlow.HandleUiState(
        onLoading = { PlatformLoadingSpinner() },
        onError = { error -> PlatformErrorCard(error) },
        onSuccess = { data -> DataList(data) }
    )
}

@Composable
fun PlatformLoadingSpinner() {
    // Automatically adapts to platform design guidelines
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}
```

## 💾 Cross-Platform Storage Integration

### Universal Reactive Storage

```kotlin
@Composable
fun SettingsScreen() {
    val storage = remember { SimpleMemoryStorage() }
    
    // Same reactive patterns on all platforms!
    val theme by storage.observeString("theme", "system").collectAsState()
    val notifications by storage.observeBoolean("notifications", true).collectAsState()
    
    Column {
        SettingRow(
            title = "Theme",
            value = theme,
            onValueChange = { newTheme ->
                storage.putString("theme", newTheme)
            }
        )
        
        SettingRow(
            title = "Notifications", 
            checked = notifications,
            onCheckedChange = { enabled ->
                storage.putBoolean("notifications", enabled)
            }
        )
    }
}
```

## 📦 Installation

### Gradle Setup (Version Catalog)

Add to your `libs.versions.toml`:

```toml
[versions]
flowkit = "0.1.0-alpha"
compose-multiplatform = "1.7.0"

[libraries]
flowkit-compose = { module = "io.flowkit:flowkit-compose", version.ref = "flowkit" }
flowkit-core = { module = "io.flowkit:flowkit-core", version.ref = "flowkit" }
flowkit-network = { module = "io.flowkit:flowkit-network", version.ref = "flowkit" }
flowkit-storage = { module = "io.flowkit:flowkit-storage", version.ref = "flowkit" }

[plugins]
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
```

### Module build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    jvm("desktop")
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.flowkit.compose)
            implementation(libs.flowkit.core)
            implementation(libs.flowkit.network)
            implementation(libs.flowkit.storage)
            
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
        }
    }
}
```

## 🔧 Migration Guide

### From Single Platform to Multiplatform

```kotlin
// Before: Android-only
@Composable
fun OldScreen() {
    val viewModel: MyViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

// After: Cross-platform FlowKit
@Composable  
fun NewScreen() {
    val container = rememberMviContainer(
        initialState = MyState(),
        reducer = MyReducer()
    )
    
    MviWithSideEffects(container) { state, onIntent ->
        // Same UI code works on all platforms!
        MyContent(state, onIntent)
    }
}
```

## 🎯 Best Practices

### 1. Platform-Agnostic State Design

```kotlin
// ✅ Good: Platform-independent state
data class ScreenState(
    val data: List<Item>,
    val isLoading: Boolean,
    val error: String?
) : MviState

// ❌ Avoid: Platform-specific state
data class AndroidScreenState(
    val navController: NavController, // Android-specific!
    val context: Context              // Android-specific!
) : MviState
```

### 2. Universal Side Effects

```kotlin
// ✅ Good: Platform-agnostic effects
sealed class AppSideEffect : MviSideEffect {
    data class ShowMessage(val text: String) : UiSideEffect()
    data class NavigateToScreen(val route: String) : NavigationSideEffect()
    object TriggerHaptic : UiSideEffect()
}

// ❌ Avoid: Platform-specific effects
sealed class AndroidSideEffect : MviSideEffect {
    data class ShowToast(val context: Context, val message: String) : AndroidSideEffect()
    data class StartActivity(val intent: Intent) : AndroidSideEffect()
}
```

### 3. Responsive Design Patterns

```kotlin
// ✅ Good: Adaptive UI for different platforms
@Composable
fun AdaptiveLayout(content: @Composable () -> Unit) {
    val windowInfo = rememberWindowInfo()
    
    when {
        windowInfo.isCompact -> {
            // Mobile layout (Android/iOS)
            Column { content() }
        }
        windowInfo.isMedium -> {
            // Tablet layout
            Row { content() }
        }
        else -> {
            // Desktop layout
            NavigationRail { content() }
        }
    }
}
```

### 4. Network Integration

```kotlin
// ✅ Good: Universal network handling
@Composable
fun DataScreen() {
    val dataFlow = remember {
        networkUiFlow {
            source { 
                // Use Ktor - works on all platforms!
                apiClient.getData() 
            }
            cache("data", ttl = 5.minutes)
            retryOnFailure(maxAttempts = 3)
        }
    }
    
    dataFlow.HandleUiState { data ->
        DataList(data)
    }
}
```

## 🚀 Advanced Examples

### Complete Multiplatform App

```kotlin
// Main App Composable - works on all platforms!
@Composable
fun FlowKitApp() {
    val navigator = rememberNavigator()
    
    MaterialTheme {
        AppNavigation(navigator = navigator)
    }
}

@Composable
fun AppNavigation(navigator: Navigator) {
    var currentScreen by remember { mutableStateOf("home") }
    
    when (currentScreen) {
        "home" -> HomeScreen(
            onNavigateToProfile = { 
                navigator.navigate("profile")
                currentScreen = "profile"
            }
        )
        "profile" -> ProfileScreen(
            onNavigateBack = { 
                navigator.popBackStack()
                currentScreen = "home"
            }
        )
    }
}

// Platform-specific main functions:

// Android - MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlowKitApp()
        }
    }
}

// iOS - Main.kt  
fun MainViewController() = ComposeUIViewController {
    FlowKitApp()
}

// Desktop - Main.kt
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "FlowKit App") {
        FlowKitApp()
    }
}
```

### Advanced State Management

```kotlin
// Universal ViewModel-like container
class AppContainer {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    
    private val _sideEffects = MutableSharedFlow<AppSideEffect>()
    val sideEffects: Flow<AppSideEffect> = _sideEffects.asSharedFlow()
    
    fun processIntent(intent: AppIntent) {
        when (intent) {
            is AppIntent.LoadUser -> {
                // Universal business logic
                loadUserData()
            }
            is AppIntent.ShowMessage -> {
                _sideEffects.tryEmit(
                    AppSideEffect.ShowMessage(intent.message)
                )
            }
        }
    }
    
    private fun loadUserData() {
        // Use FlowKit network module
        viewModelScope.launch {
            networkFlow {
                source { userRepository.getUser() }
                retryOnFailure(maxAttempts = 3)
            }.collect { result ->
                _state.value = _state.value.copy(
                    user = result.dataOrNull(),
                    isLoading = result.isLoading
                )
            }
        }
    }
}

@Composable
fun AppScreen() {
    val container = remember { AppContainer() }
    val state by container.state.collectAsState()
    
    container.sideEffects.CollectSideEffects { effect ->
        when (effect) {
            is AppSideEffect.ShowMessage -> {
                // Platform-specific message handling
                showPlatformMessage(effect.message)
            }
        }
    }
    
    AppContent(state) { intent ->
        container.processIntent(intent)
    }
}
```

### Platform-Specific Customization

```kotlin
// Common interface
interface PlatformSpecificHandler {
    fun handleSpecialAction()
    fun getSystemInfo(): String
}

// Android implementation (androidMain)
class AndroidSpecificHandler : PlatformSpecificHandler {
    override fun handleSpecialAction() {
        // Android-specific implementation
    }
    
    override fun getSystemInfo(): String {
        return "Android ${Build.VERSION.RELEASE}"
    }
}

// iOS implementation (iosMain)  
class IOSSpecificHandler : PlatformSpecificHandler {
    override fun handleSpecialAction() {
        // iOS-specific implementation
    }
    
    override fun getSystemInfo(): String {
        return "iOS ${UIDevice.currentDevice.systemVersion}"
    }
}

// Usage in common code
@Composable
expect fun rememberPlatformHandler(): PlatformSpecificHandler

@Composable
fun PlatformAwareScreen() {
    val platformHandler = rememberPlatformHandler()
    
    Column {
        Text("Running on: ${platformHandler.getSystemInfo()}")
        
        Button(
            onClick = { platformHandler.handleSpecialAction() }
        ) {
            Text("Platform Action")
        }
    }
}
```

## 🎨 Theming Across Platforms

### Universal Theme System

```kotlin
// Define your theme once, use everywhere!
@Composable
fun FlowKitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> darkColorScheme(
            primary = Purple80,
            secondary = PurpleGrey80,
            tertiary = Pink80
        )
        else -> lightColorScheme(
            primary = Purple40,
            secondary = PurpleGrey40,
            tertiary = Pink40
        )
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Platform-specific theme adaptations
@Composable
expect fun isSystemInDarkTheme(): Boolean

// Android implementation
@Composable
actual fun isSystemInDarkTheme(): Boolean {
    return androidx.compose.foundation.isSystemInDarkTheme()
}

// iOS implementation  
@Composable
actual fun isSystemInDarkTheme(): Boolean {
    return UITraitCollection.currentTraitCollection.userInterfaceStyle == UIUserInterfaceStyleDark
}
```

## 🧪 Testing Across Platforms

### Universal Testing Approach

```kotlin
// Common test code works for all platforms!
class FlowKitComposeTest {
    
    @Test
    fun testMviContainer() = runTest {
        val container = TestMviContainer(
            initialState = TestState(),
            reducer = TestReducer()
        )
        
        container.processIntent(TestIntent.Increment)
        
        assertEquals(1, container.currentState().count)
    }
    
    @Test
    fun testNetworkIntegration() = runTest {
        val mockApi = MockApiService()
        val dataFlow = networkUiFlow {
            source { mockApi.getData() }
        }
        
        dataFlow.test {
            assertEquals(UiState.Loading, awaitItem())
            assertEquals(UiState.Success(testData), awaitItem())
        }
    }
}

// Platform-specific test setup
expect class PlatformTestSetup {
    fun setupPlatformSpecificMocks()
}

// Android test setup
actual class PlatformTestSetup {
    actual fun setupPlatformSpecificMocks() {
        // Android-specific test setup
        Mockito.mockStatic(Toast::class.java)
    }
}

// iOS test setup
actual class PlatformTestSetup {
    actual fun setupPlatformSpecificMocks() {
        // iOS-specific test setup
    }
}
```

## 🚀 Performance Optimization

### Cross-Platform Performance Tips

```kotlin
// ✅ Good: Efficient state management
@Composable
fun OptimizedScreen() {
    val container = rememberMviContainer(
        initialState = ScreenState(),
        reducer = ScreenReducer()
    )
    
    // Use derivedStateOf for computed values
    val filteredItems by remember {
        derivedStateOf {
            state.items.filter { it.isVisible }
        }
    }
    
    // Minimize recompositions with stable keys
    LazyColumn {
        items(
            items = filteredItems,
            key = { item -> item.id }
        ) { item ->
            OptimizedItemCard(item)
        }
    }
}

@Composable
fun OptimizedItemCard(
    item: Item,
    modifier: Modifier = Modifier
) {
    // Use stable parameters to avoid unnecessary recompositions
    val onClick = remember(item.id) {
        { onItemClick(item.id) }
    }
    
    Card(
        onClick = onClick,
        modifier = modifier
    ) {
        ItemContent(item)
    }
}
```

## 🤝 Contributing

We love contributions! Here's how to get started:

1. **Fork the repository**
2. **Create a feature branch**
3. **Make your changes**
4. **Add tests** for new functionality
5. **Ensure all platforms build** successfully
6. **Submit a pull request**

### Development Setup

```bash
# Clone the repository
git clone https://github.com/SaadAziz9956/flowkit-sdk.git

# Build all platforms
./gradlew build

# Run tests
./gradlew testAll

# Generate documentation  
./gradlew generateDocs
```

### Platform-Specific Development

**Android Development:**
- Use Android Studio
- Test on physical devices and emulators
- Ensure Material Design compliance

**iOS Development:**
- Use Xcode with Kotlin Multiplatform plugin
- Test on simulators and devices
- Follow iOS Human Interface Guidelines

**Desktop Development:**
- Use IntelliJ IDEA
- Test on different OS (Windows, macOS, Linux)
- Optimize for keyboard navigation

## 📄 License

Apache License 2.0 - see [LICENSE](../LICENSE) for details.

---

**Made with ❤️ for the multiplatform future**

🌟 **Star us on GitHub** if FlowKit Compose revolutionizes your cross-platform development!  
🐦 **Follow us** for updates and multiplatform tips  
💬 **Join our Discord** for support and discussions  
📱 **Try on all platforms** and share your experiences!

### 🚀 What's Next?

FlowKit Compose is just the beginning! Coming soon:

- 📱 **SwiftUI Integration** - Native iOS SwiftUI components
- 🌐 **Web Components** - Custom web components with Kotlin/JS
- 🖥️ **Desktop Native** - Platform-specific desktop features
- 📊 **Analytics Module** - Cross-platform analytics integration
- 🔐 **Auth Module** - Universal authentication flows

**Ready to build the future of multiplatform apps?** 🚀# 🎨 FlowKit Compose - Reactive UI Made Beautiful!

The ultimate Compose integration for FlowKit SDK - where reactive programming meets modern Android UI!

## ✨ Features

🎯 **Smart State Collection** - Automatic lifecycle-aware state collection  
🎭 **MVI Integration** - Seamless MVI pattern with Compose  
📝 **Reactive Forms** - Type-safe forms with automatic validation  
🧭 **Navigation Made Easy** - Reactive navigation with side effects  
⚡ **Side Effects Handling** - One-time UI events (toasts, dialogs, etc.)  
🔄 **Loading States** - Beautiful loading/error/success patterns  
🎪 **Zero Boilerplate** - English-like DSL for common patterns

## 🚀 Quick Start

### Basic State Collection

```kotlin
@Composable
fun UserScreen(userFlow: Flow<UiState<User>>) {
    userFlow.HandleUiState(
        onLoading = { LoadingSpinner() },
        onError = { error -> ErrorMessage(error.message) },
        onSuccess = { user -> UserProfile(user) }
    )
}
```

### MVI Pattern

```kotlin
@Composable
fun PostsScreen() {
    val container = rememberMviContainer(
        initialState = PostsState(),
        reducer = PostsReducer()
    )
    
    MviCompose(
        container = container,
        onSideEffect = { effect ->
            when (effect) {
                is NavigateToPost -> navController.navigate("post/${effect.id}")
                is ShowToast -> showToast(effect.message)
            }
        }
    ) { state, onIntent ->
        LazyColumn {
            items(state.posts) { post ->
                PostCard(
                    post = post,
                    onClick = { onIntent(PostsIntent.SelectPost(post)) }
                )
            }
        }
    }
}
```

### Smart Forms

```kotlin
@Composable
fun LoginForm() {
    val form = rememberForm {
        emailField("email", required = true)
        passwordField("password", minLength = 6)
    }
    
    val emailField = form.getField<String>("email")!!
    val passwordField = form.getField<String>("password")!!
    
    Column {
        EmailTextField(emailField)
        PasswordTextField(passwordField)
        
        FormSubmitButton(form, "Login") {
            // Handle login
        }
    }
}
```

### Network Integration

```kotlin
@Composable
fun DataScreen() {
    val dataFlow = remember {
        networkUiFlow {
            source { apiService.getData() }
            cache("data_key", ttl = 5.minutes)
            retryOnFailure(maxAttempts = 3)
        }
    }
    
    val data = dataFlow.collectAsData(
        onError = { error -> ErrorCard(error) },
        onLoading = { LoadingCard() }
    )
    
    data?.let { DataContent(it) }
}
```

## 🎭 MVI Pattern

### State Management

```kotlin
// Your MVI contracts
data class AppState(
    val user: User? = null,
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false
) : MviState

sealed class AppIntent : MviIntent {
    object LoadData : AppIntent()
    data class SelectPost(val post: Post) : AppIntent()
    object Refresh : AppIntent()
}

sealed class AppSideEffect : MviSideEffect {
    data class NavigateToPost(val id: String) : AppSideEffect()
    data class ShowMessage(val text: String) : AppSideEffect()
}

// Complete MVI integration
@Composable
fun AppScreen() {
    val container = rememberMviContainer(
        initialState = AppState(),
        reducer = AppReducer(),
        enableLogging = true
    )
    
    MviWithSideEffects(
        container = container,
        onCustomSideEffect = { effect ->
            when (effect) {
                is AppSideEffect.NavigateToPost -> {
                    navController.navigate("post/${effect.id}")
                }
                is AppSideEffect.ShowMessage -> {
                    showSnackbar(effect.text)
                }
            }
        }
    ) { state, onIntent ->
        // Your UI here
        AppContent(state, onIntent)
    }
}
```

## 📝 Form Handling

### Type-Safe Forms

```kotlin
@Composable
fun RegistrationForm() {
    val form = rememberForm {
        stringField("name", validator = FormValidation.required())
        emailField("email", required = true)
        passwordField("password", minLength = 8)
        stringField("confirmPassword", validator = FormValidation.combine(
            FormValidation.required(),
            FormValidation.matches(passwordField.value) { "Passwords don't match" }
        ))
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FormTextField(form.getField("name")!!, "Full Name")
        EmailTextField(form.getField("email")!!)
        PasswordTextField(form.getField("password")!!)
        PasswordTextField(form.getField("confirmPassword")!!, "Confirm Password")
        
        FormSubmitButton(form, "Create Account") {
            // All fields are valid here!
            handleRegistration(
                name = form.getField<String>("name")!!.value.value,
                email = form.getField<String>("email")!!.value.value,
                password = form.getField<String>("password")!!.value.value
            )
        }
    }
}
```

### Custom Validation

```kotlin
val customValidator: suspend (String) -> ValidationResult = { value ->
    if (value.contains("@company.com")) {
        ValidationResult.Valid
    } else {
        ValidationResult.Error("Must be a company email")
    }
}

val emailField = form.stringField(
    "email", 
    validator = FormValidation.combine(
        FormValidation.email(),
        customValidator
    )
)
```

## 🧭 Navigation

### Reactive Navigation

```kotlin
// Define your navigation side effects
sealed class NavigationSideEffect : MviSideEffect {
    data class NavigateToUser(val userId: String) : NavigationSideEffect()
    object NavigateToSettings : NavigationSideEffect()
    object NavigateBack : NavigationSideEffect()
}

@Composable
fun AppWithNavigation() {
    val navController = rememberNavController()
    val container = rememberMviContainer(/* ... */)
    
    MviNavigationCompose(
        container = container,
        navController = navController
    ) { state, onIntent, nav ->
        // Navigation is handled automatically!
        AppContent(state, onIntent)
    }
}
```

### Type-Safe Routes

```kotlin
sealed class AppRoute(val route: String) {
    object Home : AppRoute("home")
    data class UserProfile(val userId: String) : AppRoute("user/$userId")
    data class PostDetail(val postId: String) : AppRoute("post/$postId")
}

// Usage
navController.navigate(AppRoute.UserProfile("123").route)
```

## ⚡ Side Effects

### Common UI Effects

```kotlin
// In your reducer
override suspend fun reduce(state: State, intent: Intent): ReducerResult<State, SideEffect> {
    return when (intent) {
        is ShowSuccessMessage -> ReducerResult(
            newState = state,
            sideEffects = listOf(
                SideEffects.toast("Success!"),
                SideEffects.vibrate(),
                SideEffects.hideKeyboard()
            )
        )
    }
}

// Handle automatically in UI
MviWithSideEffects(container) { state, onIntent ->
    // All side effects handled automatically!
    YourContent(state, onIntent)
}
```

### Custom Side Effects

```kotlin
sealed class CustomSideEffect : MviSideEffect {
    object ShareContent : CustomSideEffect()
    data class OpenUrl(val url: String) : CustomSideEffect()
}

MviWithSideEffects(
    container = container,
    onCustomSideEffect = { effect ->
        when (effect) {
            CustomSideEffect.ShareContent -> {
                shareContent("Check out this app!")
            }
            is CustomSideEffect.OpenUrl -> {
                openBrowser(effect.url)
            }
        }
    }
) { state, onIntent ->
    YourContent(state, onIntent)
}
```

## 🔄 Loading States

### Smart Loading Patterns

```kotlin
@Composable
fun DataScreen() {
    val dataFlow = networkUiFlow { /* ... */ }
    
    // Option 1: Automatic handling
    dataFlow.HandleUiState(
        onLoading = { CustomLoadingSpinner() },
        onError = { error -> CustomErrorCard(error) },
        onSuccess = { data -> DataList(data) }
    )
    
    // Option 2: Manual handling
    val state by dataFlow.collectAsUiState()
    AsyncContent(
        state = state,
        onRetry = { refreshData() }
    ) { data ->
        DataList(data)
    }
}
```

### Refreshable Content

```kotlin
@Composable
fun RefreshableScreen() {
    val refreshableState = rememberRefreshableState(
        flow = dataFlow,
        onRefresh = { triggerRefresh() }
    )
    
    PullToRefreshBox(
        isRefreshing = refreshableState.isRefreshing,
        onRefresh = refreshableState.refresh
    ) {
        when (val state = refreshableState.state) {
            is UiState.Success -> DataList(state.data)
            is UiState.Error -> ErrorMessage(state.message)
            is UiState.Loading -> LoadingSpinner()
        }
    }
}
```

## 💾 Storage Integration

### Reactive Preferences

```kotlin
@Composable
fun SettingsScreen() {
    val storage = remember { SimpleMemoryStorage() }
    
    // Observe settings reactively
    val theme by storage.observeString("theme", "system").collectAsState()
    val notifications by storage.observeBoolean("notifications", true).collectAsState()
    
    Column {
        SettingRow(
            title = "Theme",
            value = theme,
            onValueChange = { newTheme ->
                storage.putString("theme", newTheme)
            }
        )
        
        SettingRow(
            title = "Notifications",
            checked = notifications,
            onCheckedChange = { enabled ->
                storage.putBoolean("notifications", enabled)
            }
        )
    }
}
```

### Storage-Backed State

```kotlin
@Composable
fun UserProfileScreen() {
    val storage = remember { SimpleMemoryStorage() }
    val binding = storage.createBinding("user_profile", UserProfile())
    
    val userProfile by binding.observeValue().collectAsState()
    
    Column {
        TextField(
            value = userProfile.name,
            onValueChange = { newName ->
                scope.launch {
                    binding.setValue(userProfile.copy(name = newName))
                }
            }
        )
        
        TextField(
            value = userProfile.email,
            onValueChange = { newEmail ->
                scope.launch {
                    binding.setValue(userProfile.copy(email = newEmail))
                }
            }
        )
    }
}
```

## 🎪 Advanced Patterns

### Combining Multiple Flows

```kotlin
@Composable
fun DashboardScreen() {
    val userFlow = networkUiFlow { userRepository.getCurrentUser() }
    val postsFlow = networkUiFlow { postsRepository.getUserPosts() }
    val notificationsFlow = networkUiFlow { notificationRepository.getUnread() }
    
    val combinedState = remember {
        combineNetworkFlows(userFlow, postsFlow, notificationsFlow) { user, posts, notifications ->
            DashboardData(user, posts, notifications)
        }
    }
    
    combinedState.HandleUiState { dashboardData ->
        DashboardContent(dashboardData)
    }
}
```

### Pagination Support

```kotlin
@Composable
fun PostsListScreen() {
    val pagingFlow = remember {
        networkUiFlow {
            source { postsRepository.getPosts(page = currentPage) }
            cache("posts_page_$currentPage", ttl = 2.minutes)
        }
    }
    
    val pagingState by pagingFlow.collectAsPagingState()
    
    LazyColumn {
        when (pagingState) {
            is UiState.Success -> {
                items(pagingState.data.items) { post ->
                    PostCard(post)
                }
                
                if (pagingState.data.hasMore) {
                    item {
                        LoadMoreButton {
                            loadNextPage()
                        }
                    }
                }
            }
        }
    }
}
```

### Search with Debouncing

```kotlin
@Composable
fun SearchScreen() {
    var query by remember { mutableStateOf("") }
    
    val searchFlow = remember(query) {
        searchNetworkFlow(
            request = { searchRepository.search(query) },
            debounceTime = 300.milliseconds
        )
    }
    
    Column {
        SearchBar(
            query = query,
            onQueryChange = { query = it }
        )
        
        searchFlow.HandleUiState(
            onLoading = { SearchLoadingIndicator() },
            onError = { SearchErrorMessage(it) },
            onSuccess = { results -> SearchResults(results) }
        )
    }
}
```

## 🎯 Best Practices

### 1. State Composition

```kotlin
// ✅ Good: Compose related state
data class ScreenState(
    val user: UiState<User>,
    val posts: UiState<List<Post>>,
    val isRefreshing: Boolean
) : MviState

// ❌ Avoid: Too many individual states
```

### 2. Side Effect Organization

```kotlin
// ✅ Good: Group related side effects
sealed class UserSideEffect : MviSideEffect {
    sealed class Navigation : UserSideEffect() {
        object ToProfile : Navigation()
        object ToSettings : Navigation()
    }
    
    sealed class UI : UserSideEffect() {
        data class ShowToast(val message: String) : UI()
        object ShowLoading : UI()
    }
}
```

### 3. Form Validation

```kotlin
// ✅ Good: Reusable validators
object AppValidators {
    val email = FormValidation.combine(
        FormValidation.required(),
        FormValidation.email()
    )
    
    val strongPassword = FormValidation.combine(
        FormValidation.required(),
        FormValidation.minLength(8),
        { password -> 
            if (password.any { it.isDigit() }) ValidationResult.Valid
            else ValidationResult.Error("Must contain a number")
        }
    )
}
```

### 4. Network Error Handling

```kotlin
// ✅ Good: Centralized error handling
@Composable
fun AppErrorHandler(
    errorFlow: Flow<UiState.Error>
) {
    LaunchedEffect(Unit) {
        errorFlow.collect { error ->
            when (error.throwable) {
                is NetworkError.NoInternetConnection -> {
                    showSnackbar("No internet connection")
                }
                is NetworkError.HttpError -> {
                    when (error.throwable.code) {
                        401 -> navigateToLogin()
                        500 -> showSnackbar("Server error")
                        else -> showSnackbar("Something went wrong")
                    }
                }
            }
        }
    }
}
```

## 📦 Installation

```kotlin
dependencies {
    implementation("io.flowkit:flowkit-compose:0.1.0-alpha")
    
    // Required dependencies
    implementation("io.flowkit:flowkit-core:0.1.0-alpha")
    implementation("io.flowkit:flowkit-network:0.1.0-alpha")
    implementation("io.flowkit:flowkit-storage:0.1.0-alpha")
}
```

## 🔧 Migration Guide

### From Traditional Compose

```kotlin
// Before: Manual state management
@Composable
fun OldScreen() {
    var isLoading by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<List<Post>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            data = api.getPosts()
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }
    
    when {
        isLoading -> LoadingSpinner()
        error != null -> ErrorMessage(error!!)
        data != null -> PostsList(data!!)
    }
}

// After: FlowKit Compose
@Composable
fun NewScreen() {
    val postsFlow = remember {
        networkUiFlow {
            source { api.getPosts() }
            retryOnFailure(maxAttempts = 3)
            cache("posts", ttl = 5.minutes)
        }
    }
    
    postsFlow.HandleUiState(
        onLoading = { LoadingSpinner() },
        onError = { error -> ErrorMessage(error.message) },
        onSuccess = { posts -> PostsList(posts) }
    )
}
```

## 🎨 Theming & Customization

### Custom Loading Components

```kotlin
@Composable
fun CustomLoadingSpinner() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading awesome content...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// Use everywhere
dataFlow.HandleUiState(
    onLoading = { CustomLoadingSpinner() }
)
```

### Custom Error Handling

```kotlin
@Composable
fun CustomErrorCard(
    error: UiState.Error,
    onRetry: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Oops! Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
            )
            
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text("Try Again")
                }
            }
        }
    }
}
```

## 🤝 Contributing

We love contributions! Check out our [Contributing Guide](../CONTRIBUTING.md) to get started.

## 📄 License

Apache License 2.0 - see [LICENSE](../LICENSE) for details.

---

**Made with ❤️ by the FlowKit team**

🌟 **Star us on GitHub** if FlowKit Compose makes your UI development awesome!  
🐦 **Follow us** for updates and tips  
💬 **Join our Discord** for support and discussions