package com.github.larseckart.objectcalisthenics.analyzer;

import java.nio.file.Path;

/**
 * A single Object Calisthenics violation found in source code.
 *
 * @param file   source file containing the violation
 * @param line   1-based line number
 * @param rule   short rule identifier, e.g. "class-too-long"
 * @param subject class or method that violates the rule
 * @param message human-readable explanation
 */
public record Violation(Path file, int line, String rule, String subject, String message) {

  /**
   * Creates a violation without a baseline subject.
   *
   * @deprecated supply the class or method name so the violation can be baselined
   */
  @Deprecated
  public Violation(Path file, int line, String rule, String message) {
    this(file, line, rule, "", message);
  }

  /**
   * Returns guidance for improving the design behind this violation.
   */
  public Advice advice() {
    return Advice.forRule(rule);
  }
}
