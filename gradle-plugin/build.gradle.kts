plugins {
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "2.2.1"
}

group = "com.github.larseckart"

dependencies {
    implementation(project(":analyzer"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    website.set("https://github.com/LarsEckart/object-calisthenics-analyzer")
    vcsUrl.set("https://github.com/LarsEckart/object-calisthenics-analyzer.git")

    plugins {
        create("objectCalisthenics") {
            id = "com.github.larseckart.object-calisthenics"
            implementationClass = "com.github.larseckart.objectcalisthenics.gradle.ObjectCalisthenicsPlugin"
            displayName = "Object Calisthenics Analyzer"
            description = "Configurable Object Calisthenics checks and reports for Java projects"
            tags.set(listOf("java", "object-calisthenics", "static-analysis", "quality"))
        }
    }
}
