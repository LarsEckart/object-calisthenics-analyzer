package com.github.larseckart.objectcalisthenics.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * DSL for configuring where reports are written and whether they are printed.
 */
public abstract class ObjectCalisthenicsReports {

  public abstract RegularFileProperty getJson();

  public abstract Property<Boolean> getConsoleSummary();

  @Inject
  public ObjectCalisthenicsReports() {
    getConsoleSummary().convention(true);
  }
}
