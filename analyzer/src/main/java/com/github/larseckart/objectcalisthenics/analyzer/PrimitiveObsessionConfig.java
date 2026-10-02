package com.github.larseckart.objectcalisthenics.analyzer;

import java.util.List;
import java.util.Set;

/** Configuration for raw domain types and the scored primitive-obsession heuristic. */
public record PrimitiveObsessionConfig(
    int threshold,
    Set<String> allowedRawTypes,
    List<String> boundaryClassNamePatterns
) {

  private static final Set<String> ALL_RAW_TYPES = Set.of(
      "boolean", "byte", "char", "double", "float", "int", "long", "short",
      "Boolean", "Byte", "Character", "Double", "Float", "Integer", "Long", "Short",
      "String");

  public PrimitiveObsessionConfig {
    allowedRawTypes = Set.copyOf(allowedRawTypes);
    boundaryClassNamePatterns = List.copyOf(boundaryClassNamePatterns);
  }

  /** Preserves the original heuristic-only configuration API. */
  public PrimitiveObsessionConfig(int threshold) {
    this(threshold, ALL_RAW_TYPES, List.of());
  }

  /** Returns the default configuration. */
  public static PrimitiveObsessionConfig defaults() {
    return new PrimitiveObsessionConfig(5);
  }
}
