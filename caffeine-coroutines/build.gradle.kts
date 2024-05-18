plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    id("conventions.functional-test")
}

group = "dev.hsbrysk"
version = "0.0.1"

dependencies {
    api(libs.caffeine)
    api(libs.kotlin.coroutines.core)
    api(libs.kotlin.coroutines.jdk8)

    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.kotlin.coroutines.slf4j)
    testImplementation(libs.slf4j.api)
}
