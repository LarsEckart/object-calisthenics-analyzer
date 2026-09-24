package com.github.larseckart.objectcalisthenics.analyzer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The outcome of an analysis run.
 */
public record AnalysisResult(List<Violation> violations, List<ExcludedClass> excludedClasses) {

  public AnalysisResult {
    violations = List.copyOf(violations);
    excludedClasses = List.copyOf(excludedClasses);
  }

  public AnalysisResult(List<Violation> violations) {
    this(violations, List.of());
  }

  public int totalViolations() {
    return violations.size();
  }

  public Map<String, Long> countsByRule() {
    return violations.stream()
        .collect(Collectors.groupingBy(Violation::rule, Collectors.counting()));
  }
}
