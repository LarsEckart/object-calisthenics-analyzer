package com.github.larseckart.objectcalisthenics.analyzer;

import java.util.List;

/**
 * Source-specific evidence and guidance for a violation.
 *
 * @param kind stable identifier for the shape found in the source
 * @param summary concise description of that shape
 * @param relatedCode source elements relevant to the finding
 * @param suggestion a possible next step, subject to human judgement
 * @param caution why the suggestion may not fit the surrounding design
 */
public record ViolationContext(
    String kind,
    String summary,
    List<String> relatedCode,
    String suggestion,
    String caution
) {

  public ViolationContext {
    relatedCode = List.copyOf(relatedCode);
  }
}
