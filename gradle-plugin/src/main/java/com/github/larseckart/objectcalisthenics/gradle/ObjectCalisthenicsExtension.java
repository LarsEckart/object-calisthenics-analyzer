package com.github.larseckart.objectcalisthenics.gradle;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;

/**
 * Gradle DSL extension for the Object Calisthenics plugin.
 */
public class ObjectCalisthenicsExtension {

  private final Property<SourceSet> sourceSet;
  private final RegularFileProperty baselineFile;
  private final Property<Boolean> ignoreFailures;
  private final ObjectCalisthenicsRules rules;
  private final ObjectCalisthenicsExclusions exclusions;
  private final ObjectCalisthenicsReports reports;

  @Inject
  public ObjectCalisthenicsExtension(ObjectFactory objects) {
    this.sourceSet = objects.property(SourceSet.class);
    this.baselineFile = objects.fileProperty();
    this.ignoreFailures = objects.property(Boolean.class).convention(false);
    this.rules = objects.newInstance(ObjectCalisthenicsRules.class);
    this.exclusions = objects.newInstance(ObjectCalisthenicsExclusions.class);
    this.reports = objects.newInstance(ObjectCalisthenicsReports.class);
  }

  public Property<SourceSet> getSourceSet() {
    return sourceSet;
  }

  public RegularFileProperty getBaselineFile() {
    return baselineFile;
  }

  public Property<Boolean> getIgnoreFailures() {
    return ignoreFailures;
  }

  public ObjectCalisthenicsRules getRules() {
    return rules;
  }

  public ObjectCalisthenicsExclusions getExclusions() {
    return exclusions;
  }

  public ObjectCalisthenicsReports getReports() {
    return reports;
  }

  public void rules(Action<? super ObjectCalisthenicsRules> action) {
    action.execute(rules);
  }

  public void exclusions(Action<? super ObjectCalisthenicsExclusions> action) {
    action.execute(exclusions);
  }

  public void reports(Action<? super ObjectCalisthenicsReports> action) {
    action.execute(reports);
  }
}
