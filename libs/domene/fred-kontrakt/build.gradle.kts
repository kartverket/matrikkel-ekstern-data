plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("maven-publish")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(libs.kotlinxSerialization)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "no.kartverket.ekstern-data"
            artifactId = "fred-kontrakt"
            pom {
                name.set("fred-kontrakt")
                description.set("Message contracts for Fred")
                url.set("https://github.com/kartverket/matrikkel-ekstern-data")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/kartverket/matrikkel-ekstern-data/blob/main/LICENSE")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/kartverket/matrikkel-ekstern-data.git")
                    developerConnection.set("scm:git:ssh://git@github.com/kartverket/matrikkel-ekstern-data.git")
                    url.set("https://github.com/kartverket/matrikkel-ekstern-data")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/kartverket/matrikkel-ekstern-data")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

