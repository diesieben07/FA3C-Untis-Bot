import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktorVersion = "1.1.1"

plugins {
    java
    kotlin("jvm") version "1.3.11"
    application
}

group = "de.takeweiland.untisbot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://dl.bintray.com/kotlin/kotlinx/")
}

dependencies {
    compile(kotlin("stdlib-jdk8"))

    compile("io.ktor:ktor-client-core-jvm:$ktorVersion")
    compile("io.ktor:ktor-client-apache:$ktorVersion")
    compile("io.ktor:ktor-client-logging-jvm:$ktorVersion")
    compile("io.ktor:ktor-client-json-jvm:$ktorVersion")
    compile("io.ktor:ktor-client-jackson:$ktorVersion")

    compile("org.threeten:threeten-extra:1.4")

    compile("org.telegram:telegrambots:4.1")
    compile("org.telegram:telegrambotsextensions:4.1")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClassName = "de.takeweiland.untisbot.MainKt"
}