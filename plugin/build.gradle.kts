plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    id("com.github.johnrengelman.shadow")
    id("org.jmailen.kotlinter")
}

group = "com.github.cs124-illinois"
version = "2023.2.0"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.8.10")
    implementation(gradleApi())
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.4.0.202211300538-r")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.14.2")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.22.0")
}
gradlePlugin {
    plugins {
        create("plugin") {
            id = "com.github.cs124-illinois.gradlegrader"
            implementationClass = "edu.illinois.cs.cs125.gradlegrader.plugin.GradleGraderPlugin"
        }
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}
publishing {
    publications {
        create<MavenPublication>("gradlegrader") {
            artifactId = "gradlegrader"
            from(components["java"])
        }
    }
}