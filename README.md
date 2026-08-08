[![Certified Shovelware](https://justin.searls.co/img/shovelware.svg)](https://justin.searls.co/shovelware/)

# Object Calisthenics Analyzer

A small Java [JavaParser](https://javaparser.org/)-based analyzer plus a Gradle
plugin that checks Java projects against a configurable set of Object
Calisthenics rules.

## Compatibility

The plugin is compiled for Java 17 and works on Java 17 or later. These checks
used Gradle 9.6.1, the version in this repository's wrapper.

| JDK that runs Gradle and the plugin | Status | Verified with |
| --- | --- | --- |
| 17 | Supported | Project test suite and a Java 17 composite-build consumer |
| 21 | Supported | Project test suite |
| 26 | Supported | Project test suite and a Java 26 composite-build consumer |

The analyzer parses Java 21 source syntax, the newest level supported by its
current JavaParser version. A project may compile for Java 26, but source that
uses language syntax added after Java 21 is not supported yet.

## What it checks

The plugin currently enforces six rules:

1. Keep all classes under 50 meaningful lines. (`maxClassLines`)
2. No class or record may have more than two instance fields/components.
   (`maxFieldsPerClass`)
3. Do not use the `else` keyword. (`forbidElse`)
4. One level of nesting per method. (`maxMethodNesting`)
5. No getters or setters by method name. (`forbidGetters`, `forbidSetters`)
6. A class or record that has a collection or array field may not have any
   other instance fields/components. (`forbidNonFirstClassCollections`)

## What it does not check (yet)

Out of the nine Object Calisthenics rules, three are not implemented yet:

- **Wrap all primitives and strings.** Telling domain values apart from plain
  configuration or framework types needs project-specific configuration.
- **One dot per line.** Requires a clear policy on fluent APIs and lawful
  Demeter violations (for example, `System.out.println`).
- **Don't abbreviate.** Needs a configurable list of abbreviations per team or
  codebase.

## Install

The plugin is not published to the Gradle Plugin Portal or Maven Central yet.
Use it through a Gradle composite build:

```kotlin
// settings.gradle.kts in the consumer project
pluginManagement {
    includeBuild("../object-calisthenics-analyzer")
}
```

```kotlin
// build.gradle.kts in the consumer project
plugins {
    id("com.github.larseckart.object-calisthenics")
}
```

## Use

```kotlin
objectCalisthenics {
    sourceSet.set(project.sourceSets["main"])

    rules {
        maxClassLines.set(50)
        maxFieldsPerClass.set(2)
        forbidElse.set(true)
        maxMethodNesting.set(1)
        forbidGetters.set(true)
        forbidSetters.set(true)
        forbidNonFirstClassCollections.set(true)
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
    "getter_setter_methods": 0,
    "non_first_class_collections": 0
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
