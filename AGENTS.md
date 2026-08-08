# Object Calisthenics Analyzer

A Java 17+ analyzer and Gradle plugin that enforce configurable Object Calisthenics rules.

- `analyzer/` contains the JavaParser-based analysis engine and CLI.
- `gradle-plugin/` exposes the analyzer as the `com.github.larseckart.object-calisthenics` Gradle plugin.

Build and test from the repository root with `./gradlew check`.

See [README.md](README.md) for installation, configuration, tasks, report output, and compatibility. See [SPEC.md](SPEC.md) for rule behaviour and edge cases.
