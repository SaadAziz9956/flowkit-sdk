package io.flowkit.samples.todo.domain.repository

import io.flowkit.samples.todo.domain.Todo

interface TodoRepository {
    suspend fun getAllTodos(): List<Todo>
    suspend fun addTodo(todo: Todo): Todo
    suspend fun updateTodo(todo: Todo): Todo
    suspend fun deleteTodo(todoId: String)
}