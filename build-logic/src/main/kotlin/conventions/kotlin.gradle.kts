package conventions

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    kotlin("jvm")
    `project-report`
}

java {
    toolchain {
        // caffeine requires 11+
        // https://github.com/ben-manes/caffeine/blob/06a7392b1c1ab474bd54b26575c2b48568ad3005/gradle/plugins/src/main/kotlin/lifecycle/java-library-caffeine-conventions.gradle.kts#L21
        languageVersion = JavaLanguageVersion.of(11)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

kotlin {
    compilerOptions {
        javaParameters = true
        allWarningsAsErrors = true
        freeCompilerArgs = listOf(
            "-Xjsr305=strict",
        )
    }
}

val libs = the<LibrariesForLibs>()

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.assertk)
    testImplementation(libs.mockk.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        // Make sure output from standard out or error is shown in Gradle output.
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
