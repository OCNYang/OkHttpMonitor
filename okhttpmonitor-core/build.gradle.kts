plugins {
    kotlin("jvm")
    `maven-publish`
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.brotli:dec:0.1.2")
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = "okhttpmonitor-core"
            version = project.version.toString()

            pom {
                name.set("OkHttpMonitor")
                description.set("A lightweight OkHttp interceptor for HTTP request monitoring and analytics reporting")
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
