plugins {
    kotlin("jvm")
    `maven-publish`
}

dependencies {
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("org.brotli:dec:0.1.2")
    implementation("com.squareup.okio:okio:3.9.0")
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = "ktormonitor-core"
            version = project.version.toString()

            pom {
                name.set("KtorMonitor")
                description.set("A lightweight Ktor plugin for HTTP request monitoring and analytics reporting")
                url.set("https://github.com/ocnyang/OkHttpMonitor")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
}
