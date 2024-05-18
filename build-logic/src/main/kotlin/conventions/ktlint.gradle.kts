package conventions

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = the<LibrariesForLibs>()

ktlint {
    version = libs.versions.ktlint.get()
    verbose.set(true)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.SARIF)
    }
}
