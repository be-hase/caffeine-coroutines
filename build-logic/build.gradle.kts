plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradle.plugin.detekt)
    implementation(libs.gradle.plugin.kotlin)
    implementation(libs.gradle.plugin.ktlint)

    // ref: https://github.com/gradle/gradle/issues/15383
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
