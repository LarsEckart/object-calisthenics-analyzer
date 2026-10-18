package com.larseckart.objectcalisthenics.gradle.configuration;

import java.util.List;
import javax.inject.Inject;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;

/** DSL for excluding classes and records from every Object Calisthenics check. */
public abstract class ObjectCalisthenicsExclusions {

  /** Regular expressions matched against the simple name of each class or record. */
  @Input
  public abstract ListProperty<String> getClassNamePatterns();

  @Inject
  public ObjectCalisthenicsExclusions() {
    getClassNamePatterns().convention(List.of());
  }
}
