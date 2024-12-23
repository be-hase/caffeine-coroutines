package conventions

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.dokka")
}

val defaultVersion = "latest-SNAPSHOT"

version = providers.gradleProperty("publishVersion").orNull
    ?: providers.environmentVariable("PUBLISH_VERSION").orNull
        ?: defaultVersion

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(
        project.group.toString(),
        project.name,
        project.version.toString(),
    )
    configure(
        KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaHtml"), sourcesJar = true),
    )
    afterEvaluate {
        pom {
            name = project.name
            description = project.description
            url = "https://github.com/be-hase/caffeine-coroutines"
            licenses {
                license {
                    name = "MIT License"
                    url = "https://opensource.org/license/mit"
                }
            }
            developers {
                developer {
                    id = "be-hase"
                    name = "Ryosuke Hasebe"
                    email = "hsb.1014@gmail.com"
                }
            }
            scm {
                connection.set("scm:git:git://github.com/be-hase/caffeine-coroutines.git")
                developerConnection.set("scm:git:ssh://github.com:be-hase/caffeine-coroutines.git")
                url.set("https://github.com/be-hase/caffeine-coroutines")
            }
        }
    }
}
