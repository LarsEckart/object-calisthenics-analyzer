plugins {
    id("java-library")
    application
}

dependencies {
    implementation(libs.javaparser.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}

application {
    mainClass.set("com.github.larseckart.objectcalisthenics.analyzer.ObjectCalisthenicsAnalyzerCli")
}
