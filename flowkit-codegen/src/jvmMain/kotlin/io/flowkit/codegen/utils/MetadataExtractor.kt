package io.flowkit.codegen.utils

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Variance
import io.flowkit.codegen.annotations.DependencyInjection
import io.flowkit.codegen.annotations.TargetPlatform
import io.flowkit.codegen.models.AndroidPlatformConfig
import io.flowkit.codegen.models.AnnotationInfo
import io.flowkit.codegen.models.ClassInfo
import io.flowkit.codegen.models.ConfigInfo
import io.flowkit.codegen.models.DependencyInjectionConfig
import io.flowkit.codegen.models.IosPlatformConfig
import io.flowkit.codegen.models.MviMetadata
import io.flowkit.codegen.models.PlatformConfig
import io.flowkit.codegen.models.PropertyInfo
import io.flowkit.codegen.models.TypeParameterInfo
import io.flowkit.codegen.models.ValidationError

/**
 * Extracts comprehensive metadata from KSP symbols for MVI code generation.
 *
 * This class is responsible for analyzing annotated classes and converting
 * KSP symbols into our internal metadata model that drives code generation.
 * It handles the complexity of working with KSP's symbol model and provides
 * a clean, platform-independent representation of the source code structure.
 */
class MetadataExtractor(private val logger: KSPLogger) {

    /**
     * Extracts detailed class information from a KSP class declaration.
     * This includes properties, interfaces, type parameters, and other metadata
     * needed for generating MVI infrastructure.
     */
    fun extractClassInfo(classDeclaration: KSClassDeclaration): ClassInfo {
        val simpleName = classDeclaration.simpleName.asString()
        val qualifiedName = classDeclaration.qualifiedName?.asString()
            ?: throw IllegalArgumentException("Class must have a qualified name")
        val packageName = classDeclaration.packageName.asString()

        logger.info("📋 FlowKit: Extracting class info for $simpleName")

        // Extract properties from the class
        val properties = extractProperties(classDeclaration)

        // Extract implemented interfaces
        val interfaces = extractInterfaces(classDeclaration)

        // Extract type parameters for generic classes
        val typeParameters = extractTypeParameters(classDeclaration)

        // Determine class characteristics
        val isDataClass = classDeclaration.modifiers.contains(Modifier.DATA)
        val isSealedClass = classDeclaration.modifiers.contains(Modifier.SEALED)

        return ClassInfo(
            simpleName = simpleName,
            qualifiedName = qualifiedName,
            packageName = packageName,
            properties = properties,
            interfaces = interfaces,
            typeParameters = typeParameters,
            isDataClass = isDataClass,
            isSealedClass = isSealedClass
        )
    }

    /**
     * Extracts property information from a class declaration.
     * This includes constructor parameters for data classes and declared properties.
     */
    private fun extractProperties(classDeclaration: KSClassDeclaration): List<PropertyInfo> {
        val properties = mutableListOf<PropertyInfo>()

        // Extract properties from primary constructor (for data classes)
        classDeclaration.primaryConstructor?.parameters?.forEach { parameter ->
            val propertyInfo = extractPropertyFromParameter(parameter)
            if (propertyInfo != null) {
                properties.add(propertyInfo)
            }
        }

        // Extract declared properties
        classDeclaration.getAllProperties().forEach { property ->
            // Skip if this property was already added from constructor
            if (properties.none { it.name == property.simpleName.asString() }) {
                val propertyInfo = extractPropertyFromDeclaration(property)
                properties.add(propertyInfo)
            }
        }

        logger.info("📝 FlowKit: Extracted ${properties.size} properties from ${classDeclaration.simpleName.asString()}")
        return properties
    }

    /**
     * Extracts property information from a constructor parameter.
     * This is particularly important for data classes where constructor parameters
     * become properties.
     */
    private fun extractPropertyFromParameter(parameter: KSValueParameter): PropertyInfo? {
        val name = parameter.name?.asString() ?: return null
        val type = parameter.type.resolve()

        return PropertyInfo(
            name = name,
            type = formatKotlinType(type),
            isNullable = type.isMarkedNullable,
            isMutable = parameter.isVar,
            defaultValue = extractDefaultValue(parameter),
            isCollection = isCollectionType(type)
        )
    }

    /**
     * Extracts property information from a property declaration.
     */
    private fun extractPropertyFromDeclaration(property: KSPropertyDeclaration): PropertyInfo {
        val type = property.type.resolve()

        return PropertyInfo(
            name = property.simpleName.asString(),
            type = formatKotlinType(type),
            isNullable = type.isMarkedNullable,
            isMutable = property.isMutable,
            defaultValue = null, // Property declarations don't have default values in the same way
            isCollection = isCollectionType(type)
        )
    }

    /**
     * Formats a KSType into a readable Kotlin type string.
     * This handles generics, nullability, and other type features.
     */
    private fun formatKotlinType(type: KSType): String {
        val declaration = type.declaration
        val typeName = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()

        // Handle generic types
        val typeArguments = type.arguments
        val formattedType = if (typeArguments.isNotEmpty()) {
            val argumentsString = typeArguments.joinToString(", ") { argument ->
                when (argument.variance) {
                    Variance.STAR -> "*"
                    else -> formatKotlinType(argument.type?.resolve() ?: return@joinToString "Any")
                }
            }
            "$typeName<$argumentsString>"
        } else {
            typeName
        }

        // Add nullability marker
        return if (type.isMarkedNullable) "$formattedType?" else formattedType
    }

    /**
     * Determines if a type represents a collection.
     * This is used for generating collection-specific helper methods.
     */
    private fun isCollectionType(type: KSType): Boolean {
        val typeName = type.declaration.qualifiedName?.asString() ?: return false

        return typeName.startsWith("kotlin.collections.") ||
                typeName.startsWith("java.util.") ||
                listOf("List", "Set", "Map", "Array", "Collection").any {
                    typeName.endsWith(".$it") || typeName == "kotlin.$it"
                }
    }

    /**
     * Extracts the default value from a constructor parameter.
     * This is tricky with KSP as default values aren't always directly accessible.
     */
    private fun extractDefaultValue(parameter: KSValueParameter): String? {
        // KSP doesn't provide direct access to default values
        // We would need to analyze the source code or use other techniques
        // For now, return null and rely on the developer to maintain consistency
        return if (parameter.hasDefault) "/* default value */" else null
    }

    /**
     * Extracts the list of interfaces implemented by a class.
     * This is used for validation (ensuring state implements MviState, etc.).
     */
    private fun extractInterfaces(classDeclaration: KSClassDeclaration): List<String> {
        val interfaces = mutableListOf<String>()

        // Direct interfaces
        classDeclaration.superTypes.forEach { superTypeRef ->
            val superType = superTypeRef.resolve()
            val declaration = superType.declaration

            if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.INTERFACE) {
                declaration.qualifiedName?.asString()?.let { interfaces.add(it) }
            }
        }

        // Inherited interfaces (recursive)
        classDeclaration.superTypes.forEach { superTypeRef ->
            val superType = superTypeRef.resolve()
            val declaration = superType.declaration

            if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.CLASS) {
                interfaces.addAll(extractInterfaces(declaration))
            }
        }

        return interfaces.distinct()
    }

    /**
     * Extracts type parameter information for generic classes.
     */
    private fun extractTypeParameters(classDeclaration: KSClassDeclaration): List<TypeParameterInfo> {
        return classDeclaration.typeParameters.map { typeParameter ->
            TypeParameterInfo(
                name = typeParameter.simpleName.asString(),
                bounds = typeParameter.bounds.map { bound ->
                    formatKotlinType(bound.resolve())
                }.toList(),
                isCovariant = typeParameter.variance == Variance.COVARIANT,
                isContravariant = typeParameter.variance == Variance.CONTRAVARIANT
            )
        }
    }

    /**
     * Extracts annotation information from the @FlowKitMvi annotation.
     * This parses all the configuration options specified by the developer.
     */
    fun extractAnnotationInfo(annotation: KSAnnotation): AnnotationInfo {
        logger.info("🔍 FlowKit: Extracting annotation configuration")

        // Extract annotation arguments with default values
        val arguments = annotation.arguments.associate {
            it.name?.asString() to it.value
        }

        val generateContainer = arguments["generateContainer"] as? Boolean ?: true
        val generateViewModel = arguments["generateViewModel"] as? Boolean ?: true
        val generateTesting = arguments["generateTesting"] as? Boolean ?: true
        val enableLogging = arguments["enableLogging"] as? Boolean ?: false

        // Extract target platforms
        val targetPlatforms = extractTargetPlatforms(arguments["targetPlatforms"])

        // Extract dependency injection configuration
        val dependencyInjection = extractDependencyInjection(arguments["dependencyInjection"])

        // Extract config annotation
        val config = extractConfigInfo(arguments["config"])

        return AnnotationInfo(
            generateContainer = generateContainer,
            generateViewModel = generateViewModel,
            generateTesting = generateTesting,
            enableLogging = enableLogging,
            targetPlatforms = targetPlatforms,
            dependencyInjection = dependencyInjection,
            config = config
        )
    }

    /**
     * Extracts target platform configuration from annotation arguments.
     */
    private fun extractTargetPlatforms(platformsValue: Any?): List<TargetPlatform> {
        return when (platformsValue) {
            is List<*> -> {
                platformsValue.mapNotNull { value ->
                    when (value) {
                        is KSType -> {
                            val enumName = value.declaration.simpleName.asString()
                            TargetPlatform.values().find { it.name == enumName }
                        }

                        else -> null
                    }
                }
            }

            else -> listOf(TargetPlatform.ALL)
        }
    }

    /**
     * Extracts dependency injection framework configuration.
     */
    private fun extractDependencyInjection(diValue: Any?): DependencyInjection {
        return when (diValue) {
            is KSType -> {
                val enumName = diValue.declaration.simpleName.asString()
                DependencyInjection.values().find { it.name == enumName }
                    ?: DependencyInjection.AUTO_DETECT
            }

            else -> DependencyInjection.AUTO_DETECT
        }
    }

    /**
     * Extracts configuration information from the nested @FlowKitConfig annotation.
     */
    private fun extractConfigInfo(configValue: Any?): ConfigInfo {
        // For now, return default config
        // In a full implementation, we would parse the nested annotation
        return ConfigInfo(
            containerName = "",
            viewModelName = "",
            packageName = "",
            composeIntegration = true,
            coroutineSafe = true
        )
    }
}


/**
 * Validates MVI metadata to ensure it represents a valid MVI structure.
 * This catches common mistakes early and provides helpful error messages.
 */
class MviValidator(private val logger: KSPLogger) {

    /**
     * Validates the extracted MVI metadata and returns any validation errors found.
     */
    fun validate(metadata: MviMetadata): List<ValidationError> {
        logger.info("✅ FlowKit: Validating MVI structure for ${metadata.stateInfo.simpleName}")

        val errors = mutableListOf<ValidationError>()

        // Validate state class
        errors.addAll(validateStateClass(metadata.stateInfo))

        // Validate intent class if present
        metadata.intentInfo?.let { intentInfo ->
            errors.addAll(validateIntentClass(intentInfo))
        }

        // Validate side effect class if present
        metadata.sideEffectInfo?.let { sideEffectInfo ->
            errors.addAll(validateSideEffectClass(sideEffectInfo))
        }

        // Validate platform configuration
        errors.addAll(validatePlatformConfiguration(metadata.platformConfig))

        // Validate dependency injection configuration
        errors.addAll(validateDependencyInjectionConfiguration(metadata.diConfig))

        if (errors.isEmpty()) {
            logger.info("✅ FlowKit: Validation passed for ${metadata.stateInfo.simpleName}")
        } else {
            logger.error("❌ FlowKit: Found ${errors.size} validation errors")
        }

        return errors
    }

    /**
     * Validates that the state class follows MVI patterns.
     */
    private fun validateStateClass(stateInfo: ClassInfo): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Check if state class implements MviState
        if (!stateInfo.implementsInterface("io.flowkit.core.MviState")) {
            errors.add(
                ValidationError.InvalidStateClass(
                    "State class ${stateInfo.qualifiedName} must implement MviState interface"
                )
            )
        }

        // Recommend data class for immutability
        if (!stateInfo.isDataClass) {
            logger.warn(
                "⚠️ FlowKit: State class ${stateInfo.simpleName} is not a data class. " +
                        "Data classes are recommended for immutable state management."
            )
        }

        // Check for mutable properties
        val mutableProperties = stateInfo.properties.filter { it.isMutable }
        if (mutableProperties.isNotEmpty()) {
            logger.warn(
                "⚠️ FlowKit: State class ${stateInfo.simpleName} has mutable properties: " +
                        "${mutableProperties.map { it.name }}. Consider using immutable properties for better MVI patterns."
            )
        }

        return errors
    }

    /**
     * Validates that the intent class follows MVI patterns.
     */
    private fun validateIntentClass(intentInfo: ClassInfo): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Check if intent class implements MviIntent
        if (!intentInfo.implementsInterface("io.flowkit.core.MviIntent")) {
            errors.add(
                ValidationError.InvalidIntentClass(
                    "Intent class ${intentInfo.qualifiedName} must implement MviIntent interface"
                )
            )
        }

        // Recommend sealed class for intent hierarchy
        if (!intentInfo.isSealedClass) {
            logger.warn(
                "⚠️ FlowKit: Intent class ${intentInfo.simpleName} is not sealed. " +
                        "Sealed classes are recommended for intent hierarchies."
            )
        }

        return errors
    }

    /**
     * Validates that the side effect class follows MVI patterns.
     */
    private fun validateSideEffectClass(sideEffectInfo: ClassInfo): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Check if side effect class implements MviSideEffect
        if (!sideEffectInfo.implementsInterface("io.flowkit.core.MviSideEffect")) {
            errors.add(
                ValidationError.InvalidSideEffectClass(
                    "SideEffect class ${sideEffectInfo.qualifiedName} must implement MviSideEffect interface"
                )
            )
        }

        // Recommend sealed class for side effect hierarchy
        if (!sideEffectInfo.isSealedClass) {
            logger.warn(
                "⚠️ FlowKit: SideEffect class ${sideEffectInfo.simpleName} is not sealed. " +
                        "Sealed classes are recommended for side effect hierarchies."
            )
        }

        return errors
    }

    /**
     * Validates platform configuration for cross-platform compatibility.
     */
    private fun validatePlatformConfiguration(platformConfig: PlatformConfig): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Check for conflicting platform configurations
        if (platformConfig.targets.contains(TargetPlatform.ALL) && platformConfig.targets.size > 1) {
            errors.add(
                ValidationError.InvalidConfiguration(
                    "Cannot specify TargetPlatform.ALL with other specific platforms"
                )
            )
        }

        // Validate Android-specific configuration
        platformConfig.androidConfig?.let { androidConfig ->
            if (!platformConfig.targets.contains(TargetPlatform.ANDROID) &&
                !platformConfig.targets.contains(TargetPlatform.ALL)
            ) {
                errors.add(
                    ValidationError.InvalidConfiguration(
                        "Android configuration specified but ANDROID not in target platforms"
                    )
                )
            }
        }

        // Validate iOS-specific configuration
        platformConfig.iosConfig?.let { iosConfig ->
            if (!platformConfig.targets.contains(TargetPlatform.IOS) &&
                !platformConfig.targets.contains(TargetPlatform.ALL)
            ) {
                errors.add(
                    ValidationError.InvalidConfiguration(
                        "iOS configuration specified but IOS not in target platforms"
                    )
                )
            }
        }

        return errors
    }

    /**
     * Validates dependency injection configuration.
     */
    private fun validateDependencyInjectionConfiguration(diConfig: DependencyInjectionConfig): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Validate Hilt is only used with Android
        if (diConfig.framework == DependencyInjection.HILT) {
            // This validation would need access to platform config
            // For now, just log a warning
            logger.warn(
                "⚠️ FlowKit: Hilt dependency injection is Android-only. " +
                        "Ensure you're only targeting Android platforms."
            )
        }

        return errors
    }
}

/**
 * Detects platform configuration and capabilities based on project setup.
 * This helps auto-configure platform-specific features.
 */
class PlatformDetector(private val options: Map<String, String>) {

    /**
     * Detects platform configuration based on target platforms and project setup.
     */
    fun detectPlatformConfig(targetPlatforms: List<TargetPlatform>): PlatformConfig {
        val actualTargets = if (targetPlatforms.contains(TargetPlatform.ALL)) {
            listOf(TargetPlatform.ANDROID, TargetPlatform.IOS)
        } else {
            targetPlatforms
        }

        return PlatformConfig(
            targets = actualTargets,
            androidConfig = if (actualTargets.contains(TargetPlatform.ANDROID)) {
                detectAndroidConfig()
            } else null,
            iosConfig = if (actualTargets.contains(TargetPlatform.IOS)) {
                detectIosConfig()
            } else null
        )
    }

    /**
     * Detects Android-specific configuration based on available dependencies.
     */
    private fun detectAndroidConfig(): AndroidPlatformConfig {
        return AndroidPlatformConfig(
            generateViewModel = true, // Always generate ViewModel for Android
            generateComposeIntegration = true, // Assume Compose is available
            lifecycleAware = true // Enable lifecycle awareness by default
        )
    }

    /**
     * Detects iOS-specific configuration based on project setup.
     */
    private fun detectIosConfig(): IosPlatformConfig {
        return IosPlatformConfig(
            memoryOptimized = true, // Enable memory optimizations for iOS
            swiftInteropOptimized = true // Optimize for Swift interop
        )
    }
}