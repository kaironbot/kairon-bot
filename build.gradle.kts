import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktorVersion = "2.3.5"

plugins {
    kotlin("jvm") version "1.9.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.dipien:semantic-version-gradle-plugin:1.3.0")
    }
}

group = "org.wagham"
version = "4.1.1"
java {
    sourceCompatibility = JavaVersion.VERSION_19
    targetCompatibility = JavaVersion.VERSION_19
}

apply(plugin = "com.dipien.semantic-version")

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven { url = uri("https://repo.repsy.io/mvn/testadirapa/kabot") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
}

dependencies {
    implementation(project(":kabot-db-connector"))
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-reflect", version = "1.8.10")
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8", version = "1.8.10")
    implementation(group = "io.ktor", name="ktor-server-core-jvm", version = ktorVersion)
    implementation(group = "io.ktor", name="ktor-server-content-negotiation-jvm", version = ktorVersion)
    implementation(group = "io.ktor", name="ktor-server-cio-jvm", version = ktorVersion)
    implementation(group = "io.ktor", name="ktor-serialization-kotlinx-json", version = ktorVersion)
    implementation(group = "io.ktor", name = "ktor-server-status-pages", version = ktorVersion)
    implementation(group = "dev.inmo", name = "krontab", version="2.2.1")
    implementation(group = "org.litote.kmongo", name = "kmongo-coroutine", version = "4.8.0")
    implementation(group = "dev.kord", name = "kord-core", version = "0.13.1")
    implementation(group = "org.jetbrains.kotlinx", name="kotlinx-coroutines-core", version = "1.6.4")
    implementation(group = "org.jetbrains.kotlinx", name="kotlinx-coroutines-reactor", version = "1.6.4")
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version = "1.5.1")
    implementation(group = "com.github.h0tk3y.betterParse", name = "better-parse", version = "0.4.4")
    implementation(group = "com.fasterxml.jackson.module", name="jackson-module-kotlin", version = "2.13.4")
    implementation(group = "org.slf4j", name = "slf4j-api", version = "2.0.5")
    implementation(group = "org.slf4j", name="slf4j-simple", version = "2.0.5")
    implementation(group = "com.github.ben-manes.caffeine", name = "caffeine", version = "2.9.1")
    implementation(group = "io.github.microutils", name="kotlin-logging-jvm", version="2.0.11")
    implementation(group = "org.reflections", name="reflections", version="0.10.2")
    implementation(group = "com.google.api-client", name = "google-api-client", version="2.0.0")
    implementation(group = "com.google.oauth-client", name = "google-oauth-client-jetty", version="1.34.1")
    implementation(group = "com.google.apis", name = "google-api-services-sheets", version="v4-rev20220927-2.0.0")
    implementation(group = "com.google.auth", name = "google-auth-library-oauth2-http", version ="1.3.0")
    implementation(group = "org.apache.commons", name="commons-rng-simple", version="1.5")
    implementation(group = "org.apache.commons", name="commons-rng-sampling", version="1.5")

    testImplementation(group="org.junit.jupiter", name="junit-jupiter", version="5.4.2")
    testImplementation(group="io.kotest", name="kotest-assertions-core-jvm", version="5.5.3")
    testImplementation(group="io.kotest", name="kotest-framework-engine-jvm", version="5.5.3")
    testImplementation(group = "io.kotest.extensions", name = "kotest-extensions-spring", version = "1.1.2")

}

application {
    mainClass.set("org.wagham.BotKt")
}

tasks.withType<ShadowJar> {
    archiveFileName.set("application.jar")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "19"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register("printLibVersion") {
    println(version)
}
