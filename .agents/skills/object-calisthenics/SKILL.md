---
name: object-calisthenics
description: Object Calisthenics rules for implementing, reviewing, or explaining this analyzer’s checks and Java designs.
---

# Object Calisthenics

Treat these rules as design exercises, not universal laws. Apply a strict rule only when the requested policy or the configured analyzer rule set requires it.

## The nine rules

1. **One indentation level per method.** Extract methods until no control-flow block is nested inside another. Name each extraction for the work it does.
2. **No `else`.** Use a guard clause and return, a variable with a clear default, or a null object/empty collection. Use State or Strategy only for real, stable domain cases; do not add types merely to remove an `else`.
3. **Wrap primitives and strings.** Give a domain primitive with behaviour its own value object; replacing `int` with `Integer` adds no domain meaning. Do not mistake framework, configuration, or transport values for domain concepts without evidence.
4. **Use first-class collections.** A class that owns a collection owns no other instance state. Wrap the collection so its operations have a home.
5. **One dot per line.** Do not navigate through object graphs or chain ordinary calls. Ask an immediate collaborator to do the work. `a.foo(b.foo())` is not a traversal chain; `a.foo().bar()` is. Fluent interfaces and deliberate method-chaining APIs are exceptions.
6. **Do not abbreviate.** Use full, clear names. A name that is too hard to state may show duplication or mixed duties.
7. **Keep entities small.** The source exercise limits a class to 50 lines and a package to 10 files. Use the project’s configured limits when they differ.
8. **Use at most two instance variables.** Split a type with more state into focused objects; two values often mean one object maintains state and another coordinates it.
9. **No getters, setters, or public properties.** Tell an object what to do instead of reading its state and deciding outside it. Prefer intent-revealing operations. Read-only access can suit display code or DTOs at a system boundary when callers do not make domain decisions; setters remain disallowed.

## Apply the rules

Before calling code compliant, assess every rule that can apply to the requested scope. Mark each as compliant, a violation, not applicable, or an intentional exception, and name the rule for every finding.

When building or changing a check, preserve the difference between the design rule and a measurable policy. State the AST or source pattern, chosen threshold, and exceptions. Do not claim that a heuristic proves a rule that needs domain knowledge: primitive wrapping, call chaining, and abbreviations need project policy.

When refactoring, preserve behaviour and tests. Make a structural change that improves the design rather than hiding a violation from the analyzer.
