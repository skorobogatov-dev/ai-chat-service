// Root build file for multi-module project
// This file is now a simple wrapper - actual configuration is in module build files

plugins {
    kotlin("jvm") version "2.1.0" apply false
    id("io.ktor.plugin") version "3.0.3" apply false
    kotlin("plugin.serialization") version "2.1.0" apply false
}

group = "dev.skorobogatov"
version = "0.0.1"

allprojects {
    repositories {
        mavenCentral()
    }
}
