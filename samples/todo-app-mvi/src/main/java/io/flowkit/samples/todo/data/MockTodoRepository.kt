package io.flowkit.samples.todo.data

import io.flowkit.samples.todo.domain.Todo
import io.flowkit.samples.todo.domain.repository.TodoRepository

class MockTodoRepository : TodoRepository {
    private val todos = mutableListOf(
        Todo("1", "Learn FlowKit SDK", "Understand the basics of FlowKit", false),
        Todo("2", "Build Todo App", "Create a sample app using MVI pattern", false),
        Todo("3", "Write Tests", "Add unit tests for the application", true)
    )

    override suspend fun getAllTodos(): List<Todo> {
        // Simulate network delay
        kotlinx.coroutines.delay(500)
        return todos.toList()
    }

    override suspend fun addTodo(todo: Todo): Todo {
        kotlinx.coroutines.delay(200)
        todos.add(todo)
        return todo
    }

    override suspend fun updateTodo(todo: Todo): Todo {
        kotlinx.coroutines.delay(200)
        val index = todos.indexOfFirst { it.id == todo.id }
        if (index != -1) {
            todos[index] = todo
        }
        return todo
    }

    override suspend fun deleteTodo(todoId: String) {
        kotlinx.coroutines.delay(200)
        todos.removeAll { it.id == todoId }
    }
}