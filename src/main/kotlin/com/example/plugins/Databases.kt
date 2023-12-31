package com.example.plugins

import com.example.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.config.yaml.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.coroutines.*
import java.sql.*
import java.time.LocalDateTime
import kotlin.random.Random

fun Application.configureDatabases() {
    val dbConnection: Connection = connectToPostgres(embedded = false)
    val taskManager = TaskManager(dbConnection)

    // Launch a coroutine to suspend the "isDatabaseEmpty" function
    val scope = CoroutineScope(Dispatchers.Default)
    val job = scope.launch {
        if (taskManager.isDatabaseEmpty()) {
            try {
                println("\n\n\nThe table is empty, populating with sample data.\n\n\n")
                val currentDateTime = LocalDateTime.now()
                val pastDateTime = currentDateTime.minusDays(1)

                val tasks = mutableListOf<TaskRequest>()
                repeat(4) {
                    val futureDateTime = currentDateTime.plusDays(Random.nextLong(1, 10))
                    val task = TaskRequest(
                        title = "Sample task $it",
                        description = "Description for the sample task $it",
                        dueDate = DateTimeHelper.convertToLocalDateTime(Timestamp.valueOf(futureDateTime)),
                        priorityLevel = PriorityLevel.values().random(),
                        status = TaskStatus.values().random()
                    )
                    tasks.add(task)
                }

                // Adding one task with a past due date
                val pastDueTask = TaskRequest(
                    title = "Past Due Task",
                    description = "Description for Past Due Task",
                    dueDate = DateTimeHelper.convertToLocalDateTime(Timestamp.valueOf(pastDateTime)),
                    priorityLevel = PriorityLevel.values().random(),
                    status = TaskStatus.values().random()
                )
                tasks.add(pastDueTask)

                for (t in tasks) {
                    taskManager.create(t)
                }
                println("\n\n\nSample data inserted successfully!\n\n\n")
            } catch (e: Exception) {
                println("\n\n\nSome error occurred while seeding DB: ${e.message}\n\n\n")
            }
        }
    }
    runBlocking {
        job.join()
    }
    scope.cancel()

    routing {
        post("/tasks") {
            val task = call.receive<TaskRequest>()
            val id = taskManager.create(task)
            call.respond(HttpStatusCode.Created, id)
        }
        get("/tasks/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            try {
                val task = taskManager.read(id)
                call.respond(HttpStatusCode.OK, task)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        get("/tasks") {
            try {
                val tasks = taskManager.readAll()
                call.respond(HttpStatusCode.OK, tasks)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, "No tasks yet")
            }
        }
        put("/tasks/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            val task = call.receive<TaskRequest>()
            taskManager.update(id, task)
            call.respond(HttpStatusCode.OK)
        }
        delete("/tasks/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            taskManager.delete(id)
            call.respond(HttpStatusCode.OK)
        }
    }
}

/**
 * Makes a connection to a Postgres database.
 *
 * In order to connect to your running Postgres process,
 * please specify the following parameters in your configuration file:
 * - postgres.url -- Url of your running database process.
 * - postgres.user -- Username for database connection
 * - postgres.password -- Password for database connection
 *
 * If you don't have a database process running yet, you may need to [download]((https://www.postgresql.org/download/))
 * and install Postgres and follow the instructions [here](https://postgresapp.com/).
 * Then, you would be able to edit your url,  which is usually "jdbc:postgresql://host:port/database", as well as
 * user and password values.
 *
 *
 * @param embedded -- if [true] defaults to an embedded database for tests that runs locally in the same process.
 * In this case you don't have to provide any parameters in configuration file, and you don't have to run a process.
 *
 * @return [Connection] that represent connection to the database. Please, don't forget to close this connection when
 * your application shuts down by calling [Connection.close]
 * */
fun Application.connectToPostgres(embedded: Boolean): Connection {
    Class.forName("org.postgresql.Driver")
    return if (embedded) {
        DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "root", "")
    } else {
        val configs = YamlConfig("postgres.yaml")

        val dbName = configs?.property("services.postgres.environment.POSTGRES_DB")?.getString()
        val dbHost = configs?.property("services.postgres.environment.POSTGRES_HOST")?.getString()
        val dbPort = configs?.property("services.postgres.environment.POSTGRES_PORT")?.getString()
        val url = "jdbc:postgresql://$dbHost:$dbPort/$dbName"

        val user = configs?.property("services.postgres.environment.POSTGRES_USER")?.getString()
        val password = configs?.property("services.postgres.environment.POSTGRES_PASSWORD")?.getString()

        return DriverManager.getConnection(url, user, password)
    }
}
