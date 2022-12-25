import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.7.4"
    id("io.spring.dependency-management") version "1.0.14.RELEASE"
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.spring") version "1.7.20"
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
version = "2.0.0"
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
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(group="org.junit.jupiter", name="junit-jupiter", version="5.4.2")
    testImplementation(group="io.kotest", name="kotest-assertions-core-jvm", version="5.5.3")
    testImplementation(group="io.kotest", name="kotest-framework-engine-jvm", version="5.5.3")
    testImplementation(group = "io.kotest.extensions", name = "kotest-extensions-spring", version = "1.1.2")
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

tasks.bootBuildImage {
    imageName = "testadirapa/wagham-bot:${version.toString().replace("-SNAPSHOT", "")}"
}

tasks.register("printLibVersion") {
    println(version)
}