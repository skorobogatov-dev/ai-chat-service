val ktor_version: String = "3.0.3"
val kotlin_version: String = "2.1.0"
val logback_version: String = "1.4.14"

plugins {
    kotlin("jvm") version "2.1.0"
    id("io.ktor.plugin") version "3.0.3"
    kotlin("plugin.serialization") version "2.1.0"
    application
}

group = "dev.skorobogatov"
version = "0.0.1"

application {
    mainClass.set("dev.skorobogatov.mcp.weather.WeatherMCPServerKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server (для WebSocket)
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktor_version")

    // Ktor Client (для запросов к погодным API)
    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-cio-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktor_version")

    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // MCP SDK (Model Context Protocol)
    implementation("io.modelcontextprotocol:kotlin-sdk:0.6.0")
}
