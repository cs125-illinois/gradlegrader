import java.io.File
import java.io.StringWriter
import java.util.Properties

group = "edu.illinois.cs.cs125"
version = "2020.10.0"

plugins {
    kotlin("jvm")
    kotlin("kapt")
    application
    id("com.github.johnrengelman.shadow")
    id("org.jmailen.kotlinter")
    id("com.palantir.docker") version "0.25.0"
}
dependencies {
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.11.0")

    val ktorVersion = "1.4.1"
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("org.mongodb:mongodb-driver:3.12.7")
    implementation("com.squareup.moshi:moshi-kotlin-codegen:1.11.0")
    implementation("com.github.cs125-illinois:ktor-moshi:1.0.3")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("com.uchuhimo:konf-core:0.23.0")
    implementation("com.uchuhimo:konf-yaml:0.23.0")
    implementation("io.github.microutils:kotlin-logging:2.0.3")
}
application {
    mainClassName = "edu.illinois.cs.cs125.gradlegrader.server.MainKt"
}
docker {
    name = "cs125/gradlegrader"
    tag("latest", "cs125/gradlegrader:latest")
    tag(version.toString(), "cs125/gradlegrader:$version")
    files(tasks["shadowJar"].outputs)
}
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
