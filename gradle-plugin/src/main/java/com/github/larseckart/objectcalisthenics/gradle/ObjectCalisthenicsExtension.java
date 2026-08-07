package com.github.larseckart.objectcalisthenics.gradle;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;

/**
 * Gradle DSL extension for the Object Calisthenics plugin.
 */
public class ObjectCalisthenicsExtension {

  private final Property<SourceSet> sourceSet;
  private final ObjectCalisthenicsRules rules;
  private final ObjectCalisthenicsReports reports;

  @Inject
  public ObjectCalisthenicsExtension(ObjectFactory objects) {
    this.sourceSet = objects.property(SourceSet.class);
    this.rules = objects.newInstance(ObjectCalisthenicsRules.class);
    this.reports = objects.newInstance(ObjectCalisthenicsReports.class);
  }

  public Property<SourceSet> getSourceSet() {
    return sourceSet;
  }

  public ObjectCalisthenicsRules getRules() {
    return rules;
  }

  public ObjectCalisthenicsReports getReports() {
    return reports;
  }

  public void rules(Action<? super ObjectCalisthenicsRules> action) {
    action.execute(rules);
  }

  public void reports(Action<? super ObjectCalisthenicsReports> action) {
    action.execute(reports);
  }
}
