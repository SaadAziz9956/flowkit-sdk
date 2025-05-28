package io.flowkit.storage

import io.flowkit.core.Result
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * Storage-specific error types
 */
sealed class StorageError : Exception() {
    data class KeyNotFound(val key: String) : StorageError() {
        override val message: String = "Key '$key' not found in storage"
    }

    data class SerializationError(
        override val message: String,
        override val cause: Throwable? = null
    ) : StorageError()

    data class DeserializationError(
        override val message: String,
        override val cause: Throwable? = null
    ) : StorageError()

    data class PermissionError(override val message: String) : StorageError()

    data class StorageFullError(override val message: String = "Storage is full") : StorageError()

    data class DatabaseError(override val message: String, override val cause: Throwable? = null) :
        StorageError()

    data class UnknownStorageError(
        override val message: String,
        override val cause: Throwable? = null
    ) : StorageError()
}

/**
 * Storage result type using our modern Either pattern
 */
typealias StorageResult<T> = Result<StorageError, T>

/**
 * Storage configuration options
 */
data class StorageConfig(
    val enableEncryption: Boolean = false,
    val compressionEnabled: Boolean = false,
    val expirationTime: Duration? = null,
    val syncAcrossDevices: Boolean = false,
    val maxSize: Long? = null
)

/**
 * Storage operation types
 */
enum class StorageOperation {
    INSERT, UPDATE, DELETE, READ, CLEAR
}

/**
 * Storage change event
 */
@OptIn(ExperimentalTime::class)
data class StorageChangeEvent<T>(
    val key: String,
    val operation: StorageOperation,
    val oldValue: T? = null,
    val newValue: T? = null,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)

/**
 * Base interface for all storage operations
 * Provides reactive, type-safe storage with Flow support
 */
interface ReactiveStorage {

    /**
     * Store a value with the given key
     */
    suspend fun <T> put(
        key: String,
        value: T,
        config: StorageConfig = StorageConfig()
    ): StorageResult<Unit>

    /**
     * Retrieve a value by key
     */
    suspend fun <T> get(key: String): StorageResult<T?>

    /**
     * Remove a value by key
     */
    suspend fun remove(key: String): StorageResult<Unit>

    /**
     * Check if a key exists
     */
    suspend fun contains(key: String): StorageResult<Boolean>

    /**
     * Clear all storage
     */
    suspend fun clear(): StorageResult<Unit>

    /**
     * Get all keys
     */
    suspend fun getAllKeys(): StorageResult<Set<String>>

    /**
     * Get storage size in bytes
     */
    suspend fun getSize(): StorageResult<Long>

    /**
     * Observe changes to a specific key
     */
    fun <T> observe(key: String): Flow<StorageResult<T?>>

    /**
     * Observe all storage changes
     */
    fun <T> observeChanges(): Flow<StorageChangeEvent<T>>
}

/**
 * Key-value storage interface for preferences and settings
 */
interface KeyValueStorage : ReactiveStorage {

    /**
     * Store primitive values with type safety
     */
    suspend fun putString(key: String, value: String): StorageResult<Unit>
    suspend fun putInt(key: String, value: Int): StorageResult<Unit>
    suspend fun putLong(key: String, value: Long): StorageResult<Unit>
    suspend fun putFloat(key: String, value: Float): StorageResult<Unit>
    suspend fun putDouble(key: String, value: Double): StorageResult<Unit>
    suspend fun putBoolean(key: String, value: Boolean): StorageResult<Unit>

    /**
     * Retrieve primitive values with defaults
     */
    suspend fun getString(key: String, defaultValue: String = ""): StorageResult<String>
    suspend fun getInt(key: String, defaultValue: Int = 0): StorageResult<Int>
    suspend fun getLong(key: String, defaultValue: Long = 0L): StorageResult<Long>
    suspend fun getFloat(key: String, defaultValue: Float = 0f): StorageResult<Float>
    suspend fun getDouble(key: String, defaultValue: Double = 0.0): StorageResult<Double>
    suspend fun getBoolean(key: String, defaultValue: Boolean = false): StorageResult<Boolean>

    /**
     * Observe primitive values
     */
    fun observeString(key: String, defaultValue: String = ""): Flow<String>
    fun observeInt(key: String, defaultValue: Int = 0): Flow<Int>
    fun observeLong(key: String, defaultValue: Long = 0L): Flow<Long>
    fun observeFloat(key: String, defaultValue: Float = 0f): Flow<Float>
    fun observeDouble(key: String, defaultValue: Double = 0.0): Flow<Double>
    fun observeBoolean(key: String, defaultValue: Boolean = false): Flow<Boolean>
}

/**
 * Database storage interface for complex data
 */
interface DatabaseStorage {

    /**
     * Execute a query and return results as Flow
     */
    fun <T> query(query: String, mapper: (Map<String, Any?>) -> T): Flow<StorageResult<List<T>>>

    /**
     * Insert data into database
     */
    suspend fun insert(table: String, data: Map<String, Any?>): StorageResult<Long>

    /**
     * Update data in database
     */
    suspend fun update(table: String, data: Map<String, Any?>, where: String): StorageResult<Int>

    /**
     * Delete data from database
     */
    suspend fun delete(table: String, where: String): StorageResult<Int>

    /**
     * Execute raw SQL
     */
    suspend fun execute(sql: String): StorageResult<Unit>

    /**
     * Start a transaction
     */
    suspend fun <T> transaction(block: suspend DatabaseStorage.() -> T): StorageResult<T>
}

/**
 * File storage interface for large data and files
 */
interface FileStorage {

    /**
     * Write data to file
     */
    suspend fun writeFile(path: String, data: ByteArray): StorageResult<Unit>

    /**
     * Read data from file
     */
    suspend fun readFile(path: String): StorageResult<ByteArray>

    /**
     * Check if file exists
     */
    suspend fun fileExists(path: String): StorageResult<Boolean>

    /**
     * Delete file
     */
    suspend fun deleteFile(path: String): StorageResult<Unit>

    /**
     * List files in directory
     */
    suspend fun listFiles(directory: String): StorageResult<List<String>>

    /**
     * Get file size
     */
    suspend fun getFileSize(path: String): StorageResult<Long>

    /**
     * Observe file changes
     */
    fun observeFile(path: String): Flow<StorageResult<ByteArray>>
}

/**
 * Cache storage interface with TTL support
 */
interface CacheStorage : ReactiveStorage {

    /**
     * Put value with TTL
     */
    suspend fun <T> putWithTtl(key: String, value: T, ttl: Duration): StorageResult<Unit>

    /**
     * Get value if not expired
     */
    suspend fun <T> getIfValid(key: String): StorageResult<T?>

    /**
     * Check if key is expired
     */
    suspend fun isExpired(key: String): StorageResult<Boolean>

    /**
     * Clean up expired entries
     */
    suspend fun cleanupExpired(): StorageResult<Int>

    /**
     * Set global TTL for all entries
     */
    suspend fun setGlobalTtl(ttl: Duration): StorageResult<Unit>
}

/**
 * Synchronized storage interface for multi-device sync
 */
interface SyncStorage : ReactiveStorage {

    /**
     * Sync data with remote storage
     */
    suspend fun sync(): StorageResult<Unit>

    /**
     * Force upload local changes
     */
    suspend fun forceUpload(): StorageResult<Unit>

    /**
     * Force download remote changes
     */
    suspend fun forceDownload(): StorageResult<Unit>

    /**
     * Get sync status
     */
    suspend fun getSyncStatus(): StorageResult<SyncStatus>

    /**
     * Observe sync events
     */
    fun observeSyncEvents(): Flow<SyncEvent>
}

/**
 * Sync status information
 */
data class SyncStatus(
    val isOnline: Boolean,
    val lastSyncTime: Long,
    val pendingUploads: Int,
    val pendingDownloads: Int,
    val conflictCount: Int
)

/**
 * Sync events
 */
sealed class SyncEvent {
    data object SyncStarted : SyncEvent()
    data object SyncCompleted : SyncEvent()
    data class SyncFailed(val error: StorageError) : SyncEvent()
    data class ConflictDetected(val key: String, val localValue: Any?, val remoteValue: Any?) :
        SyncEvent()
}