# Object Calisthenics Analyzer

A small Java [JavaParser](https://javaparser.org/)-based analyzer plus a Gradle
plugin that checks Java projects against the Object Calisthenics rules.

The analyzer replaces the regex-based `.auto/measure.sh` script from the
`padel-slop-kata` repository with an AST-aware implementation that is much less
fragile around generics, records, lambdas, annotations, and nested classes.

## Modules

- **`analyzer`** – Java library that parses source and reports violations.
- **`gradle-plugin`** – Thin Gradle wrapper that adds
  `objectCalisthenicsCheck` and `objectCalisthenicsReport` tasks.

## Currently enforced rules

The first version reproduces the five measurable rules from `measure.sh`:

1. Keep all classes under 50 meaningful lines. (`maxClassLines`)
2. No class or record may have more than two instance fields/components. (`maxFieldsPerClass`)
3. Do not use the `else` keyword. (`forbidElse`)
4. One level of nesting per method. (`maxMethodNesting`)
5. No getters or setters by method name. (`forbidGetters`, `forbidSetters`)

The remaining four rules (wrap primitives, first-class collections, one dot per
line, don't abbreviate) are designed to be added later as optional checks.

## Build

```bash
./gradlew build
```

## Run the analyzer from the command line

```bash
./gradlew :analyzer:run --args="src/main/java"
```

The CLI prints the same `METRIC` lines as the original Python script, followed
by a list of individual violations.

## Apply the Gradle plugin

The plugin is not published to the Gradle Plugin Portal or Maven Central yet.
Use it through a Gradle composite build pointing at this repository.

### 1. Include this repository as a composite build

In the consumer project's `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("../object-calisthenics")
}
```

The exact path depends on where the consumer project lives relative to this
repository. When the path is correct, Gradle resolves the plugin directly from
the included build, so no `version` is declared in the consumer.

### 2. Apply the plugin

In the Java project's `build.gradle.kts`:

```kotlin
plugins {
    id("com.github.larseckart.object-calisthenics")
}

objectCalisthenics {
    sourceSet.set(project.sourceSets["main"])

    rules {
        maxClassLines.set(50)
        maxFieldsPerClass.set(2)
        forbidElse.set(true)
        maxMethodNesting.set(1)
        forbidGetters.set(true)
        forbidSetters.set(true)
    }

    reports {
        json.set(layout.buildDirectory.file("reports/calisthenics/calisthenics.json"))
        consoleSummary.set(true)
    }
}
```

### Tasks

- `objectCalisthenicsCheck` – analyses source and fails the build if any
  violation is found.
- `objectCalisthenicsReport` – writes a JSON report without failing.
- The `check` lifecycle task depends on `objectCalisthenicsCheck`.

### JSON report format

```json
{
  "violations": 1,
  "metrics": {
    "classes_over_50": 1,
    "classes_over_2_fields": 0,
    "methods_with_else": 0,
    "methods_over_nested": 0,
    "getter_setter_methods": 0
  },
  "details": [
    {
      "file": "src/main/java/org/example/ApiResource.java",
      "line": 42,
      "rule": "class-too-long",
      "message": "ApiResource has 943 meaningful lines (limit 50)"
    }
  ]
}
```

## Migration from the Python script

1. Build and publish the plugin locally (or include the project).
2. Run both the plugin and `measure.sh` on the same source root.
3. Compare the `METRIC` lines.
4. Tune rules or violation details until parity is good enough.
5. Remove `measure.sh` once the plugin is trusted.
