package io.flowkit.codegen.models

import io.flowkit.codegen.annotations.*
import kotlinx.serialization.Serializable

/**
 * Comprehensive metadata container for MVI code generation.
 *
 * This class captures all the information needed to generate MVI infrastructure
 * from annotated classes. It serves as the bridge between the annotation processor
 * (which runs at compile time) and the code generators (which produce the final Kotlin code).
 *
 * The metadata extraction process follows these steps:
 * 1. Scan for @FlowKitMvi annotations on state classes
 * 2. Find related Intent and SideEffect classes by convention or annotation
 * 3. Extract configuration from annotations
 * 4. Validate the structure meets MVI requirements
 * 5. Build comprehensive metadata for code generation
 */
@Serializable
data class MviMetadata(
    /**
     * Information about the state class that triggered code generation.
     * This includes the class name, package, properties, and type parameters.
     */
    val stateInfo: ClassInfo,

    /**
     * Information about the associated Intent class.
     * May be null if no Intent class was found or specified.
     */
    val intentInfo: ClassInfo?,

    /**
     * Information about the associated SideEffect class.
     * May be null if no SideEffect class was found or specified.
     */
    val sideEffectInfo: ClassInfo?,

    /**
     * The original @FlowKitMvi annotation with all its configuration options.
     * This drives the entire code generation process.
     */
    val annotation: AnnotationInfo,

    /**
     * Code generation configuration derived from annotations and conventions.
     * This includes naming strategies, output locations, and generation flags.
     */
    val generationConfig: GenerationConfig,

    /**
     * Platform-specific generation settings.
     * Different platforms may require different approaches or optimizations.
     */
    val platformConfig: PlatformConfig,

    /**
     * Dependency injection configuration.
     * Determines how dependencies are injected into generated code.
     */
    val diConfig: DependencyInjectionConfig
) {

    /**
     * Validates that the metadata represents a valid MVI structure.
     * This ensures we can generate working code from the provided information.
     */
    fun validate(): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Validate state class implements MviState
        if (!stateInfo.implementsInterface("io.flowkit.core.MviState")) {
            errors.add(ValidationError.InvalidStateClass(
                "State class ${stateInfo.qualifiedName} must implement MviState"
            ))
        }

        // Validate Intent class if present
        intentInfo?.let { intent ->
            if (!intent.implementsInterface("io.flowkit.core.MviIntent")) {
                errors.add(ValidationError.InvalidIntentClass(
                    "Intent class ${intent.qualifiedName} must implement MviIntent"
                ))
            }
        }

        // Validate SideEffect class if present
        sideEffectInfo?.let { sideEffect ->
            if (!sideEffect.implementsInterface("io.flowkit.core.MviSideEffect")) {
                errors.add(ValidationError.InvalidSideEffectClass(
                    "SideEffect class ${sideEffect.qualifiedName} must implement MviSideEffect"
                ))
            }
        }

        return errors
    }
}

/**
 * Represents information about a class involved in MVI code generation.
 * This abstraction allows us to work with class information in a platform-independent way.
 */
@Serializable
data class ClassInfo(
    /**
     * Simple name of the class (without package).
     * Example: "UserScreenState"
     */
    val simpleName: String,

    /**
     * Fully qualified name including package.
     * Example: "com.example.features.user.UserScreenState"
     */
    val qualifiedName: String,

    /**
     * Package name where the class is located.
     * Example: "com.example.features.user"
     */
    val packageName: String,

    /**
     * List of properties defined in the class.
     * For data classes, this includes all constructor parameters.
     */
    val properties: List<PropertyInfo>,

    /**
     * List of interfaces that this class implements.
     * Used for validation (ensuring state implements MviState, etc.)
     */
    val interfaces: List<String>,

    /**
     * Type parameters if the class is generic.
     * Example: For "MyState<T, R>", this would contain information about T and R.
     */
    val typeParameters: List<TypeParameterInfo>,

    /**
     * Whether this is a data class.
     * Data classes receive special treatment in code generation.
     */
    val isDataClass: Boolean,

    /**
     * Whether this is a sealed class.
     * Sealed classes (like Intent and SideEffect) are handled differently.
     */
    val isSealedClass: Boolean
) {

    /**
     * Checks if this class implements a specific interface.
     * This is used for validation during code generation.
     */
    fun implementsInterface(interfaceName: String): Boolean {
        return interfaces.any { it.contains(interfaceName) }
    }

    /**
     * Gets all sealed subclasses if this is a sealed class.
     * This is particularly useful for Intent and SideEffect classes.
     */
    fun getSealedSubclasses(): List<ClassInfo> {
        // This would be populated by the processor when analyzing sealed classes
        // For now, return empty list - processor will populate this
        return emptyList()
    }
}

/**
 * Represents information about a property within a class.
 * This is used to understand the structure of state classes and generate appropriate code.
 */
@Serializable
data class PropertyInfo(
    /**
     * Name of the property.
     * Example: "isLoading"
     */
    val name: String,

    /**
     * Kotlin type of the property.
     * Example: "Boolean", "List<User>", "String?"
     */
    val type: String,

    /**
     * Whether the property is nullable.
     */
    val isNullable: Boolean,

    /**
     * Whether the property is mutable (var vs val).
     */
    val isMutable: Boolean,

    /**
     * Default value if any.
     * For data class constructor parameters with default values.
     */
    val defaultValue: String?,

    /**
     * Whether this property represents a collection.
     * Used for generating collection-specific helper methods.
     */
    val isCollection: Boolean
)

/**
 * Represents information about type parameters for generic classes.
 */
@Serializable
data class TypeParameterInfo(
    /**
     * Name of the type parameter.
     * Example: "T" in "MyClass<T>"
     */
    val name: String,

    /**
     * Upper bounds for the type parameter.
     * Example: ["Comparable<T>", "Serializable"]
     */
    val bounds: List<String>,

    /**
     * Whether the type parameter is covariant (out).
     */
    val isCovariant: Boolean,

    /**
     * Whether the type parameter is contravariant (in).
     */
    val isContravariant: Boolean
)

/**
 * Captures annotation information in a serializable format.
 * This allows us to pass annotation data from the processor to generators.
 */
@Serializable
data class AnnotationInfo(
    /**
     * Whether to generate the MVI container.
     */
    val generateContainer: Boolean,

    /**
     * Whether to generate ViewModel wrappers.
     */
    val generateViewModel: Boolean,

    /**
     * Whether to generate test suites.
     */
    val generateTesting: Boolean,

    /**
     * Whether to enable debug logging.
     */
    val enableLogging: Boolean,

    /**
     * Target platforms for generation.
     */
    val targetPlatforms: List<TargetPlatform>,

    /**
     * Dependency injection framework to use.
     */
    val dependencyInjection: DependencyInjection,

    /**
     * Custom configuration options.
     */
    val config: ConfigInfo
)

/**
 * Configuration information extracted from @FlowKitConfig.
 */
@Serializable
data class ConfigInfo(
    val containerName: String,
    val viewModelName: String,
    val packageName: String,
    val composeIntegration: Boolean,
    val coroutineSafe: Boolean
)

/**
 * Complete configuration for code generation process.
 * This includes both user-specified and derived configuration.
 */
@Serializable
data class GenerationConfig(
    /**
     * Name for the generated MVI container class.
     * Derived from state class name if not explicitly specified.
     */
    val containerClassName: String,

    /**
     * Name for the generated ViewModel class.
     * Derived from state class name if not explicitly specified.
     */
    val viewModelClassName: String,

    /**
     * Name for the generated Reducer class.
     * Derived from state class name if not explicitly specified.
     */
    val reducerClassName: String,

    /**
     * Package name for generated files.
     * Uses source class package if not explicitly specified.
     */
    val outputPackage: String,

    /**
     * Whether to generate Compose integration code.
     */
    val generateComposeIntegration: Boolean,

    /**
     * Whether to make generated code coroutine-safe.
     */
    val generateCoroutineSafeCode: Boolean,

    /**
     * Additional imports needed for generated code.
     */
    val requiredImports: List<String>,

    /**
     * Custom middleware classes to integrate.
     */
    val middleware: List<String>
)

/**
 * Platform-specific configuration options.
 * Different platforms may require different generation strategies.
 */
@Serializable
data class PlatformConfig(
    /**
     * Target platforms for this generation.
     */
    val targets: List<TargetPlatform>,

    /**
     * Android-specific configuration.
     */
    val androidConfig: AndroidPlatformConfig?,

    /**
     * iOS-specific configuration.
     */
    val iosConfig: IosPlatformConfig?
)

/**
 * Android-specific generation configuration.
 */
@Serializable
data class AndroidPlatformConfig(
    /**
     * Whether to generate Android ViewModel integration.
     */
    val generateViewModel: Boolean,

    /**
     * Whether to generate Compose integration.
     */
    val generateComposeIntegration: Boolean,

    /**
     * Whether to include Android lifecycle awareness.
     */
    val lifecycleAware: Boolean
)

/**
 * iOS-specific generation configuration.
 */
@Serializable
data class IosPlatformConfig(
    /**
     * Whether to generate memory-optimized implementations.
     */
    val memoryOptimized: Boolean,

    /**
     * Whether to include Swift interop optimizations.
     */
    val swiftInteropOptimized: Boolean
)

/**
 * Dependency injection configuration.
 */
@Serializable
data class DependencyInjectionConfig(
    /**
     * The DI framework to generate code for.
     */
    val framework: DependencyInjection,

    /**
     * Whether DI was auto-detected or explicitly specified.
     */
    val autoDetected: Boolean,

    /**
     * Framework-specific configuration options.
     */
    val frameworkConfig: Map<String, String>
)

/**
 * Validation errors that can occur during metadata processing.
 * These help developers understand and fix issues with their MVI setup.
 */
sealed class ValidationError(val message: String) {
    data class InvalidStateClass(val error: String) : ValidationError(error)
    data class InvalidIntentClass(val error: String) : ValidationError(error)
    data class InvalidSideEffectClass(val error: String) : ValidationError(error)
    data class MissingRequiredInterface(val error: String) : ValidationError(error)
    data class InvalidConfiguration(val error: String) : ValidationError(error)
    data class UnsupportedPlatform(val error: String) : ValidationError(error)
}

/**
 * Builder class for constructing MviMetadata instances.
 * This provides a fluent API for building metadata during annotation processing.
 */
class MviMetadataBuilder {
    private var stateInfo: ClassInfo? = null
    private var intentInfo: ClassInfo? = null
    private var sideEffectInfo: ClassInfo? = null
    private var annotation: AnnotationInfo? = null
    private var generationConfig: GenerationConfig? = null
    private var platformConfig: PlatformConfig? = null
    private var diConfig: DependencyInjectionConfig? = null

    fun stateClass(classInfo: ClassInfo) = apply { this.stateInfo = classInfo }
    fun intentClass(classInfo: ClassInfo?) = apply { this.intentInfo = classInfo }
    fun sideEffectClass(classInfo: ClassInfo?) = apply { this.sideEffectInfo = classInfo }
    fun annotation(info: AnnotationInfo) = apply { this.annotation = info }
    fun generationConfig(config: GenerationConfig) = apply { this.generationConfig = config }
    fun platformConfig(config: PlatformConfig) = apply { this.platformConfig = config }
    fun dependencyInjectionConfig(config: DependencyInjectionConfig) = apply { this.diConfig = config }

    fun build(): MviMetadata {
        requireNotNull(stateInfo) { "State class info is required" }
        requireNotNull(annotation) { "Annotation info is required" }
        requireNotNull(generationConfig) { "Generation config is required" }
        requireNotNull(platformConfig) { "Platform config is required" }
        requireNotNull(diConfig) { "Dependency injection config is required" }

        return MviMetadata(
            stateInfo = stateInfo!!,
            intentInfo = intentInfo,
            sideEffectInfo = sideEffectInfo,
            annotation = annotation!!,
            generationConfig = generationConfig!!,
            platformConfig = platformConfig!!,
            diConfig = diConfig!!
        )
    }
}