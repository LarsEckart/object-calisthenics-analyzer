# The nine rules: implementation map

This maps the original Object Calisthenics rules to the classes that evaluate
Java source today. A check measures a source pattern; it does not prove that a
design follows the spirit of the rule.

All checker classes below live in
[`analyzer/.../analyzer/checks/`](../analyzer/src/main/java/com/larseckart/objectcalisthenics/analyzer/checks/).

| # | Original rule | Evaluation classes | What the analyzer checks |
|---|---|---|---|
| 1 | Only one level of indentation per method | [MethodChecker] and [NestingDepthCalculator] | `MethodChecker.checkNesting()` compares measured nesting depth with `maxMethodNesting` (default: 1). Reports `method-over-nested`. This measures syntax-tree nesting, not whitespace. |
| 2 | Don't use the `else` keyword | [MethodChecker] | `checkElse()` finds `if` statements with an `else` branch when `forbidElse` is enabled. Reports `else-used`. |
| 3 | Wrap all primitives and strings | [PrimitiveObsessionChecker] and [RawTypePolicyChecker] | The first scores repeated primitive/String concepts with behavioural evidence and reports `primitive-obsession` (default threshold: 5). The second checks fields, record components, and callable parameters against `allowedRawTypes` and boundary-class exceptions, reporting `raw-domain-primitive`. By default, all supported raw types are allowed, so the strict policy produces no findings. These checks do not decide whether a value has domain meaning. |
| 4 | First-class collections | [FieldChecker] and [CollectionSignatureChecker], supported by [CollectionTypes] | `FieldChecker.checkFirstClassCollections()` requires a type with collection state to own exactly one collection field/component and no other instance state (`non-first-class-collection`). `CollectionSignatureChecker` finds bare collections, maps, arrays, and varargs in callable signatures (`unwrapped-collection`), with an exception for collection-wrapper constructor parameters. Controlled by `forbidNonFirstClassCollections`. |
| 5 | One dot per line | [TraversalChainChecker] | Finds receiver chains that traverse collaborators (`traversal-chain`) when `forbidTraversalChains` is enabled. Uses fluent-chain exceptions and safe roots from `RuleSet`; it does not count literal dots per source line. |
| 6 | Don't abbreviate | None | Not implemented. |
| 7 | Keep all entities small | [ClassLengthChecker] | Counts non-blank source lines that do not start with `//`, from the type declaration through its end. Reports `class-too-long` above `maxClassLines` (default: 50). The original package limit of 10 files is not implemented. |
| 8 | No classes with more than two instance variables | [FieldChecker] | `checkClassFields()` counts non-static fields; `checkRecordFields()` counts record components when enabled. Compares with `maxFieldsPerClass` (default: 2). Reports `too-many-instance-fields` or `too-many-record-components`. |
| 9 | No getters/setters/properties | [MethodChecker] and [GetterChecker] | `GetterChecker` detects getter-shaped methods, using declared/returned state by default or names alone with `strictGetterNames`. `MethodChecker.checkSetter()` detects setter-shaped names with a void return and one parameter. Reports `getter` or `setter`. No separate public-field/property check exists. |

## How the checks run

1. [ObjectCalisthenicsAnalyzer] asks `JavaSourceParser` to parse the source files
   and collects the results.
2. [CompilationUnitChecks.check()][CompilationUnitChecks] coordinates checks for
   each parsed file. It applies class-name exclusions, walks types and their
   members, and runs the type-, method-, and file-level checkers.
3. The individual checkers evaluate their source patterns and emit violations.

There is no shared `Check` interface. The coordinator calls concrete checkers
with the syntax-tree nodes each one needs.

**Scope matters:** `MethodChecker` only checks public methods with bodies
(including implicit public interface methods). Its nesting, `else`, getter,
and setter checks do not cover constructors or non-public methods.
Other checkers have their own scope and exceptions; see their linked source
for details.

## Configuration

- [RuleSet] holds most rule switches, limits, and call-chain exceptions.
- [PrimitiveObsessionConfig] holds the primitive heuristic threshold, allowed
  raw types, and boundary-class patterns. `CompilationUnitChecks` uses it to
  configure both primitive-related checkers.
- Whole-class exclusion patterns are a separate constructor input to
  `CompilationUnitChecks`.

The analyzer's default constructor wires these defaults together. Its injected
constructor takes a parser and `CompilationUnitChecks`, not the two rule
configuration objects directly.

## Extra check outside the nine rules

`CompilationUnitChecks.checkTopLevelTypes()` implements the optional
`oneTopLevelTypePerFile` setting. It reports `multiple-top-level-types` for
extra top-level types. This is a file-layout check, not the original rule's
package-size limit.

See [README.md](../README.md) for user settings and [SPEC.md](../SPEC.md) for
rule behaviour and design notes.

[MethodChecker]: ../analyzer/src/main/java/com/larseckart/objectcalisthenics/analyzer/checks/MethodChecker.java
[NestingDepthCalculator]: ../analyzer/src/main/java/com/larseckart/objectcalisthenics/analyzer/checks/NestingDepthCalculator.java
[PrimitiveObsessionChecker]: ../analyzer/src/main/java/com/larseckart/objectcalisthenics/analyzer/checks/PrimitiveObsessionChecker.java
[RawTypePolicyChecker]: ../analyzer/src/main/java/com/larseckart/objectcalisthenics/analyzer/checks/RawTypePolicyChecker.java
[FieldChecker]: ../analyzer/src/main/java/com/larseckart/objectcalisthenics/analyzer/checks/FieldChecker.java
[CollectionSignatureChecker]: ../analyzer/src/main/java/com/larseckart/objectcalisthenics/analyzer/checks/CollectionSignatureChecker.java
[CollectionTypes]: ../analyzer/src/main/java/com/larseckart/objectcalisthenics/analyzer/checks/CollectionTypes.java
[TraversalChainChecker]: ../analyzer/src/main/java/com/larseckart/objectcalisthenics/analyzer/checks/TraversalChainChecker.java
[ClassLengthChecker]: ../analyzer/src/main/java/com/larseckart/objectcalisthenics/analyzer/checks/ClassLengthChecker.java
[GetterChecker]: ../analyzer/src/main/java/com/larseckart/objectcalisthenics/analyzer/checks/GetterChecker.java
[CompilationUnitChecks]: ../analyzer/src/main/java/com/larseckart/objectcalisthenics/analyzer/checks/CompilationUnitChecks.java
[ObjectCalisthenicsAnalyzer]: ../analyzer/src/main/java/com/larseckart/objectcalisthenics/analyzer/ObjectCalisthenicsAnalyzer.java
[RuleSet]: ../analyzer/src/main/java/com/larseckart/objectcalisthenics/analyzer/RuleSet.java
[PrimitiveObsessionConfig]: ../analyzer/src/main/java/com/larseckart/objectcalisthenics/analyzer/PrimitiveObsessionConfig.java
