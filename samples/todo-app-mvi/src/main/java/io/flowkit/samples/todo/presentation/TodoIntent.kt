package io.flowkit.samples.todo.presentation

import io.flowkit.core.MviIntent

sealed class TodoIntent : MviIntent {
    data object LoadTodos : TodoIntent()
    data class AddTodo(val title: String, val description: String) : TodoIntent()
    data class ToggleTodo(val todoId: String) : TodoIntent()
    data class DeleteTodo(val todoId: String) : TodoIntent()
    data class UpdateNewTodoTitle(val title: String) : TodoIntent()
    data class UpdateNewTodoDescription(val description: String) : TodoIntent()
    data object ClearForm : TodoIntent()
}