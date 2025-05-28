package io.flowkit.core

import kotlin.jvm.JvmInline

sealed interface Result<out E, out T> {

    @JvmInline
    value class Error<out E>(val error: E) : Result<E, Nothing>

    @JvmInline
    value class Success<out T>(val data: T) : Result<Nothing, T>

    data object Loading : Result<Nothing, Nothing>
}

// Extensions for AppResult
val <E, T> Result<E, T>.isError: Boolean get() = this is Result.Error
val <E, T> Result<E, T>.isSuccess: Boolean get() = this is Result.Success
val <E, T> Result<E, T>.isLoading: Boolean get() = this is Result.Loading

fun <E, T> Result<E, T>.errorOrNull(): E? = (this as? Result.Error)?.error
fun <E, T> Result<E, T>.dataOrNull(): T? = (this as? Result.Success)?.data

inline fun <E, T, R> Result<E, T>.fold(
    onFailure: (E) -> R,
    onSuccess: (T) -> R,
    onLoading: () -> R
): R = when (this) {
    is Result.Error -> onFailure(error)
    is Result.Success -> onSuccess(data)
    is Result.Loading -> onLoading()
}

inline fun <E, T, R> Result<E, T>.map(transform: (T) -> R): Result<E, R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error -> Result.Error(error)
    is Result.Loading -> Result.Loading
}

inline fun <E, T> Result<E, T>.onSuccess(action: (T) -> Unit): Result<E, T> {
    if (this is Result.Success) {
        action(data)
    }
    return this
}

inline fun <E, T> Result<E, T>.onFailure(action: (E) -> Unit): Result<E, T> {
    if (this is Result.Error) action(this.error)
    return this
}