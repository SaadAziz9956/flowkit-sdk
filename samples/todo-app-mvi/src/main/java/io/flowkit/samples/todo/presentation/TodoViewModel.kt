package io.flowkit.samples.todo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.flowkit.core.mviContainer
import io.flowkit.samples.todo.data.MockTodoRepository
import io.flowkit.samples.todo.domain.repository.TodoRepository
import kotlinx.coroutines.launch

/**
 * ViewModel demonstrating FlowKit SDK usage with MVI pattern
 * This shows how clean and simple MVI becomes with FlowKit!
 */
class TodoViewModel(
    private val repository: TodoRepository = MockTodoRepository()
) : ViewModel() {

    // 🚀 FlowKit magic: One-liner MVI container setup!
    private val container = viewModelScope.mviContainer(
        initialState = TodoState(),
        reducer = TodoReducer(repository),
        enableLogging = true // Enable logging for development
    )

    // Expose state and side effects to UI
    val state = container.state
    val sideEffects = container.sideEffects

    init {
        // Load initial data
        handleIntent(TodoIntent.LoadTodos)
    }

    /**
     * Single entry point for all user actions
     * This maintains strict unidirectional data flow
     */
    fun handleIntent(intent: TodoIntent) {
        viewModelScope.launch {
            container.processIntent(intent)
        }
    }

    // Convenience methods for UI (optional - you can use handleIntent directly)
    fun loadTodos() = handleIntent(TodoIntent.LoadTodos)

    fun addTodo(title: String, description: String) =
        handleIntent(TodoIntent.AddTodo(title, description))

    fun toggleTodo(todoId: String) =
        handleIntent(TodoIntent.ToggleTodo(todoId))

    fun deleteTodo(todoId: String) =
        handleIntent(TodoIntent.DeleteTodo(todoId))

    fun updateNewTodoTitle(title: String) =
        handleIntent(TodoIntent.UpdateNewTodoTitle(title))

    fun updateNewTodoDescription(description: String) =
        handleIntent(TodoIntent.UpdateNewTodoDescription(description))

    fun clearForm() = handleIntent(TodoIntent.ClearForm)

    // Get current state synchronously (useful for testing)
    fun currentState() = container.currentState()
}