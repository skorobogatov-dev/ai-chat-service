plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

group = "dev.skorobogatov.mcp.task"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Ktor Server
    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-netty:3.0.3")
    implementation("io.ktor:ktor-server-websockets:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")

    // MCP Kotlin SDK (Model Context Protocol)
    implementation("io.modelcontextprotocol:kotlin-sdk:0.6.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

application {
    mainClass.set("dev.skorobogatov.mcp.task.TaskMCPServerKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
