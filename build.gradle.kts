plugins {
    base
}

val releaseVersion = providers.gradleProperty("releaseVersion").orElse("0.0.0-SNAPSHOT")

allprojects {
    group = "com.larseckart"
    version = releaseVersion.get()

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }

        tasks.withType<JavaCompile> {
            options.release.set(17)
        }

        tasks.withType<Test> {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
                showStandardStreams = true
            }
        }
    }
}
