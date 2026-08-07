package com.github.larseckart.objectcalisthenics.gradle;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;

/**
 * DSL for configuring which rules to enforce and their thresholds.
 */
public abstract class ObjectCalisthenicsRules {

  @Input
  public abstract Property<Integer> getMaxClassLines();

  @Input
  public abstract Property<Integer> getMaxFieldsPerClass();

  @Input
  public abstract Property<Boolean> getForbidElse();

  @Input
  public abstract Property<Integer> getMaxMethodNesting();

  @Input
  public abstract Property<Boolean> getForbidGetters();

  @Input
  public abstract Property<Boolean> getForbidSetters();

  @Inject
  public ObjectCalisthenicsRules() {
    getMaxClassLines().convention(50);
    getMaxFieldsPerClass().convention(2);
    getForbidElse().convention(true);
    getMaxMethodNesting().convention(1);
    getForbidGetters().convention(true);
    getForbidSetters().convention(true);
  }
}
