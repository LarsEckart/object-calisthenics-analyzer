plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "object-calisthenics"

include("analyzer")
include("gradle-plugin")
