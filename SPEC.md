# Object Calisthenics Analyzer — Java + JavaParser + Gradle Plugin

## Goal

Build a small, testable Gradle plugin that analyses Java source code and reports Object Calisthenics violations. The first version should reproduce — and then improve on — the Python `measure.sh` script used in `padel-slop-kata`. It should run as part of a normal Gradle build and produce both a failure-on-violation task and a report task.

## Why JavaParser instead of regex?

The current Python script parses Java with regular expressions. That works for a quick baseline but is fragile around:

- nested generics, records, lambdas
- comments and string literals that look like code
- annotations on classes, methods and parameters
- `record` component counts
- distinguishing method overloads, inner classes, and local variables from fields

JavaParser gives us the real AST, so each rule can be expressed against AST nodes (`ClassOrInterfaceDeclaration`, `MethodDeclaration`, `BlockStmt`, `IfStmt`, `FieldDeclaration`, etc.) instead of line patterns.

## Reference implementation

The existing measurement script is:

```
/Users/lars/GitHub/padel-slop-kata/.auto/measure.sh
```

It outputs `METRIC` lines for five measurable rules:

- class body larger than 50 non-comment, non-blank lines
- class with more than two instance variables; record components are included
  by default and can be excluded with `includeRecordComponentsInFieldRule`
- method containing an `else` keyword
- method with more than one level of nested braces
- public getter or setter method

It also reports a total `violations` count.

The script uses regex-based scanning and line counting. It deliberately does **not** try to measure:

- wrap all primitives and strings in classes
- first-class collections
- one dot per line
- don't abbreviate

Those four are left for human review. The Java analyzer can close that gap.

## Proposed module design

A deep module with a tiny Gradle-facing interface and most of the complexity hidden in the analyzer:

```
┌────────────────────────────────────────┐
│ Gradle plugin DSL + tasks              │  public entry point
├────────────────────────────────────────┤
│ ObjectCalisthenicsAnalyzer (Java)      │  parses and checks files
│   - JavaParser source root             │
│   - Rule implementations               │
│   - Results aggregator                 │
└────────────────────────────────────────┘
```

### Suggested Gradle DSL

```kotlin
plugins {
    id("object-calisthenics")
}

objectCalisthenics {
    sourceSet.set(project.sourceSets["main"])
    rules {
        maxClassLines.set(50)
        maxFieldsPerClass.set(2)
        includeRecordComponentsInFieldRule.set(true)
        forbidElse.set(true)
        maxMethodNesting.set(1)
        forbidGetters.set(true)
        forbidSetters.set(true)
        noRawCollections.set(false) // opt-in later
    }
    reports {
        json.set(layout.buildDirectory.file("reports/calisthenics.json"))
        consoleSummary.set(true)
    }
}
```

### Tasks

- `objectCalisthenicsCheck` — analyses source and fails the build if violations exceed configured thresholds.
- `objectCalisthenicsReport` — writes a JSON and/or Markdown report without failing.

### Output format

```json
{
  "violations": 0,
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

For backwards compatibility with `.auto/measure.sh`, the task could also print:

```
METRIC violations=35
METRIC classes_over_50=3
METRIC classes_over_2_fields=6
METRIC methods_with_else=1
METRIC methods_over_nested=21
METRIC getter_setter_methods=4
```

## Mapping the nine rules to JavaParser

| Rule | Relevant AST nodes | Detection idea |
|---|---|---|
| Keep all classes < 50 lines | `ClassOrInterfaceDeclaration`, `RecordDeclaration`, `EnumDeclaration` | Compute meaningful lines from source range, ignoring blank lines and line comments |
| ≤ 2 instance variables per class | `FieldDeclaration` inside a type, `RecordDeclaration.getParameters()` | Count non-static instance fields. Count record components when `includeRecordComponentsInFieldRule` is enabled. Suppress an intentional per-type exception with `@SuppressWarnings("calisthenics:fields")` |
| Don't use `else` | `IfStmt` | Flag when `getElseStmt().isPresent()` |
| One level of indentation per method | `MethodDeclaration`, `BlockStmt` inside it | Walk the method body and find the maximum nesting depth of blocks/statements. Depth > 1 is a violation. Loops, ifs, try-with-resources, lambdas and anonymous classes all count as nesting |
| No getters or setters | `MethodDeclaration` | Detect getter-shaped methods when the property is a declared field/record component or the body directly returns state; optionally use strict name-only matching. Detect `public void setX(T x)` setters by signature |
| Wrap all primitives and strings | field and method parameter types | Flag public fields, parameters or return types that are primitive, boxed, `String`, or arrays of those when they represent domain concepts. Allow value-object wrappers. Requires configuration/deny-list |
| First-class collections | field and method parameter types | Flag `List`, `Set`, `Map`, arrays or varargs that are exposed publicly without a wrapping collection class |
| One dot per line | `MethodCallExpr`, `FieldAccessExpr` | Follow receiver paths and flag maximal chains with more than one non-exempt step. Inspect arguments independently; treat configured fluent methods and safe roots as exceptions |
| Don't abbreviate | names of classes, methods, fields, variables, parameters | Maintain a configurable list of abbreviations (e.g. `conn`, `stmt`, `rs`, `db`, `id`) and flag identifiers containing them |

## Implementation sketch

```java
public class ObjectCalisthenicsAnalyzer {

    public AnalysisResult analyze(Path sourceRoot) {
        SourceRoot root = new SourceRoot(sourceRoot);
        List<CompilationUnit> units = root.tryToParseParallelized();

        List<Violation> violations = new ArrayList<>();
        for (CompilationUnit unit : units) {
            for (TypeDeclaration<?> type : unit.getTypes()) {
                checkType(type, violations);
                for (BodyDeclaration<?> member : type.getMembers()) {
                    if (member instanceof MethodDeclaration method) {
                        checkMethod(method, violations);
                    }
                }
            }
        }
        return new AnalysisResult(violations);
    }

    private void checkMethod(MethodDeclaration method, List<Violation> violations) {
        if (ruleSet.forbidsElse() && containsElse(method)) {
            violations.add(new Violation(method, "else-used", ...));
        }
        if (ruleSet.maxMethodNesting() > 0 && nestingDepth(method) > ruleSet.maxMethodNesting()) {
            violations.add(new Violation(method, "method-over-nested", ...));
        }
        if (ruleSet.forbidsGetters() && looksLikeGetter(method)) {
            violations.add(new Violation(method, "getter", ...));
        }
        // ...
    }
}
```

The analyzer is framework-agnostic. The Gradle plugin is a thin adapter that:

1. Collects the source set directories.
2. Calls `ObjectCalisthenicsAnalyzer.analyze(...)`.
3. Writes reports.
4. Fails the task if violations exceed thresholds.

## Migration path from the Python script

1. Build the Java analyzer as a standalone library first.
2. Run it on `backend/app/src/main/java/org/example` and compare its output with `./.auto/measure.sh` until they agree.
3. Wrap the analyzer in the Gradle plugin and add it to `backend/build.gradle.kts`.
4. Update `.auto/prompt.md` to point at the new task instead of the Python script.
5. Keep `measure.sh` during a transition period, then remove it once the plugin is trusted.

## Open questions

- **Abbreviations.** The list is subjective. Make it a configuration file in each repo so teams can tune it.
- **Primitive wrapping.** The rule is in tension with configuration / DTO / JSON mapping. Define a whitelist for framework-level raw types and a deny-list for domain concepts.
- **Lambda bodies.** A lambda with `{}` can introduce nested braces inside a method. Decide whether the rule applies to them or only to explicit `BlockStmt`s in the method body.

## Suggested first deliverables

1. A Java module with `ObjectCalisthenicsAnalyzer`, the five measured rules implemented, and JUnit tests against sample classes.
2. A simple `main` that can be run from Gradle with `--args=src/main/java`.
3. Once parity with `measure.sh` is proven, add the Gradle plugin wrapper.
