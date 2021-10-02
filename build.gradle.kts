import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "1.5.31"
    kotlin("jvm") version kotlinVersion apply false
    kotlin("kapt") version kotlinVersion apply false
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("org.jmailen.kotlinter") version "3.6.0" apply false
    id("com.github.ben-manes.versions") version "0.39.0"
}
allprojects {
    repositories {
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
    tasks.withType<KotlinCompile> {
        val javaVersion = JavaVersion.VERSION_1_8.toString()
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        kotlinOptions {
            jvmTarget = javaVersion
        }
    }
}
subprojects {
    tasks.withType<Test> {
        enableAssertions = true
    }
}
tasks.dependencyUpdates {
    resolutionStrategy {
        componentSelection {
            all {
                if (listOf("alpha", "beta", "rc", "cr", "m", "preview", "b", "ea", "eap", "pr").any { qualifier ->
                    candidate.version.matches(Regex("(?i).*[.-]$qualifier[.\\d-+]*"))
                }) {
                    reject("Release candidate")
                }
            }
        }
    }
    gradleReleaseChannel = "current"
}
