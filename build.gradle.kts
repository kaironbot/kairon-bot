import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.20"
    id("com.github.johnrengelman.shadow") version "7.1.2"
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
version = "3.1.0"
java.sourceCompatibility = JavaVersion.VERSION_17

apply(plugin = "com.dipien.semantic-version")

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven { url = uri("https://repo.repsy.io/mvn/testadirapa/kabot") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(group="dev.kord", name="kord-core", version="0.8.0-M17")
    implementation(group="org.jetbrains.kotlinx", name="kotlinx-coroutines-core", version="1.6.4")
    implementation(group="org.jetbrains.kotlinx", name="kotlinx-coroutines-reactor", version="1.6.4")
    implementation(group="org.wagham", name="kabot-db-connector", version="0.4.1")
    implementation(group="com.fasterxml.jackson.module", name="jackson-module-kotlin", version="2.13.4")
    implementation(group="org.slf4j", name="slf4j-api", version = "2.0.5")
    implementation(group="org.slf4j", name="slf4j-simple", version = "2.0.5")
    implementation(group = "com.github.ben-manes.caffeine", name = "caffeine", version = "2.9.1")
    implementation(group="io.github.microutils", name="kotlin-logging-jvm", version="2.0.11")
    implementation(group="org.reflections", name="reflections", version="0.10.2")
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
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register("printLibVersion") {
    println(version)
}
