import java.io.File
import java.io.StringWriter
import java.util.Properties

group = "edu.illinois.cs.cs125"
version = "2020.2.1"

plugins {
    kotlin("jvm")
    kotlin("kapt")
    application
    id("com.github.johnrengelman.shadow")
    id("org.jmailen.kotlinter")
    id("com.palantir.docker") version "0.22.1"
}
dependencies {
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.9.2")

    val ktorVersion = "1.3.1"
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("org.mongodb:mongodb-driver:3.12.1")
    implementation("com.squareup.moshi:moshi-kotlin-codegen:1.9.2")
    implementation("com.github.cs125-illinois:ktor-moshi:1.0.3")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("com.uchuhimo:konf-core:0.22.1")
    implementation("com.uchuhimo:konf-yaml:0.22.1")
    implementation("io.github.microutils:kotlin-logging:1.7.8")

    val kotlintestVersion = "3.4.2"
    testImplementation("io.kotlintest:kotlintest-runner-junit5:$kotlintestVersion")
    testImplementation("io.kotlintest:kotlintest-assertions-ktor:$kotlintestVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}
val mainClass = "edu.illinois.cs.cs125.gradlegrader.server.MainKt"
application {
    mainClassName = mainClass
}
docker {
    name = "cs125/gradlegrader"
    tag("latest", "cs125/gradlegrader:latest")
    tag(version.toString(), "cs125/gradlegrader:$version")
    files(tasks["shadowJar"].outputs)
}
tasks.test {
    useJUnitPlatform()
    systemProperties["logback.configurationFile"] = File(projectDir, "src/test/resources/logback-test.xml").absolutePath
    environment("MONGO", "mongodb://localhost:27017/testing")
}
tasks.jar {
    manifest {
        attributes["Main-Class"] = mainClass
    }
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
