[![Certified Shovelware](https://justin.searls.co/img/shovelware.svg)](https://justin.searls.co/shovelware/)

# Object Calisthenics Analyzer

A Java [JavaParser](https://javaparser.org/)-based analyzer and Gradle plugin
that checks Java source against Object Calisthenics rules. Each finding also
gives the design principle and possible refactorings. Field, nesting, and
accessor findings also point to the source shape that triggered them. The
guidance stays non-prescriptive because the final design needs human judgement.

Requires Java 17 or later. Parses Java source up to Java 26.

## Add to a Gradle project

```kotlin
plugins {
    java
    id("com.larseckart.object-calisthenics") version "0.4.0"
}

repositories {
    mavenCentral()
}
```

The analyzer library is also on Maven Central as
`com.larseckart:object-calisthenics-analyzer:0.4.0`.

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
        primitiveObsessionThreshold.set(5)
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
| `primitiveObsessionThreshold` | `5` | Minimum evidence score for a repeated primitive/String concept connected through fields or method-argument flow; `-1` disables the heuristic. |
| `fluentChainMethods` | `[]` | Method names that continue a fluent/value operation. |
| `safeChainRoots` | `System.out`, `System.err` | Receivers that may start a multi-step chain. |
| `reports.json` | — | JSON report path. |
| `reports.consoleSummary` | `true` | Print a console summary. |

Suppress a per-type exception with `@SuppressWarnings("calisthenics:fields")`
or `@SuppressWarnings("calisthenics:primitive-obsession")`.

## Tasks

- `objectCalisthenicsCheck` – analyse and fail on new violations.
- `objectCalisthenicsCheckChanged` – check staged, unstaged, and untracked
  Java files in the configured source set. Requires Git; does not run as part of
  `check`. Skips when no Java files have changed. A baseline still hides known
  findings, but this task does not warn about stale entries in untouched files.
- `objectCalisthenicsBaseline` – write current violations to the baseline file.
- `objectCalisthenicsReport` – write the JSON report without failing.
- `check` depends on `objectCalisthenicsCheck`.

The console prints source-aware context below a finding when the rule can
extract useful evidence. The JSON detail then includes a `context` object with
its `kind`, `summary`, `related_code`, `suggestion`, and `caution`. Findings
without useful source context omit this object and keep the stable rule-level
advice.

To adopt the plugin on an existing codebase without fixing everything first:

```shell
./gradlew objectCalisthenicsBaseline
```

Commit the generated baseline file. Future checks then fail only for new
findings. To check only local changes instead, run:

```shell
./gradlew objectCalisthenicsCheckChanged
```

This checks whole changed files, not just changed lines or changes since a branch.

## Supported rules

- Keep all classes under 50 meaningful lines.
- No class or record may have more than two instance fields/components.
- Do not use the `else` keyword.
- One level of nesting per method.
- No getters that expose declared state and no setters.
- A class/record that owns a collection or array may not own any other instance
  field/component.
- Do not traverse through collaborators in receiver chains.
- Wrap repeated primitive and String concepts when validation, transformation,
  comparison, or arithmetic behaviour has accumulated around them.

The primitive-obsession rule groups fields, record components, and callable
parameters by name and primitive type. It reports one finding per concept only
when the concept is declared more than once and has behavioural evidence.
Enums and single-component primitive records already encapsulate their values,
so they are not inspected. Primitive arrays and array `offset`/`length`
coordinates are left to collection and parser rules. Its score is additive:

| Evidence | Score |
|----------|------:|
| Repeated declaration | 2 |
| Accepted by a private helper | 2 |
| Validation or conditional use | 3 |
| Normalization, parsing, or formatting | 2 |
| Comparison with a literal | 2 |
| Numeric arithmetic | 2 |
| Validation or conditional use in multiple methods | 3 |
| Repeatedly travels with another primitive concept | 2 |

The finding includes its score and evidence. This is a design prompt rather
than proof that a value object is required; framework, transport, and
configuration values may intentionally remain primitive.

## Rules not yet implemented

- **Don't abbreviate.** Needs a configurable list of banned abbreviations.

## Articles

- William Durand — [Object Calisthenics](https://williamdurand.fr/2013/06/03/object-calisthenics/)
- Jeff Bay — *Object Calisthenics*, in **The ThoughtWorks Anthology** (2007)
