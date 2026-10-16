package com.github.larseckart.objectcalisthenics.analyzer;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable settings for the analyzer's rules.
 * Use {@link #defaults()} or override named settings through {@link #builder()}.
 */
public final class RuleSet {

  private final int maxClassLines;
  private final int maxFieldsPerClass;
  private final int maxMethodNesting;
  private final Set<Rule> enabledRules;
  private final Set<String> fluentChainMethods;
  private final Set<String> safeChainRoots;

  private RuleSet(Builder builder) {
    maxClassLines = builder.maxClassLines;
    maxFieldsPerClass = builder.maxFieldsPerClass;
    maxMethodNesting = builder.maxMethodNesting;
    enabledRules = Set.copyOf(builder.enabledRules);
    fluentChainMethods = Set.copyOf(builder.fluentChainMethods);
    safeChainRoots = Set.copyOf(builder.safeChainRoots);
  }

  /** Returns the standard Object Calisthenics settings. */
  public static RuleSet defaults() {
    return builder().build();
  }

  /** Starts a builder with the same settings as {@link #defaults()}. */
  public static Builder builder() {
    return new Builder();
  }

  /** Reports whether the given switch is enabled in this snapshot. */
  public boolean enabled(Rule rule) {
    return enabledRules.contains(Objects.requireNonNull(rule));
  }

  public int maxClassLines() {
    return maxClassLines;
  }

  public int maxFieldsPerClass() {
    return maxFieldsPerClass;
  }

  public int maxMethodNesting() {
    return maxMethodNesting;
  }

  public boolean includeRecordComponentsInFieldRule() {
    return enabled(Rule.INCLUDE_RECORD_COMPONENTS_IN_FIELD_RULE);
  }

  public boolean forbidElse() {
    return enabled(Rule.FORBID_ELSE);
  }

  public boolean forbidGetters() {
    return enabled(Rule.FORBID_GETTERS);
  }

  public boolean forbidSetters() {
    return enabled(Rule.FORBID_SETTERS);
  }

  public boolean forbidNonFirstClassCollections() {
    return enabled(Rule.FORBID_NON_FIRST_CLASS_COLLECTIONS);
  }

  public boolean strictGetterNames() {
    return enabled(Rule.STRICT_GETTER_NAMES);
  }

  public boolean forbidTraversalChains() {
    return enabled(Rule.FORBID_TRAVERSAL_CHAINS);
  }

  public boolean oneTopLevelTypePerFile() {
    return enabled(Rule.ONE_TOP_LEVEL_TYPE_PER_FILE);
  }

  public Set<String> fluentChainMethods() {
    return fluentChainMethods;
  }

  public Set<String> safeChainRoots() {
    return safeChainRoots;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof RuleSet that)) {
      return false;
    }
    return maxClassLines == that.maxClassLines && maxFieldsPerClass == that.maxFieldsPerClass
        && maxMethodNesting == that.maxMethodNesting && enabledRules.equals(that.enabledRules)
        && fluentChainMethods.equals(that.fluentChainMethods) && safeChainRoots.equals(that.safeChainRoots);
  }

  @Override
  public int hashCode() {
    return Objects.hash(maxClassLines, maxFieldsPerClass, maxMethodNesting,
        enabledRules, fluentChainMethods, safeChainRoots);
  }

  @Override
  public String toString() {
    return "RuleSet[maxClassLines=" + maxClassLines + ", maxFieldsPerClass=" + maxFieldsPerClass
        + ", maxMethodNesting=" + maxMethodNesting + ", enabledRules=" + enabledRules
        + ", fluentChainMethods=" + fluentChainMethods + ", safeChainRoots=" + safeChainRoots + "]";
  }

  /** Collects named overrides; each build creates an independent immutable value. */
  public static final class Builder {

    private int maxClassLines = 50;
    private int maxFieldsPerClass = 2;
    private int maxMethodNesting = 1;
    private final EnumSet<Rule> enabledRules = EnumSet.of(
        Rule.INCLUDE_RECORD_COMPONENTS_IN_FIELD_RULE, Rule.FORBID_ELSE,
        Rule.FORBID_GETTERS, Rule.FORBID_SETTERS, Rule.FORBID_NON_FIRST_CLASS_COLLECTIONS,
        Rule.FORBID_TRAVERSAL_CHAINS);
    private Set<String> fluentChainMethods = Set.of();
    private Set<String> safeChainRoots = Set.of(
        "System.out", "System.err", "java.lang.System.out", "java.lang.System.err");

    private Builder() {}

    public Builder maxClassLines(int value) {
      maxClassLines = value;
      return this;
    }

    public Builder maxFieldsPerClass(int value) {
      maxFieldsPerClass = value;
      return this;
    }

    public Builder maxMethodNesting(int value) {
      maxMethodNesting = value;
      return this;
    }

    /** Enables a rule without changing any other switches or limits. */
    public Builder enable(Rule rule) {
      enabledRules.add(Objects.requireNonNull(rule));
      return this;
    }

    /** Disables a rule without changing any other switches or limits. */
    public Builder disable(Rule rule) {
      enabledRules.remove(Objects.requireNonNull(rule));
      return this;
    }

    public Builder fluentChainMethods(Set<String> value) {
      fluentChainMethods = Set.copyOf(value);
      return this;
    }

    public Builder safeChainRoots(Set<String> value) {
      safeChainRoots = Set.copyOf(value);
      return this;
    }

    /** Copies the current settings without keeping a link to this builder. */
    public RuleSet build() {
      return new RuleSet(this);
    }
  }
}
