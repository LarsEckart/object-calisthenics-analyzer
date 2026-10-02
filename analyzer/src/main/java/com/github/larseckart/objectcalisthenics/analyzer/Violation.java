package com.github.larseckart.objectcalisthenics.analyzer;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A single Object Calisthenics violation found in source code.
 *
 * @param file   source file containing the violation
 * @param line   1-based line number
 * @param rule   short rule identifier, e.g. "class-too-long"
 * @param details human-readable details about the violation
 */
public record Violation(
    Path file,
    int line,
    String rule,
    Details details
) {

  public Violation(Path file, int line, String rule, String subject, String message) {
    this(file, line, rule, new Details(subject, message, Optional.empty()));
  }

  /** Returns a copy enriched with source-specific context. */
  public Violation withContext(ViolationContext context) {
    return new Violation(file, line, rule, new Details(subject(), message(), Optional.of(context)));
  }

  public String subject() {
    return details.subject();
  }

  public String message() {
    return details.message();
  }

  public Optional<ViolationContext> context() {
    return details.context();
  }

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

  /** Human-readable details and optional source context for a violation. */
  public record Details(
      String subject,
      String message,
      Optional<ViolationContext> context
  ) {
  }
}
