package com.github.larseckart.objectcalisthenics.gradle;

import com.github.larseckart.objectcalisthenics.analyzer.RuleSet;

final class RuleSetFactory {

  private RuleSetFactory() {
    // utility class
  }

  static RuleSet from(ObjectCalisthenicsRules rules) {
    return new RuleSet(
        rules.getMaxClassLines().get(),
        rules.getMaxFieldsPerClass().get(),
        rules.getIncludeRecordComponentsInFieldRule().get(),
        rules.getForbidElse().get(),
        rules.getMaxMethodNesting().get(),
        rules.getForbidGetters().get(),
        rules.getForbidSetters().get(),
        rules.getForbidNonFirstClassCollections().get()
    );
  }
}
