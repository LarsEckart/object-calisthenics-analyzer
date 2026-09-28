package com.github.larseckart.objectcalisthenics.analyzer;

/** Configuration for the scored primitive-obsession heuristic. */
public record PrimitiveObsessionConfig(int threshold) {

  /** Returns the default configuration. */
  public static PrimitiveObsessionConfig defaults() {
    return new PrimitiveObsessionConfig(5);
  }
}
