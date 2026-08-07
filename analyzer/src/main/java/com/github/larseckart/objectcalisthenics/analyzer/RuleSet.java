package com.github.larseckart.objectcalisthenics.analyzer;

/**
 * Configuration for the rules the analyzer enforces.
 *
 * The defaults reproduce the checks performed by the reference Python script.
 */
public record RuleSet(
    int maxClassLines,
    int maxFieldsPerClass,
    boolean forbidElse,
    int maxMethodNesting,
    boolean forbidGetters,
    boolean forbidSetters
) {

  public static RuleSet defaults() {
    return new RuleSet(50, 2, true, 1, true, true);
  }
}
