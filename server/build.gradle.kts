import java.io.File
import java.io.StringWriter
import java.util.Properties

group = "edu.illinois.cs.cs124"
version = "2023.2.0"

plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow")
    id("org.jmailen.kotlinter")
    id("com.google.devtools.ksp")
}
dependencies {
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.14.0")

    implementation("io.ktor:ktor-server-netty:2.2.3")
    implementation("org.mongodb:mongodb-driver:3.12.12")
    implementation("com.squareup.moshi:moshi-kotlin:1.14.0")
    implementation("com.github.cs125-illinois:ktor-moshi:2022.9.0")
    implementation("ch.qos.logback:logback-classic:1.4.5")
    implementation("com.uchuhimo:konf-core:1.1.2")
    implementation("com.uchuhimo:konf-yaml:1.1.2")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("io.ktor:ktor-server-cors:2.2.3")
    implementation("io.ktor:ktor-server-forwarded-header:2.2.3")
    implementation("io.ktor:ktor-server-content-negotiation:2.2.3")
}
application {
    mainClass.set("edu.illinois.cs.cs125.gradlegrader.server.MainKt")
}
/*
docker {
    name = "cs125/gradlegrader"
    files(tasks["shadowJar"].outputs)
    @Suppress("DEPRECATION")
    tags("latest")
}
 */
task("createProperties") {
    doLast {
        val properties = Properties().also {
            it["version"] = project.version.toString()
        }
        File(projectDir, "src/main/resources/edu.illinois.cs.cs125.gradlegrader.server.version")
            .printWriter().use { printWriter ->
                printWriter.print(
                    StringWriter().also { properties.store(it, null) }.buffer.toString()
                        .lines().drop(1).joinToString(separator = "\n").trim()
                )
            }
    }
}
tasks.processResources {
    dependsOn("createProperties")
}