// flowkit-codegen/src/jvmMain/kotlin/processor/FlowKitSymbolProcessor.kt
// KSP-based symbol processor for cross-platform MVI code generation

package io.flowkit.codegen.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import io.flowkit.codegen.annotations.FlowKitMvi
import io.flowkit.codegen.annotations.FlowKitIntent
import io.flowkit.codegen.annotations.FlowKitSideEffect
import io.flowkit.codegen.annotations.TargetPlatform
import io.flowkit.codegen.annotations.DependencyInjection
import io.flowkit.codegen.generators.*
import io.flowkit.codegen.models.*
import io.flowkit.codegen.utils.*

/**
 * Main KSP symbol processor for FlowKit code generation.
 *
 * This processor scans for @FlowKitMvi annotations and generates complete MVI infrastructure
 * that works across Android and iOS platforms. Unlike traditional annotation processors
 * that only work on JVM, KSP allows us to generate code for all Kotlin targets.
 *
 * The processing flow:
 * 1. Find all classes annotated with @FlowKitMvi
 * 2. Extract metadata about state, intent, and side effect classes
 * 3. Validate the MVI structure
 * 4. Generate platform-appropriate code using KotlinPoet
 * 5. Write generated files to the appropriate source sets
 */
class FlowKitSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    // Code generators for different aspects of MVI infrastructure
    private val containerGenerator = MviContainerGenerator(codeGenerator, logger)
    private val viewModelGenerator = MviViewModelGenerator(codeGenerator, logger)
    private val reducerGenerator = MviReducerGenerator(codeGenerator, logger)
    private val testGenerator = MviTestGenerator(codeGenerator, logger)

    // Utilities for metadata extraction and validation
    private val metadataExtractor = MetadataExtractor(logger)
    private val validator = MviValidator(logger)
    private val platformDetector = PlatformDetector(options)

    companion object {
        // Configuration keys for customizing generation behavior
        private const val OPTION_ENABLE_LOGGING = "flowkit.enableLogging"
        private const val OPTION_GENERATE_TESTS = "flowkit.generateTests"
        private const val OPTION_TARGET_PLATFORMS = "flowkit.targetPlatforms"
        private const val OPTION_PACKAGE_PREFIX = "flowkit.packagePrefix"
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info("🚀 FlowKit: Starting cross-platform MVI code generation")

        // Find all symbols annotated with @FlowKitMvi
        val mviAnnotatedSymbols = resolver
            .getSymbolsWithAnnotation(FlowKitMvi::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        if (mviAnnotatedSymbols.isEmpty()) {
            logger.info("📭 FlowKit: No @FlowKitMvi annotations found")
            return emptyList()
        }

        logger.info("🎯 FlowKit: Found ${mviAnnotatedSymbols.size} state classes to process")

        // Process each annotated state class
        val unprocessedSymbols = mutableListOf<KSAnnotated>()

        for (stateClass in mviAnnotatedSymbols) {
            try {
                if (!stateClass.validate()) {
                    // Symbol isn't ready yet, defer processing
                    unprocessedSymbols.add(stateClass)
                    continue
                }

                processStateClass(stateClass, resolver)

            } catch (exception: Exception) {
                logger.error("❌ FlowKit: Failed to process ${stateClass.simpleName.asString()}: ${exception.message}", stateClass)
                logger.exception(exception)
            }
        }

        logger.info("✅ FlowKit: Code generation completed successfully")
        return unprocessedSymbols
    }

    /**
     * Processes a single state class annotated with @FlowKitMvi.
     * This is the heart of the code generation process.
     */
    private fun processStateClass(stateClass: KSClassDeclaration, resolver: Resolver) {
        val stateClassName = stateClass.simpleName.asString()
        logger.info("🔧 FlowKit: Processing state class: $stateClassName")

        // Step 1: Extract metadata from the state class and related classes
        val metadata = extractMviMetadata(stateClass, resolver)

        // Step 2: Validate the MVI structure
        val validationErrors = validator.validate(metadata)
        if (validationErrors.isNotEmpty()) {
            validationErrors.forEach { error ->
                logger.error("🚨 FlowKit Validation Error: ${error.message}", stateClass)
            }
            return
        }

        // Step 3: Generate code based on the configuration
        generateMviInfrastructure(metadata)

        logger.info("✨ FlowKit: Successfully generated MVI infrastructure for $stateClassName")
    }

    /**
     * Extracts comprehensive metadata from the annotated state class.
     * This includes finding related Intent and SideEffect classes,
     * parsing configuration options, and determining target platforms.
     */
    private fun extractMviMetadata(
        stateClass: KSClassDeclaration,
        resolver: Resolver
    ): MviMetadata {
        logger.info("📋 FlowKit: Extracting metadata for ${stateClass.simpleName.asString()}")

        // Extract the @FlowKitMvi annotation
        val mviAnnotation = stateClass.annotations
            .first { it.shortName.asString() == "FlowKitMvi" }

        // Extract state class information
        val stateInfo = metadataExtractor.extractClassInfo(stateClass)

        // Find related Intent class (by convention or annotation)
        val intentClass = findRelatedClass(stateClass, resolver, "Intent") { declaration ->
            declaration.annotations.any { it.shortName.asString() == "FlowKitIntent" }
        }
        val intentInfo = intentClass?.let { metadataExtractor.extractClassInfo(it) }

        // Find related SideEffect class (by convention or annotation)
        val sideEffectClass = findRelatedClass(stateClass, resolver, "SideEffect") { declaration ->
            declaration.annotations.any { it.shortName.asString() == "FlowKitSideEffect" }
        }
        val sideEffectInfo = sideEffectClass?.let { metadataExtractor.extractClassInfo(it) }

        // Extract annotation configuration
        val annotationInfo = metadataExtractor.extractAnnotationInfo(mviAnnotation)

        // Build generation configuration
        val generationConfig = buildGenerationConfig(stateInfo, annotationInfo)

        // Determine platform configuration
        val platformConfig = platformDetector.detectPlatformConfig(annotationInfo.targetPlatforms)

        // Configure dependency injection
        val diConfig = buildDependencyInjectionConfig(annotationInfo.dependencyInjection, resolver)

        return MviMetadataBuilder()
            .stateClass(stateInfo)
            .intentClass(intentInfo)
            .sideEffectClass(sideEffectInfo)
            .annotation(annotationInfo)
            .generationConfig(generationConfig)
            .platformConfig(platformConfig)
            .dependencyInjectionConfig(diConfig)
            .build()
    }

    /**
     * Finds related Intent or SideEffect classes using naming conventions and annotations.
     * For example, if the state class is "UserScreenState", it looks for:
     * - "UserScreenIntent" or "UserIntent"
     * - "UserScreenSideEffect" or "UserSideEffect"
     */
    private fun findRelatedClass(
        stateClass: KSClassDeclaration,
        resolver: Resolver,
        suffix: String,
        annotationCheck: (KSClassDeclaration) -> Boolean
    ): KSClassDeclaration? {
        val stateClassName = stateClass.simpleName.asString()
        val packageName = stateClass.packageName.asString()

        // Generate possible class names based on conventions
        val possibleNames = listOf(
            stateClassName.replace("State", suffix),           // UserScreenState -> UserScreenIntent
            stateClassName.replace("ScreenState", suffix),     // UserScreenState -> UserIntent
            "${stateClassName.removeSuffix("State")}$suffix"   // UserState -> UserIntent
        ).distinct()

        for (className in possibleNames) {
            val qualifiedName = "$packageName.$className"
            val classDeclaration = resolver.getClassDeclarationByName(qualifiedName)

            if (classDeclaration != null && annotationCheck(classDeclaration)) {
                logger.info("🔗 FlowKit: Found related $suffix class: $className")
                return classDeclaration
            }
        }

        logger.info("🔍 FlowKit: No related $suffix class found for $stateClassName")
        return null
    }

    /**
     * Builds the generation configuration from the state class and annotation info.
     * This includes deriving class names, package names, and other generation options.
     */
    private fun buildGenerationConfig(
        stateInfo: ClassInfo,
        annotationInfo: AnnotationInfo
    ): GenerationConfig {
        val stateClassName = stateInfo.simpleName
        val basePackage = stateInfo.packageName

        // Derive class names from state class name
        val containerClassName = if (annotationInfo.config.containerName.isNotEmpty()) {
            annotationInfo.config.containerName
        } else {
            stateClassName.replace("State", "MviContainer")
        }

        val viewModelClassName = if (annotationInfo.config.viewModelName.isNotEmpty()) {
            annotationInfo.config.viewModelName
        } else {
            stateClassName.replace("State", "ViewModel")
        }

        val reducerClassName = "${stateClassName.replace("State", "Reducer")}"

        // Determine output package
        val outputPackage = if (annotationInfo.config.packageName.isNotEmpty()) {
            annotationInfo.config.packageName
        } else {
            val packagePrefix = options[OPTION_PACKAGE_PREFIX] ?: ""
            if (packagePrefix.isNotEmpty()) "$packagePrefix.$basePackage" else basePackage
        }

        return GenerationConfig(
            containerClassName = containerClassName,
            viewModelClassName = viewModelClassName,
            reducerClassName = reducerClassName,
            outputPackage = outputPackage,
            generateComposeIntegration = annotationInfo.config.composeIntegration,
            generateCoroutineSafeCode = annotationInfo.config.coroutineSafe,
            requiredImports = buildRequiredImports(annotationInfo),
            middleware = emptyList() // TODO: Extract from annotation
        )
    }

    /**
     * Builds the list of imports required for the generated code.
     * This ensures all necessary dependencies are available.
     */
    private fun buildRequiredImports(annotationInfo: AnnotationInfo): List<String> {
        val imports = mutableListOf(
            "kotlinx.coroutines.flow.MutableStateFlow",
            "kotlinx.coroutines.flow.StateFlow",
            "kotlinx.coroutines.flow.asStateFlow",
            "kotlinx.coroutines.flow.MutableSharedFlow",
            "kotlinx.coroutines.flow.Flow",
            "kotlinx.coroutines.flow.asSharedFlow",
            "kotlinx.coroutines.CoroutineScope",
            "io.flowkit.core.MviContainer",
            "io.flowkit.core.MviReducer",
            "io.flowkit.core.ReducerResult"
        )

        // Add platform-specific imports
        if (annotationInfo.targetPlatforms.contains(TargetPlatform.ANDROID)) {
            imports.add("androidx.lifecycle.ViewModel")
            imports.add("androidx.lifecycle.viewModelScope")
        }

        // Add DI framework imports
        when (annotationInfo.dependencyInjection) {
            DependencyInjection.KOIN -> {
                imports.add("org.koin.core.component.KoinComponent")
                imports.add("org.koin.core.component.inject")
            }
            DependencyInjection.HILT -> {
                imports.add("dagger.hilt.android.lifecycle.HiltViewModel")
                imports.add("javax.inject.Inject")
            }
            else -> { /* No additional imports needed */ }
        }

        return imports
    }

    /**
     * Configures dependency injection based on the specified framework and project setup.
     */
    private fun buildDependencyInjectionConfig(
        diFramework: DependencyInjection,
        resolver: Resolver
    ): DependencyInjectionConfig {
        val actualFramework = if (diFramework == DependencyInjection.AUTO_DETECT) {
            detectDependencyInjectionFramework(resolver)
        } else {
            diFramework
        }

        return DependencyInjectionConfig(
            framework = actualFramework,
            autoDetected = diFramework == DependencyInjection.AUTO_DETECT,
            frameworkConfig = emptyMap() // TODO: Add framework-specific config
        )
    }

    /**
     * Auto-detects the dependency injection framework by scanning available dependencies.
     */
    private fun detectDependencyInjectionFramework(resolver: Resolver): DependencyInjection {
        // Try to find DI framework classes in the classpath
        return when {
            resolver.getClassDeclarationByName("org.koin.core.Koin") != null -> {
                logger.info("🔍 FlowKit: Auto-detected Koin dependency injection")
                DependencyInjection.KOIN
            }
            resolver.getClassDeclarationByName("dagger.hilt.android.HiltAndroidApp") != null -> {
                logger.info("🔍 FlowKit: Auto-detected Hilt dependency injection")
                DependencyInjection.HILT
            }
            resolver.getClassDeclarationByName("org.kodein.di.DI") != null -> {
                logger.info("🔍 FlowKit: Auto-detected Kodein dependency injection")
                DependencyInjection.KODEIN
            }
            else -> {
                logger.info("🔍 FlowKit: No DI framework detected, using manual dependency injection")
                DependencyInjection.MANUAL
            }
        }
    }

    /**
     * Generates the complete MVI infrastructure based on the extracted metadata.
     * This includes containers, ViewModels, reducers, and tests.
     */
    private fun generateMviInfrastructure(metadata: MviMetadata) {
        val stateClassName = metadata.stateInfo.simpleName
        logger.info("🏗️ FlowKit: Generating MVI infrastructure for $stateClassName")

        // Generate core MVI container (always generated)
        if (metadata.annotation.generateContainer) {
            logger.info("📦 FlowKit: Generating MVI container")
            containerGenerator.generate(metadata)
        }

        // Generate platform-specific ViewModels
        if (metadata.annotation.generateViewModel) {
            logger.info("🎮 FlowKit: Generating ViewModels")
            viewModelGenerator.generate(metadata)
        }

        // Generate reducer implementation
        logger.info("⚙️ FlowKit: Generating reducer")
        reducerGenerator.generate(metadata)

        // Generate comprehensive test suites
        if (metadata.annotation.generateTesting) {
            logger.info("🧪 FlowKit: Generating test suites")
            testGenerator.generate(metadata)
        }

        logger.info("🎉 FlowKit: Successfully generated all components for $stateClassName")
    }
}

/**
 * Provider factory for creating FlowKitSymbolProcessor instances.
 * This is required by KSP to instantiate our processor.
 */
class FlowKitSymbolProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return FlowKitSymbolProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            options = environment.options
        )
    }
}