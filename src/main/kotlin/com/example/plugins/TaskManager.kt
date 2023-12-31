package com.example.plugins

import com.example.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.Statement

class TaskManager(private val connection: Connection) {
    companion object {
        private const val CREATE_TABLE_TASKS =
            """CREATE TABLE IF NOT EXISTS TASKS (
                ID SERIAL PRIMARY KEY, 
                TITLE VARCHAR(255), 
                DESCRIPTION VARCHAR(255),
                DUE_DATE TIMESTAMP,
                PRIORITY_LEVEL VARCHAR(255),
                STATUS VARCHAR(255)
            );"""
        private const val SELECT_TASK_BY_ID = "SELECT * FROM TASKS WHERE ID = ?"
        private const val SELECT_ALL_TASKS = "SELECT * FROM TASKS"
        private const val INSERT_TASK =
            "INSERT INTO TASKS (TITLE, DESCRIPTION, DUE_DATE, PRIORITY_LEVEL, STATUS) VALUES (?, ?, ?, ?, ?)"
        private const val UPDATE_TASK =
            "UPDATE TASKS SET TITLE = ?, DESCRIPTION = ?, DUE_DATE = ?, PRIORITY_LEVEL = ?, STATUS = ? WHERE ID = ?"
        private const val DELETE_TASK = "DELETE FROM TASKS WHERE ID = ?"
        // TODO: add more db methods
    }

    init {
        val statement = connection.createStatement()
        statement.executeUpdate(CREATE_TABLE_TASKS)
    }

    suspend fun create(task: TaskRequest): Int = withContext(Dispatchers.IO) {
        val statement = connection.prepareStatement(INSERT_TASK, Statement.RETURN_GENERATED_KEYS)
        statement.setString(1, task.title)
        statement.setString(2, task.description)
        statement.setTimestamp(3, DateTimeHelper.convertToSqlTimestamp(task.dueDate))
        statement.setString(4, task.priorityLevel.toString())
        statement.setString(5, task.status.toString())
        statement.executeUpdate()

        val generatedKeys = statement.generatedKeys
        if (generatedKeys.next()) {
            return@withContext generatedKeys.getInt(1)
        } else {
            throw Exception("Unable to retrieve the id of the newly inserted task")
        }
    }

    suspend fun read(id: Int): Task = withContext(Dispatchers.IO) {
        val statement = connection.prepareStatement(SELECT_TASK_BY_ID)
        statement.setInt(1, id)
        val resultSet = statement.executeQuery()

        if (resultSet.next()) {
            val taskId = resultSet.getInt("ID")
            val title = resultSet.getString("TITLE")
            val description = resultSet.getString("DESCRIPTION")
            val dueDate = resultSet.getTimestamp("DUE_DATE")
            val priorityLevel = resultSet.getString("PRIORITY_LEVEL")
            val status = resultSet.getString("STATUS")
            return@withContext Task(
                taskId,
                title,
                description,
                DateTimeHelper.convertToLocalDateTime(dueDate),
                PriorityLevel.fromString(priorityLevel),
                TaskStatus.fromString(status)
            )
        } else {
            throw Exception("Record not found")
        }
    }

//    suspend fun isDatabaseEmpty(): Boolean = withContext(Dispatchers.IO) {
//        val statement = connection.prepareStatement(SELECT_ALL_TASKS)
//        val resultSet = statement.executeQuery()
//        return@withContext !resultSet.next()
//    }

    fun isDatabaseEmpty(): Boolean {
        val statement = connection.prepareStatement(SELECT_ALL_TASKS)
        val resultSet = statement.executeQuery()
        return !resultSet.next()
    }

    suspend fun readAll(): List<Task> = withContext(Dispatchers.IO) {
        val statement = connection.prepareStatement(SELECT_ALL_TASKS)
        val resultSet = statement.executeQuery()

        val retrievedTasks = mutableListOf<Task>()
        while (resultSet.next()) {
            val taskId = resultSet.getInt("ID")
            val title = resultSet.getString("TITLE")
            val description = resultSet.getString("DESCRIPTION")
            val dueDate = resultSet.getTimestamp("DUE_DATE")
            val priorityLevel = resultSet.getString("PRIORITY_LEVEL")
            val status = resultSet.getString("STATUS")

            retrievedTasks.add(
                Task(
                    taskId,
                    title,
                    description,
                    DateTimeHelper.convertToLocalDateTime(dueDate),
                    PriorityLevel.fromString(priorityLevel),
                    TaskStatus.fromString(status)
                )
            )
        }
        if (retrievedTasks.isEmpty()) {
            throw Exception("No records found")
        } else {
            return@withContext retrievedTasks
        }
    }

    suspend fun update(id: Int, task: TaskRequest) = withContext(Dispatchers.IO) {
        val statement = connection.prepareStatement(UPDATE_TASK)
        statement.setString(1, task.title)
        statement.setString(2, task.description)
        statement.setTimestamp(3, DateTimeHelper.convertToSqlTimestamp(task.dueDate))
        statement.setString(4, task.priorityLevel.toString())
        statement.setString(5, task.status.toString())
        statement.setInt(6, id)
        statement.executeUpdate()
    }

    suspend fun delete(id: Int) = withContext(Dispatchers.IO) {
        val statement = connection.prepareStatement(DELETE_TASK)
        statement.setInt(1, id)
        statement.executeUpdate()
    }
}
