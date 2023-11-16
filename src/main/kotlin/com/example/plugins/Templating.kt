package com.example.plugins

import com.example.models.DateTimeHelper
import com.example.models.PriorityLevel
import com.example.models.TaskRequest
import com.example.models.TaskStatus
import freemarker.cache.ClassTemplateLoader
import io.ktor.server.application.*
import io.ktor.server.freemarker.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import java.sql.Connection

fun Application.configureTemplating() {
    val dbConnection: Connection = connectToPostgres(embedded = false)
    val taskManager = TaskManager(dbConnection)

    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
    }
    routing {
        get("/") {
            try {
                val tasks = taskManager.readAll()
                call.respond(FreeMarkerContent("index.ftl", mapOf("tasks" to tasks)))
            } catch (e: Exception) {
                // TODO: format properly
                call.respond(FreeMarkerContent("index.ftl", mapOf("tasks" to e.message)))
            }
        }
        get("/todos/create") {
            call.respond(FreeMarkerContent("task_create.ftl", model = null))
        }
        post("/todos") {
            try {
                val formParameters = call.receiveParameters()
                // TODO: check whether the date is not in past
                val createdTask = TaskRequest(
                    formParameters.getOrFail("title"),
                    formParameters.getOrFail("description"),
                    DateTimeHelper.convertToLocalDateTime(formParameters.getOrFail("dueDate")),
                    PriorityLevel.fromString(formParameters.getOrFail("priorityLevel")),
                    TaskStatus.fromString(formParameters.getOrFail("status"))
                )
                taskManager.create(createdTask)
                call.respondRedirect("/")
            } catch (e: Exception) {
                // TODO: format properly
                call.respondRedirect("/")
            }
        }
        get("/todos/{id}") {
            try {
                val id = call.parameters.getOrFail<Int>("id").toInt()
                val task = taskManager.read(id)
                call.respond(FreeMarkerContent("task_detail.ftl", mapOf("task" to task)))
            } catch (e: Exception) {
                // TODO: format properly
                call.respond(FreeMarkerContent("task_detail.ftl", mapOf("task" to "No such task found")))
            }
        }
        post("/todos/{id}/delete") {
            try {
                val id = call.parameters.getOrFail<Int>("id").toInt()
                taskManager.delete(id)
                call.respondRedirect("/")
            } catch (e: Exception) {
                // TODO: format properly
                call.respondRedirect("/")
            }
        }
        get("/todos/{id}/edit") {
            try {
                val id = call.parameters.getOrFail<Int>("id").toInt()
                val task = taskManager.read(id)
                call.respond(FreeMarkerContent("task_edit.ftl", mapOf("task" to task)))
            } catch (e: Exception) {
                // TODO: format properly
                call.respond(FreeMarkerContent("task_edit.ftl", mapOf("task" to "No such task found")))
            }
        }
        post("/todos/{id}/edit") {
            try {
                val id = call.parameters.getOrFail<Int>("id").toInt()
                val formParameters = call.receiveParameters()
                // TODO: check whether the date is not in past
                val updatedTask = TaskRequest(
                    formParameters.getOrFail("title"),
                    formParameters.getOrFail("description"),
                    DateTimeHelper.convertToLocalDateTime(formParameters.getOrFail("dueDate")),
                    PriorityLevel.fromString(formParameters.getOrFail("priorityLevel")),
                    TaskStatus.fromString(formParameters.getOrFail("status"))
                )
                taskManager.update(id, updatedTask)
                call.respondRedirect("/")
            } catch (e: Exception) {
                // TODO: format properly
                call.respondRedirect("/")
            }
        }
    }
}