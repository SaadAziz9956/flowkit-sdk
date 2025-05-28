package io.flowkit.samples.todo.presentation

import io.flowkit.core.MviReducer
import io.flowkit.core.ReducerResult
import io.flowkit.samples.todo.domain.Todo
import io.flowkit.samples.todo.domain.repository.TodoRepository

class TodoReducer(
    private val repository: TodoRepository
) : MviReducer<TodoState, TodoIntent, TodoSideEffect> {

    override suspend fun reduce(
        currentState: TodoState,
        intent: TodoIntent
    ): ReducerResult<TodoState, TodoSideEffect> {
        return when (intent) {
            is TodoIntent.LoadTodos -> handleLoadTodos(currentState)
            is TodoIntent.AddTodo -> handleAddTodo(currentState, intent)
            is TodoIntent.ToggleTodo -> handleToggleTodo(currentState, intent)
            is TodoIntent.DeleteTodo -> handleDeleteTodo(currentState, intent)
            is TodoIntent.UpdateNewTodoTitle -> handleUpdateNewTodoTitle(currentState, intent)
            is TodoIntent.UpdateNewTodoDescription -> handleUpdateNewTodoDescription(currentState, intent)
            is TodoIntent.ClearForm -> handleClearForm(currentState)
        }
    }

    private suspend fun handleLoadTodos(currentState: TodoState): ReducerResult<TodoState, TodoSideEffect> {
        return try {
            val newState = currentState.copy(isLoading = true, error = null)
            val todos = repository.getAllTodos()

            ReducerResult(
                newState = newState.copy(
                    todos = todos,
                    isLoading = false
                ),
                sideEffects = emptyList()
            )
        } catch (e: Exception) {
            ReducerResult(
                newState = currentState.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load todos"
                ),
                sideEffects = listOf(TodoSideEffect.ShowError("Failed to load todos"))
            )
        }
    }

    private suspend fun handleAddTodo(
        currentState: TodoState,
        intent: TodoIntent.AddTodo
    ): ReducerResult<TodoState, TodoSideEffect> {
        if (intent.title.isBlank()) {
            return ReducerResult(
                newState = currentState,
                sideEffects = listOf(TodoSideEffect.ShowToast("Title cannot be empty"))
            )
        }

        return try {
            val newTodo = Todo(
                id = System.currentTimeMillis().toString(),
                title = intent.title.trim(),
                description = intent.description.trim()
            )

            repository.addTodo(newTodo)
            val updatedTodos = currentState.todos + newTodo

            ReducerResult(
                newState = currentState.copy(
                    todos = updatedTodos,
                    newTodoTitle = "",
                    newTodoDescription = ""
                ),
                sideEffects = listOf(
                    TodoSideEffect.ShowToast("Todo added successfully"),
                    TodoSideEffect.ScrollToTop,
                    TodoSideEffect.ClearInputFocus
                )
            )
        } catch (e: Exception) {
            ReducerResult(
                newState = currentState,
                sideEffects = listOf(TodoSideEffect.ShowError("Failed to add todo"))
            )
        }
    }

    private suspend fun handleToggleTodo(
        currentState: TodoState,
        intent: TodoIntent.ToggleTodo
    ): ReducerResult<TodoState, TodoSideEffect> {
        val todoToUpdate = currentState.todos.find { it.id == intent.todoId }
            ?: return ReducerResult(currentState)

        return try {
            val updatedTodo = todoToUpdate.copy(isCompleted = !todoToUpdate.isCompleted)
            repository.updateTodo(updatedTodo)

            val updatedTodos = currentState.todos.map { todo ->
                if (todo.id == intent.todoId) updatedTodo else todo
            }

            val message = if (updatedTodo.isCompleted) "Todo completed!" else "Todo reopened"

            ReducerResult(
                newState = currentState.copy(todos = updatedTodos),
                sideEffects = listOf(TodoSideEffect.ShowToast(message))
            )
        } catch (e: Exception) {
            ReducerResult(
                newState = currentState,
                sideEffects = listOf(TodoSideEffect.ShowError("Failed to update todo"))
            )
        }
    }

    private suspend fun handleDeleteTodo(
        currentState: TodoState,
        intent: TodoIntent.DeleteTodo
    ): ReducerResult<TodoState, TodoSideEffect> {
        return try {
            repository.deleteTodo(intent.todoId)
            val updatedTodos = currentState.todos.filter { it.id != intent.todoId }

            ReducerResult(
                newState = currentState.copy(todos = updatedTodos),
                sideEffects = listOf(TodoSideEffect.ShowToast("Todo deleted"))
            )
        } catch (e: Exception) {
            ReducerResult(
                newState = currentState,
                sideEffects = listOf(TodoSideEffect.ShowError("Failed to delete todo"))
            )
        }
    }

    private fun handleUpdateNewTodoTitle(
        currentState: TodoState,
        intent: TodoIntent.UpdateNewTodoTitle
    ): ReducerResult<TodoState, TodoSideEffect> {
        return ReducerResult(
            newState = currentState.copy(newTodoTitle = intent.title),
            sideEffects = emptyList()
        )
    }

    private fun handleUpdateNewTodoDescription(
        currentState: TodoState,
        intent: TodoIntent.UpdateNewTodoDescription
    ): ReducerResult<TodoState, TodoSideEffect> {
        return ReducerResult(
            newState = currentState.copy(newTodoDescription = intent.description),
            sideEffects = emptyList()
        )
    }

    private fun handleClearForm(currentState: TodoState): ReducerResult<TodoState, TodoSideEffect> {
        return ReducerResult(
            newState = currentState.copy(
                newTodoTitle = "",
                newTodoDescription = ""
            ),
            sideEffects = listOf(TodoSideEffect.ClearInputFocus)
        )
    }
}