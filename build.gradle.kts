import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(connectorLibs.plugins.kotlin.jvm)
    alias(connectorLibs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.semantic.version) apply false
    application
}

group = "org.wagham"
version = "4.1.1"
apply(plugin = "com.dipien.semantic-version")

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(project(":kabot-db-connector"))
    implementation(libs.bundles.apache.rng)
    implementation(libs.bundles.google.api)
    implementation(libs.bundles.ktor.server)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.ktor.serialization.kotlinx)
    implementation(libs.krontab)
    implementation(connectorLibs.kmongo)
    implementation(libs.kord)
    implementation(connectorLibs.kotlinx.coroutines.core)
    implementation(connectorLibs.kotlinx.coroutines.reactor)
    implementation(connectorLibs.kotlinx.serialization.json)
    implementation(libs.better.parse)
    implementation(libs.jackson.kotlin)
    implementation(connectorLibs.slf4j.simple)
    implementation(connectorLibs.slf4j.api)
    implementation(libs.caffeine)
    implementation(libs.kotlin.logging)
    implementation(libs.reflections)

    testImplementation(libs.jupiter)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.framework.engine)
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
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register("printLibVersion") {
    println(version)
}
