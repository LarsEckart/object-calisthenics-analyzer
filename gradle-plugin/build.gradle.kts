plugins {
    id("java-gradle-plugin")
}

dependencies {
    implementation(project(":analyzer"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("objectCalisthenics") {
            id = "com.github.larseckart.object-calisthenics"
            implementationClass = "com.github.larseckart.objectcalisthenics.gradle.ObjectCalisthenicsPlugin"
        }
    }
}
