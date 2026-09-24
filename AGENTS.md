# Object Calisthenics Analyzer

A Java 17+ analyzer and Gradle plugin that enforce configurable Object Calisthenics rules.

- `analyzer/` contains the JavaParser-based analysis engine and CLI.
- `gradle-plugin/` exposes the analyzer as the `com.larseckart.object-calisthenics` Gradle plugin.

Build and test from the repository root with `./gradlew check`.

Releases start with `.github/workflows/prepare-release.yml`. Trigger it with `gh workflow run prepare-release.yml -f version=X.Y.Z` (for example `gh workflow run prepare-release.yml -f version=0.3.0`). It updates the version strings in `README.md` and commits and pushes to `main`. Then run `gh release create vX.Y.Z --generate-notes` (for example `gh release create v0.3.0 --generate-notes`). This creates the tag and release on the updated `main` commit and the `release.yml` workflow publishes the analyzer to Maven Central and the plugin to the Gradle Plugin Portal.

See [README.md](README.md) for installation, configuration, tasks, report output, and compatibility. See [SPEC.md](SPEC.md) for rule behaviour and edge cases.
