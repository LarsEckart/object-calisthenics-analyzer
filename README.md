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

The plugin currently enforces seven rules:

1. Keep all classes under 50 meaningful lines. (`maxClassLines`)
2. No class or record may have more than two instance fields/components.
   (`maxFieldsPerClass`; set `includeRecordComponentsInFieldRule` to `false`
   to exclude records)
3. Do not use the `else` keyword. (`forbidElse`)
4. One level of nesting per method. (`maxMethodNesting`)
5. No getters that expose declared state and no setters. (`forbidGetters`,
   `forbidSetters`) Set `strictGetterNames` to flag every getter-shaped name,
   including computed queries such as `isReady()`.
6. A class or record that has a collection or array field may not have any
   other instance fields/components. (`forbidNonFirstClassCollections`)
7. Do not traverse through collaborators in receiver chains.
   (`forbidTraversalChains`)

## What it does not check (yet)

Out of the nine Object Calisthenics rules, two are not implemented yet:

- **Wrap all primitives and strings.** Telling domain values apart from plain
  configuration or framework types needs project-specific configuration.
- **Don't abbreviate.** Needs a configurable list of abbreviations per team or
  codebase.

## Install

Add the plugin to your Java project's `build.gradle.kts`. Gradle gets it from
the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/com.larseckart.object-calisthenics):

```kotlin
plugins {
    java
    id("com.larseckart.object-calisthenics") version "0.1.1"
}

repositories {
    mavenCentral()
}
```

The analyzer library is also available on
[Maven Central](https://central.sonatype.com/artifact/com.larseckart/object-calisthenics-analyzer)
as `com.larseckart:object-calisthenics-analyzer:0.1.1`. See
[releases](https://github.com/LarsEckart/object-calisthenics-analyzer/releases)
for newer versions.

## Release

Before making a release, run **Actions → Release → Run workflow** on `main`
and check that the `validate` job passes. This signs and checks the Maven
artifacts without publishing them. Then publish a GitHub release with a tag
such as `v0.1.2`. The release workflow runs the tests, publishes the signed
analyzer library to Maven Central, waits for it to become available, then
publishes the plugin to the Gradle Plugin Portal and tests it in a fresh build.
The plugin ID stays `com.larseckart.object-calisthenics`.

Keep these GitHub Actions secrets in this repository (or share them through an
organization):
`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_IN_MEMORY_KEY`,
`SIGNING_IN_MEMORY_KEY_PASSWORD`, `GRADLE_PUBLISH_KEY`, and
`GRADLE_PUBLISH_SECRET`. GitHub does not let a workflow in this repository
read secrets scoped only to another repository. The signing secrets hold the
full ASCII-armored private key (including the `BEGIN` and `END` lines, not
base64) and its passphrase. Find them in 1Password: **Private → Java release
signing — object-calisthenics-analyzer + crappy-java**. The key fingerprint is
`45353912409CF4BE37320C1FE012009D3EE97E82`; it expires **2028-09-23
(UTC)**. Fetch the public key from `keyserver.ubuntu.com` by fingerprint to
check signatures. Maven Central and Plugin Portal credentials are separate
from the signing key.

## Use

```kotlin
objectCalisthenics {
    sourceSet.set(project.sourceSets["main"])
    baselineFile.set(layout.projectDirectory.file("object-calisthenics-baseline.txt"))
    ignoreFailures.set(false)

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

The defaults include record components in the field rule. Set
`includeRecordComponentsInFieldRule` to `false` when records are primarily
configuration or data carriers and should not be checked by that rule. To
suppress an intentional exception on one class or record instead, annotate it
with `@SuppressWarnings("calisthenics:fields")`.

### Traversal-chain policy

The traversal rule follows only the receiver side of method calls and field
accesses. It reports one finding for each maximal receiver chain with more than
one step. It does not count independent calls in arguments as one chain:

- `service.execute()` and `a.b(c.d())` are allowed.
- `customer().address()` and `order.customer.address` are reported.
- In `a.b(c.d().e())`, only `c.d().e()` is reported.
- `this.service.execute()` is equivalent to `service.execute()`.
- Parentheses, casts, and array access do not hide a receiver chain.

`fluentChainMethods` names methods whose intermediate results continue the
same fluent or value operation. For example, configuring `minus` and `times`
allows `vector.minus(other).times(scale).dot(axis)`, but still reports
`ball.position().minus(other)` because `position()` crosses a collaborator
boundary. Method-name exceptions apply globally because the analyzer does not
resolve types. `safeChainRoots` contains exact dotted starting receivers;
`System.out` and `System.err` (including their `java.lang` forms) are safe by
default.

The rule is disabled by default. It deliberately does not infer whether
`ball.motion().relativeTo(...)` is a good whole-value collaboration while
`ball.position().minus(...)` is state traversal: those expressions have the
same AST shape without type and domain knowledge. Splitting a chain into local
variables can also hide it, so findings are coaching prompts rather than proof
of a design defect.

### Tasks

- `objectCalisthenicsCheck` – analyses source and fails the build if any
  violation not in the baseline is found (unless `ignoreFailures` is enabled).
- `objectCalisthenicsBaseline` – writes the current violations to the configured
  baseline file.
- `objectCalisthenicsReport` – writes a JSON report without failing.
- The `check` lifecycle task depends on `objectCalisthenicsCheck`.

### Adopting on an existing codebase

To start enforcing the rules without first fixing every existing finding, run:

```shell
./gradlew objectCalisthenicsBaseline
```

This creates `object-calisthenics-baseline.txt` in the project directory.
Commit that file. Each entry is identified by its project-relative source file,
rule, and class or method name, so moving code within the same file does not
invalidate it. Future `check` runs fail only for new findings. When findings
are fixed, the check reports stale entries; rerun the baseline task and commit
the smaller file.

For a reporting-only rollout, configure:

```kotlin
objectCalisthenics {
    ignoreFailures.set(true)
}
```

The check then reports all findings but does not fail the build. The baseline
path can be changed with `baselineFile`; it defaults to
`object-calisthenics-baseline.txt` in the project directory.

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
    "non_first_class_collections": 0,
    "traversal_chains": 0
  },
  "details": [
    {
      "file": "src/main/java/org/example/ApiResource.java",
      "line": 42,
      "rule": "class-too-long",
      "subject": "ApiResource",
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
