plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    id("conventions.maven-publish")
}

description = "A simple extension that adds Kotlin Coroutines support to caffeine"

dependencies {
    api(libs.caffeine)
    api(libs.kotlin.coroutines.core)
    api(libs.kotlin.coroutines.jdk8)

    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.kotlin.coroutines.slf4j)
    testImplementation(libs.logback.classic)
}
