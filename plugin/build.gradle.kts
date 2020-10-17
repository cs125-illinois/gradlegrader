plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    id("com.github.johnrengelman.shadow")
    id("org.jmailen.kotlinter")
}

group = "com.github.cs125-illinois"
version = "2020.10.1"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(gradleApi())
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
    implementation("org.apache.commons:commons-text:1.9")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.9.0.202009080501-r")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.11.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.3")
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
