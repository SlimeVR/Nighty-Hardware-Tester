import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.21"
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("org.slf4j:slf4j-simple:1.7.32")
    implementation("com.pi4j:pi4j-ktx:2.4.0")
    implementation("com.pi4j:pi4j-core:2.3.0")
    implementation("com.pi4j:pi4j-plugin-raspberrypi:2.3.0")
    implementation("com.pi4j:pi4j-plugin-pigpio:2.3.0")
    implementation("com.pi4j:pi4j-plugin-linuxfs:2.3.0")
    implementation("com.googlecode.lanterna:lanterna:3.1.1")
    implementation("com.fazecast:jSerialComm:[2.0.0,3.0.0)")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.12.7.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("slimevr")
    archiveClassifier.set("")
    archiveVersion.set("")
}

application {
    mainClass.set("dev.slimevr.testing.Main")
}
