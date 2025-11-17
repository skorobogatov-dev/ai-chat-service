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
    mainClass.set("dev.skorobogatov.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-config-yaml:$ktor_version")

    // Ktor Client (для запросов к Anthropic API)
    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-cio-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-logging-jvm:$ktor_version")

    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")

    // OpenAPI/Swagger
    implementation("io.ktor:ktor-server-openapi:$ktor_version")
    implementation("io.ktor:ktor-server-swagger:$ktor_version")

    // CORS
    implementation("io.ktor:ktor-server-cors-jvm:$ktor_version")

    // Status pages
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // MCP SDK (Model Context Protocol)
    implementation("io.modelcontextprotocol:kotlin-sdk:0.6.0")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("io.ktor:ktor-client-mock:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("io.mockk:mockk:1.13.8")
}

// Task для запуска Weather MCP сервера
tasks.register<JavaExec>("runWeatherMCP") {
    group = "application"
    description = "Run Weather MCP Server on port 3000"
    mainClass.set("dev.skorobogatov.mcp.weather.WeatherMCPServerKt")
    classpath = sourceSets["main"].runtimeClasspath
}
