import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    base
    id("com.diffplug.spotless") version "8.10.4" apply false
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
        apply(plugin = "com.diffplug.spotless")

        extensions.configure<SpotlessExtension> {
            java {
                googleJavaFormat("1.36.1")
            }
        }

        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            dependsOn("spotlessApply")
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
