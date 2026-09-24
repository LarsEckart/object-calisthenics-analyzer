package com.github.larseckart.objectcalisthenics.analyzer;

import java.nio.file.Path;
import java.util.List;

/**
 * A class or record skipped because its simple name matched one or more
 * configured exclusion patterns.
 */
public record ExcludedClass(Path file, String className, List<String> matchedPatterns) {

  public ExcludedClass {
    matchedPatterns = List.copyOf(matchedPatterns);
  }
}
