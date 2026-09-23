package com.github.larseckart.objectcalisthenics.analyzer;

import java.util.Set;

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
    boolean forbidNonFirstClassCollections,
    boolean strictGetterNames,
    boolean forbidTraversalChains,
    Set<String> fluentChainMethods,
    Set<String> safeChainRoots
) {

  private static final Set<String> DEFAULT_SAFE_CHAIN_ROOTS = Set.of(
      "System.out", "System.err", "java.lang.System.out", "java.lang.System.err");

  public RuleSet {
    fluentChainMethods = Set.copyOf(fluentChainMethods);
    safeChainRoots = Set.copyOf(safeChainRoots);
  }

  /**
   * Creates a rule set with configurable record handling and default behavior
   * for newer checks.
   */
  public RuleSet(
      int maxClassLines,
      int maxFieldsPerClass,
      boolean includeRecordComponentsInFieldRule,
      boolean forbidElse,
      int maxMethodNesting,
      boolean forbidGetters,
      boolean forbidSetters,
      boolean forbidNonFirstClassCollections
  ) {
    this(
        maxClassLines,
        maxFieldsPerClass,
        includeRecordComponentsInFieldRule,
        forbidElse,
        maxMethodNesting,
        forbidGetters,
        forbidSetters,
        forbidNonFirstClassCollections,
        false,
        false,
        Set.of(),
        DEFAULT_SAFE_CHAIN_ROOTS);
  }

  /**
   * Creates a rule set with the original seven settings. Record components
   * remain included and newer checks keep their default behavior.
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
        forbidNonFirstClassCollections,
        false,
        false,
        Set.of(),
        DEFAULT_SAFE_CHAIN_ROOTS);
  }

  public static RuleSet defaults() {
    return new RuleSet(50, 2, true, 1, true, true, false);
  }
}
