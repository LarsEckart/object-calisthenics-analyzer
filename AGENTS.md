# Object Calisthenics Analyzer

A Java 17+ analyzer and Gradle plugin that enforce configurable Object Calisthenics rules.

- `analyzer/` contains the JavaParser-based analysis engine and CLI.
- `gradle-plugin/` exposes the analyzer as the `com.larseckart.object-calisthenics` Gradle plugin.

Build and test from the repository root with `./gradlew check`.

Releases are tag-driven: run `gh release create vX.Y.Z --title "vX.Y.Z" --generate-notes` and the `release.yml` workflow publishes the analyzer to Maven Central and the plugin to the Gradle Plugin Portal.

See [README.md](README.md) for installation, configuration, tasks, report output, and compatibility. See [SPEC.md](SPEC.md) for rule behaviour and edge cases.
