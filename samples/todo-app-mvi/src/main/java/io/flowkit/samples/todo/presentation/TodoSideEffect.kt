package io.flowkit.samples.todo.presentation

import io.flowkit.core.MviSideEffect

sealed class TodoSideEffect : MviSideEffect {
    data class ShowToast(val message: String) : TodoSideEffect()
    data object ScrollToTop : TodoSideEffect()
    data object ClearInputFocus : TodoSideEffect()
    data class ShowError(val error: String) : TodoSideEffect()
}