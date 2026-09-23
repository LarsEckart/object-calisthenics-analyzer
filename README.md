[![Certified Shovelware](https://justin.searls.co/img/shovelware.svg)](https://justin.searls.co/shovelware/)

# Object Calisthenics Analyzer

A small Java [JavaParser](https://javaparser.org/)-based analyzer plus a Gradle
plugin that checks Java projects against a configurable set of Object
Calisthenics rules. Each finding also explains the design goal, suggests
possible refactorings, and names the judgement that still needs a human.

## Compatibility

The plugin is compiled for Java 17 and works on Java 17 or later. These checks
used Gradle 9.6.1, the version in this repository's wrapper.

| JDK that runs Gradle and the plugin | Status | Verified with |
| --- | --- | --- |
| 17 | Supported | Project test suite and a Java 17 composite-build consumer |
| 21 | Supported | Project test suite |
| 26 | Supported | Project test suite and a Java 26 composite-build consumer |

The analyzer parses Java 26 source syntax, the newest level supported by its
current JavaParser version. Source that uses language syntax added after Java 26
is not supported yet.

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

After the first release, install the plugin from the Gradle Plugin Portal:

```kotlin
plugins {
    id("com.larseckart.object-calisthenics") version "<released-version>"
}

repositories {
    mavenCentral()
}
```

The analyzer library is published to Maven Central as
`com.larseckart:object-calisthenics-analyzer:<released-version>`.
Until the first release, use the plugin through a Gradle composite build:

```kotlin
// settings.gradle.kts in the consumer project
pluginManagement {
    includeBuild("../object-calisthenics-analyzer")
}
```

```kotlin
// build.gradle.kts in the consumer project
plugins {
    id("com.larseckart.object-calisthenics")
}
```

## Release

Before making a release, run **Actions → Release → Run workflow** on `main`
and check that the `validate` job passes. This signs and checks the Maven
artifacts without publishing them. Then publish a GitHub release with a tag
such as `v0.1.0`. The release workflow
runs the tests, publishes the signed analyzer library to Maven Central, waits
for it to become available, then publishes the plugin to the Gradle Plugin
Portal and tests it in a fresh build. The plugin ID stays
`com.larseckart.object-calisthenics`.

Before the first release, confirm ownership of the `com.larseckart` Maven
Central namespace and the plugin ID on the Plugin Portal. Set these GitHub
Actions secrets in this repository (or share them through an organization):
`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_IN_MEMORY_KEY`,
`SIGNING_IN_MEMORY_KEY_PASSWORD`, `GRADLE_PUBLISH_KEY`, and
`GRADLE_PUBLISH_SECRET`. GitHub does not let a workflow in this repository
read secrets scoped only to another repository. The signing secrets hold the
full ASCII-armored private key (including the `BEGIN` and `END` lines, not
base64) and its passphrase. Find them in 1Password: **Private → Java release
signing — object-calisthenics-analyzer + crappy-java**. The key fingerprint is
`45353912409CF4BE37320C1FE012009D3EE97E82`; it expires **2028-09-23
(UTC)**. Fetch the public key from `keyserver.ubuntu.com` by fingerprint to
check signatures. Keep the old `tcr-extension` key to check past releases; it
expired in July 2025. Maven Central and Plugin Portal credentials are separate
from the signing key.

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
      "message": "ApiResource has 943 meaningful lines (limit 50)",
      "advice": {
        "principle": "Keep each class focused on one responsibility.",
        "options": [
          "Split the class by responsibility, not just by line count.",
          "Move behaviour together with the data it uses."
        ],
        "caution": "Do not split a cohesive class merely to meet the line limit."
      }
    }
  ]
}
```
