import com.vanniktech.maven.publish.DeploymentValidation

plugins {
    id("java-library")
    application
    id("com.vanniktech.maven.publish") version "0.37.0"
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true, validateDeployment = DeploymentValidation.PUBLISHED)
    signAllPublications()

    coordinates(project.group.toString(), "object-calisthenics-analyzer", project.version.toString())

    pom {
        name.set("Object Calisthenics Analyzer")
        description.set("Configurable Object Calisthenics checks for Java source")
        inceptionYear.set("2026")
        url.set("https://github.com/LarsEckart/object-calisthenics-analyzer")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("LarsEckart")
                name.set("Lars Eckart")
                url.set("https://github.com/LarsEckart")
            }
        }

        scm {
            url.set("https://github.com/LarsEckart/object-calisthenics-analyzer")
            connection.set("scm:git:git://github.com/LarsEckart/object-calisthenics-analyzer.git")
            developerConnection.set("scm:git:ssh://git@github.com/LarsEckart/object-calisthenics-analyzer.git")
        }

        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/LarsEckart/object-calisthenics-analyzer/issues")
        }
    }
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
