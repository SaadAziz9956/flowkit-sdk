package io.flowkit.samples.todo.presentation

import io.flowkit.core.MviState
import io.flowkit.samples.todo.domain.Todo

data class TodoState(
    val todos: List<Todo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val newTodoTitle: String = "",
    val newTodoDescription: String = ""
) : MviState