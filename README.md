[![Certified Shovelware](https://justin.searls.co/img/shovelware.svg)](https://justin.searls.co/shovelware/)

# Object Calisthenics Analyzer

A Java [JavaParser](https://javaparser.org/)-based analyzer and Gradle plugin
that checks Java source against Object Calisthenics rules. Each finding also
gives the design principle, possible refactorings, and the human judgement that
still belongs to you.

Requires Java 17 or later. Parses Java source up to Java 26.

## Add to a Gradle project

```kotlin
plugins {
    java
    id("com.larseckart.object-calisthenics") version "0.3.0"
}

repositories {
    mavenCentral()
}
```

The analyzer library is also on Maven Central as
`com.larseckart:object-calisthenics-analyzer:0.3.0`.

## Configure

```kotlin
objectCalisthenics {
    sourceSet.set(project.sourceSets["main"])
    baselineFile.set(layout.projectDirectory.file("object-calisthenics-baseline.txt"))
    ignoreFailures.set(false)

    exclusions {
        classNamePatterns.add(".*Response$")
    }

    rules {
        maxClassLines.set(50)
        maxFieldsPerClass.set(2)
        includeRecordComponentsInFieldRule.set(true)
        forbidElse.set(true)
        maxMethodNesting.set(1)
        forbidGetters.set(true)
        forbidSetters.set(true)
        forbidNonFirstClassCollections.set(true)
        strictGetterNames.set(false)
        forbidTraversalChains.set(true)
        fluentChainMethods.set(setOf("with", "build"))
        safeChainRoots.set(setOf("System.out", "System.err"))
    }

    reports {
        json.set(layout.buildDirectory.file("reports/calisthenics/calisthenics.json"))
        consoleSummary.set(true)
    }
}
```

All rules have sensible defaults. Set a rule to `false`/`-1` to leave it
unchecked.

| Option | Default | What it controls |
|--------|---------|------------------|
| `sourceSet` | `main` source set | Source to analyse. |
| `baselineFile` | `object-calisthenics-baseline.txt` | Existing violations to ignore. |
| `ignoreFailures` | `false` | Let the build succeed despite findings. |
| `exclusions.classNamePatterns` | `[]` | Regexes matching simple class/record names to skip entirely. |
| `maxClassLines` | `50` | Meaningful-line limit per class. |
| `maxFieldsPerClass` | `2` | Maximum instance fields per class. |
| `includeRecordComponentsInFieldRule` | `true` | Count record components as fields. |
| `forbidElse` | `true` | Forbid the `else` keyword. |
| `maxMethodNesting` | `1` | Maximum nesting depth per method. |
| `forbidGetters` | `true` | Forbid getters that expose state. |
| `forbidSetters` | `true` | Forbid setters. |
| `strictGetterNames` | `false` | Flag every getter-shaped name, even computed queries. |
| `forbidNonFirstClassCollections` | `true` | Forbid a collection field plus any other field in the same class. |
| `forbidTraversalChains` | `true` | Forbid chains through collaborators (`a.b().c()`). |
| `fluentChainMethods` | `[]` | Method names that continue a fluent/value operation. |
| `safeChainRoots` | `System.out`, `System.err` | Receivers that may start a multi-step chain. |
| `reports.json` | — | JSON report path. |
| `reports.consoleSummary` | `true` | Print a console summary. |

Suppress a per-type exception with `@SuppressWarnings("calisthenics:fields")`.

## Tasks

- `objectCalisthenicsCheck` – analyse and fail on new violations.
- `objectCalisthenicsBaseline` – write current violations to the baseline file.
- `objectCalisthenicsReport` – write the JSON report without failing.
- `check` depends on `objectCalisthenicsCheck`.

To adopt the plugin on an existing codebase without fixing everything first:

```shell
./gradlew objectCalisthenicsBaseline
```

Commit the generated baseline file. Future checks then fail only for new
findings.

## Supported rules

- Keep all classes under 50 meaningful lines.
- No class or record may have more than two instance fields/components.
- Do not use the `else` keyword.
- One level of nesting per method.
- No getters that expose declared state and no setters.
- A class/record that owns a collection or array may not own any other instance
  field/component.
- Do not traverse through collaborators in receiver chains.

## Rules not yet implemented

- **Wrap all primitives and strings.** Needs project-specific configuration to
  distinguish domain values from framework or configuration types.
- **Don't abbreviate.** Needs a configurable list of banned abbreviations.

## Articles

- William Durand — [Object Calisthenics](https://williamdurand.fr/2013/06/03/object-calisthenics/)
- Jeff Bay — *Object Calisthenics*, in **The ThoughtWorks Anthology** (2007)
