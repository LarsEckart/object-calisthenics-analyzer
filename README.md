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
    id("com.larseckart.object-calisthenics") version "0.5.0"
}

repositories {
    mavenCentral()
}
```

The analyzer library is also on Maven Central as
`com.larseckart:object-calisthenics-analyzer:0.5.0`.

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
        allowedRawTypes.set(setOf("boolean", "int"))
        boundaryClassNamePatterns.add(".*(Request|Response)$")
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
| `oneTopLevelTypePerFile` | `false` | Optional companion rule: give each top-level type its own file. |
| `maxClassLines` | `50` | Meaningful-line limit per class. |
| `maxFieldsPerClass` | `2` | Maximum instance fields per class. |
| `includeRecordComponentsInFieldRule` | `true` | Count record components as fields. |
| `forbidElse` | `true` | Forbid the `else` keyword. |
| `maxMethodNesting` | `1` | Maximum nesting depth per method. |
| `forbidGetters` | `true` | Forbid getters that expose state. |
| `forbidSetters` | `true` | Forbid setters. |
| `strictGetterNames` | `false` | Flag every getter-shaped name, even computed queries. |
| `forbidNonFirstClassCollections` | `true` | Require collection-only wrappers and flag bare collections, maps, arrays, and varargs in method and constructor signatures. |
| `forbidTraversalChains` | `true` | Forbid chains through collaborators (`a.b().c()`). |
| `primitiveObsessionThreshold` | `5` | Minimum evidence score for a repeated primitive/String concept connected through fields or method-argument flow; `-1` disables the heuristic. |
| `allowedRawTypes` | All primitive, boxed primitive, and `String` types | Raw types permitted in fields, record components, and callable parameters. Set this to a narrower set to opt into strict policy findings for every other raw type. |
| `boundaryClassNamePatterns` | `[]` | Regexes matching simple class/record names where raw types are intentional, such as transport, persistence, framework, or configuration boundaries. |
| `fluentChainMethods` | `[]` | Method names that continue a fluent/value operation. |
| `safeChainRoots` | `System.out`, `System.err` | Receivers that may start a multi-step chain. |
| `reports.json` | — | JSON report path. |
| `reports.consoleSummary` | `true` | Print a console summary. |

Suppress a per-type exception with `@SuppressWarnings("calisthenics:fields")`,
`@SuppressWarnings("calisthenics:primitive-obsession")`, or
`@SuppressWarnings("calisthenics:raw-domain-primitive")`.

The first-class collections rule reports `unwrapped-collection` for parameters
and return types at every visibility, including private helpers. Constructors
of types with exactly one collection field (or record component) are exempt.
For external API contracts or deliberate boundaries, suppress a method,
constructor, or type with `@SuppressWarnings("calisthenics:unwrapped-collection")`.
This is a design smell, not proof that a wrapper would improve the code.
Type recognition uses simple names, not resolved inheritance.

## Java analyzer settings

Use named settings when calling the analyzer from Java:

```java
RuleSet rules = RuleSet.builder()
    .maxClassLines(80)
    .enable(Rule.ONE_TOP_LEVEL_TYPE_PER_FILE)
    .build();

CompilationUnitChecks checks =
    new CompilationUnitChecks(rules, PrimitiveObsessionConfig.defaults(), List.of());
ObjectCalisthenicsAnalyzer analyzer =
    new ObjectCalisthenicsAnalyzer(new JavaSourceParser(), checks);
AnalysisResult result = analyzer.analyze(sourceRoot);
```

Use `enable(Rule.FORBID_ELSE)` or `disable(Rule.FORBID_ELSE)` for rule switches.
Numeric limits keep named methods such as `maxClassLines(80)`.
Unspecified settings keep the defaults. `RuleSet.defaults()` remains available.
Each build creates an immutable value, independent of later builder changes.
The builder copies collection inputs and exposes read-only sets.

**Java interface change:** the positional `RuleSet` constructors have been
removed. Replace those calls with named builder settings. The Gradle settings
and their defaults have not changed.

`ObjectCalisthenicsAnalyzer` now has two constructors: no arguments for defaults,
or `(JavaSourceParser, CompilationUnitChecks)` for custom setup. Replace the old
rule-based constructors with the injected form shown above.

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
- Wrap bare collections, maps, arrays, and varargs in method parameters and
  return types, and in constructor parameters except when building a collection wrapper.
- Do not traverse through collaborators in receiver chains.
- Wrap repeated primitive and String concepts when validation, transformation,
  comparison, or arithmetic behaviour has accumulated around them.
- Enforce an opt-in policy for raw primitive, boxed primitive, and `String`
  declarations outside configured boundary types.

### Carry grouped concepts through constructors

When you group related fields into a value object, consider accepting that
object in the constructor too. For example, prefer
`Product(String barCode, ProductDetails details)` over
`Product(String barCode, String name, int priceInCents)` that builds
`ProductDetails` inside. This lets callers work with the new concept rather
than its parts.

This is design advice, not a constructor rule. The field limit counts stored
state, not constructor inputs. Conversion and setup may belong in a
constructor, so its parameter types need not match its field types.

### Raw-type policy

The strict raw-type policy is separate from the heuristic. By default,
`allowedRawTypes` contains every supported raw type, so it produces no
findings. Narrow the set to the technical types that may remain raw in domain
code. Every field, record component, constructor parameter, and method
parameter using another raw type then produces a `raw-domain-primitive`
finding. Static fields, arrays, and single-component primitive record wrappers
are not inspected. `boundaryClassNamePatterns` matches simple type names and
keeps intentional transport, persistence, framework, or configuration data
out of this policy without disabling other rules for those types.

For example, this policy permits raw booleans and integers in domain code,
allows all raw representations in request/response records, and reports raw
`String`, `long`, and other declarations elsewhere:

```kotlin
rules {
    allowedRawTypes.set(setOf("boolean", "int"))
    boundaryClassNamePatterns.add(".*(Request|Response)$")
}
```

Policy findings suggest a meaningful value object only when the declaration
represents a domain concept. Add an allowed type or boundary exception when a
raw representation is intentional; the rule does not claim every primitive
needs a wrapper.

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

## Optional companion rules

These support Object Calisthenics but are not part of the nine original rules.

Enable one top-level type per file with:

```kotlin
objectCalisthenics {
    rules {
        oneTopLevelTypePerFile.set(true)
    }
}
```

This reports `multiple-top-level-types` for each extra top-level class, record,
enum, interface, or annotation type. The type matching the filename stays;
if none matches, the first type stays. Each finding points to the extra type's
declaration. Class-name exclusions skip findings for matching types without
changing which type stays. Nested, local, and anonymous types do not count.
Files without type declarations produce no finding.

Move extra types into same-named files in the same package. They can stay
package-private. Do not nest types just to avoid the check: file layout does
not prove that responsibilities belong together. Class-length checks remain
unchanged.

## Rules not yet implemented

- **Don't abbreviate.** Needs a configurable list of banned abbreviations.

## Articles

- William Durand — [Object Calisthenics](https://williamdurand.fr/2013/06/03/object-calisthenics/)
- Jeff Bay — *Object Calisthenics*, in **The ThoughtWorks Anthology** (2007)
