package edu.illinois.cs.cs125.gradlegrader.reporting

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.mongodb.client.MongoClients
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.XForwardedHeaderSupport
import io.ktor.features.origin
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
    val collection: String = "progress",
    val checkXForwarded: Boolean = false
)

fun main() {
    val configLoader = ObjectMapper(YAMLFactory()).also { it.registerModule(KotlinModule()) }
    val config = try {
        @Suppress("RemoveExplicitTypeArguments")
        configLoader.readValue<ReportLoggingConfig>(File("config.yaml"))
    } catch (e: Exception) {
        System.err.println("Couldn't load config.yaml, using default configuration")
        ReportLoggingConfig()
    }

    val connectionString = System.getenv("MONGO")
        ?: throw RuntimeException("MONGO connection string variable not specified")
    val mongo = MongoClients.create(connectionString)
    val collection = mongo.getDatabase(config.db).getCollection(config.collection)

    val server = embeddedServer(Netty, port = config.port) {
        if (config.checkXForwarded) install(XForwardedHeaderSupport)
        routing {
            post("/") {
                val document = try {
                    Document.parse(call.receiveText())
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON")
                }
                document.append("received",
                    Document("time", System.currentTimeMillis())
                        .append("ip", call.request.origin.remoteHost))
                collection.insertOne(document)
                call.response.status(HttpStatusCode.OK)
            }
        }
    }
    server.start(wait = true)
}
