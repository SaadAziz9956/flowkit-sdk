package io.flowkit.samples.todo.domain

data class Todo(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean = false,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
)