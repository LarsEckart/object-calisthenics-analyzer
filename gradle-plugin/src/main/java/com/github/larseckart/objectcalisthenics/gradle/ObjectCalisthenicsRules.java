package com.github.larseckart.objectcalisthenics.gradle;

import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.Set;

/**
 * DSL for configuring which rules to enforce and their thresholds.
 */
public abstract class ObjectCalisthenicsRules {

  @Input
  public abstract Property<Integer> getMaxClassLines();

  @Input
  public abstract Property<Integer> getMaxFieldsPerClass();

  @Input
  public abstract Property<Boolean> getIncludeRecordComponentsInFieldRule();

  @Input
  public abstract Property<Boolean> getForbidElse();

  @Input
  public abstract Property<Integer> getMaxMethodNesting();

  @Input
  public abstract Property<Boolean> getForbidGetters();

  @Input
  public abstract Property<Boolean> getForbidSetters();

  @Input
  public abstract Property<Boolean> getForbidNonFirstClassCollections();

  @Input
  public abstract Property<Boolean> getStrictGetterNames();

  @Input
  public abstract Property<Boolean> getForbidTraversalChains();

  @Input
  public abstract SetProperty<String> getFluentChainMethods();

  @Input
  public abstract SetProperty<String> getSafeChainRoots();

  @Inject
  public ObjectCalisthenicsRules() {
    getMaxClassLines().convention(50);
    getMaxFieldsPerClass().convention(2);
    getIncludeRecordComponentsInFieldRule().convention(true);
    getForbidElse().convention(true);
    getMaxMethodNesting().convention(1);
    getForbidGetters().convention(true);
    getForbidSetters().convention(true);
    getForbidNonFirstClassCollections().convention(false);
    getStrictGetterNames().convention(false);
    getForbidTraversalChains().convention(false);
    getFluentChainMethods().convention(Set.of());
    getSafeChainRoots().convention(Set.of(
        "System.out", "System.err", "java.lang.System.out", "java.lang.System.err"));
  }
}
