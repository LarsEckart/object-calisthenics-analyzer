package com.github.larseckart.objectcalisthenics.analyzer;

import java.util.List;
import java.util.Map;

/**
 * Practical guidance for addressing a rule violation.
 *
 * <p>Advice explains the design goal and offers options. It does not promise
 * that any option is safe without understanding the surrounding domain.</p>
 *
 * @param principle the design goal behind the rule
 * @param options possible ways to improve the design
 * @param caution a reminder of the trade-off that needs human judgement
 */
public record Advice(String principle, List<String> options, String caution) {

  private static final Advice CLASS_TOO_LONG = new Advice(
      "Keep each class focused on one responsibility.",
      List.of(
          "Split the class by responsibility, not just by line count.",
          "Move behaviour together with the data it uses.",
          "Extract a collaborating type for a distinct domain concept."),
      "Do not split a cohesive class merely to meet the line limit.");
  private static final Advice TOO_MANY_FIELDS = new Advice(
      "Keep related state and its behaviour together.",
      List.of(
          "Group values that change together in a value object.",
          "Move a field and the behaviour that uses it into a focused collaborator.",
          "Let one object hold state while another coordinates two collaborators."),
      "Choose domain concepts from the code's meaning; field counts alone cannot name them.");
  private static final Advice ELSE = new Advice(
      "Make each path explicit without growing conditional logic.",
      List.of(
          "Use a guard clause and return when one path ends early.",
          "Choose a clear default before the conditional when both paths produce a value.",
          "Use a state or strategy type when the cases are stable domain states."),
      "Do not add types only to remove an else; use them for real domain behaviour.");
  private static final Advice NESTING = new Advice(
      "Keep each method at one level of abstraction.",
      List.of(
          "Extract the inner block into a method named for the work it does.",
          "Use a guard clause to remove a wrapping conditional.",
          "Give a collaborator the part of the work that belongs with its data."),
      "Preserve the order of side effects and error handling while extracting methods.");
  private static final Advice ACCESSOR = new Advice(
      "Tell an object what to do instead of taking its data to decide elsewhere.",
      List.of(
          "Move the caller's decision into an intent-revealing method on this object.",
          "Expose a domain operation such as approve, matches, or display instead of raw state.",
          "Keep read-only access at a DTO or display boundary when callers do not make domain decisions."),
      "A structural check cannot tell a DTO boundary from domain code; review the caller before changing it.");
  private static final Advice TRAVERSAL_CHAIN = new Advice(
      "Ask an immediate collaborator to do the work instead of navigating through its collaborators.",
      List.of(
          "Move the operation to the first object in the chain and give it an intent-revealing name.",
          "Pass the required value to a collaborator instead of obtaining and coordinating its parts here.",
          "Configure an exception when this is a deliberate fluent API or value operation."),
      "Extracting temporary variables only hides the chain; first decide whether it is intentional value arithmetic or traversal.");
  private static final Advice FIRST_CLASS_COLLECTION = new Advice(
      "Give a domain collection its own home for collection behaviour.",
      List.of(
          "Wrap the collection in a domain type.",
          "Move filtering, matching, and aggregation operations into that type.",
          "Keep the collection wrapper's state limited to its collection."),
      "A collection wrapper should express domain behaviour, not just rename List.");
  private static final Advice PRIMITIVE_OBSESSION = new Advice(
      "Give a repeated domain value its own type when behaviour has accumulated around its primitive representation.",
      List.of(
          "Introduce a value object named for the concept and move validation and normalization into it.",
          "Move comparisons, arithmetic, and formatting that belong to the value onto the new type.",
          "Group primitives that repeatedly travel together when they form one cohesive concept."),
      "This is a scored heuristic; framework, transport, and configuration values may intentionally remain primitive.");
  private static final Advice UNKNOWN = new Advice(
      "Review the rule and the surrounding design.",
      List.of("Choose the smallest change that improves the design without changing behaviour."),
      "The analyzer found a structural pattern; only code context can confirm the right refactoring.");
  private static final Map<String, Advice> BY_RULE = Map.ofEntries(
      Map.entry("class-too-long", CLASS_TOO_LONG),
      Map.entry("too-many-instance-fields", TOO_MANY_FIELDS),
      Map.entry("too-many-record-components", TOO_MANY_FIELDS),
      Map.entry("else-used", ELSE),
      Map.entry("method-over-nested", NESTING),
      Map.entry("getter", ACCESSOR),
      Map.entry("setter", ACCESSOR),
      Map.entry("non-first-class-collection", FIRST_CLASS_COLLECTION),
      Map.entry("traversal-chain", TRAVERSAL_CHAIN),
      Map.entry("primitive-obsession", PRIMITIVE_OBSESSION));

  /**
   * Returns the standard guidance for a rule identifier.
   */
  public static Advice forRule(String rule) {
    return BY_RULE.getOrDefault(rule, UNKNOWN);
  }
}
