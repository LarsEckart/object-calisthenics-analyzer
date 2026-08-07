package com.github.larseckart.objectcalisthenics.analyzer;

import java.nio.file.Path;

/**
 * A single Object Calisthenics violation found in source code.
 *
 * @param file   source file containing the violation
 * @param line   1-based line number
 * @param rule   short rule identifier, e.g. "class-too-long"
 * @param message human-readable explanation
 */
public record Violation(Path file, int line, String rule, String message) {
}
