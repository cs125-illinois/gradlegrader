package edu.illinois.cs.cs125.gradlegrader.reporting

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.mongodb.client.MongoClients
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.bson.Document
import java.io.File

data class ReportLoggingConfig(
    val port: Int = 8181,
    val db: String = "cs125",
    val collection: String = "progress"
)

fun main() {
    val configLoader = ObjectMapper(YAMLFactory()).also { it.registerModule(KotlinModule()) }
    val config = try {
        configLoader.readValue<ReportLoggingConfig>(File("config.yaml"))
    } catch (e: Exception) {
        System.err.println("Couldn't load config.yaml, using default configuration")
        ReportLoggingConfig()
    }

    val mongo = MongoClients.create(System.getenv("MONGO"))
    val collection = mongo.getDatabase(config.db).getCollection(config.collection)

    val server = embeddedServer(Netty, port = config.port) {
        routing {
            post("/") {
                val document = try {
                    Document.parse(call.receiveText())
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON")
                }
                collection.insertOne(document)
                call.response.status(HttpStatusCode.OK)
            }
        }
    }
    server.start(wait = true)
}
