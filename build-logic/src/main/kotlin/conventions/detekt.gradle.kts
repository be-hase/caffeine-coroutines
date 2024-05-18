package conventions

plugins {
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    buildUponDefaultConfig = true
    source.setFrom(
        "src/main/java",
        "src/main/kotlin",
        "src/test/java",
        "src/test/kotlin",
        "src/functionalTest/java",
        "src/functionalTest/kotlin",
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt> {
    reports {
        sarif.required.set(true)
    }
}
