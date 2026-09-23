package com.github.larseckart.objectcalisthenics.analyzer;

/**
 * Configuration for the rules the analyzer enforces.
 *
 * The defaults reproduce the checks performed by the reference Python script.
 */
public record RuleSet(
    int maxClassLines,
    int maxFieldsPerClass,
    boolean includeRecordComponentsInFieldRule,
    boolean forbidElse,
    int maxMethodNesting,
    boolean forbidGetters,
    boolean forbidSetters,
    boolean forbidNonFirstClassCollections
) {

  /**
   * Creates a rule set that includes record components in the field rule.
   */
  public RuleSet(
      int maxClassLines,
      int maxFieldsPerClass,
      boolean forbidElse,
      int maxMethodNesting,
      boolean forbidGetters,
      boolean forbidSetters,
      boolean forbidNonFirstClassCollections
  ) {
    this(
        maxClassLines,
        maxFieldsPerClass,
        true,
        forbidElse,
        maxMethodNesting,
        forbidGetters,
        forbidSetters,
        forbidNonFirstClassCollections
    );
  }

  public static RuleSet defaults() {
    return new RuleSet(50, 2, true, true, 1, true, true, false);
  }
}
