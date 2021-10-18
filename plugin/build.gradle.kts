import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    id("com.github.johnrengelman.shadow")
    id("org.jmailen.kotlinter")
}

group = "com.github.cs125-illinois"
version = "2021.10.6"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.31")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.31")
    implementation(gradleApi())
    implementation("com.google.code.gson:gson:2.8.8")
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
    implementation("org.apache.commons:commons-text:1.9")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.13.0.202109080827-r")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.0")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.18.1")
}
gradlePlugin {
    plugins {
        create("plugin") {
            id = "com.github.cs125-illinois.gradlegrader"
            implementationClass = "edu.illinois.cs.cs125.gradlegrader.plugin.GradleGraderPlugin"
        }
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
publishing {
    publications {
        create<MavenPublication>("gradlegrader") {
            artifactId = "gradlegrader"
            from(components["java"])
        }
    }
}
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        apiVersion = "1.4"
        languageVersion = "1.4"
    }
}
tasks.shadowJar {
    isZip64 = true
}