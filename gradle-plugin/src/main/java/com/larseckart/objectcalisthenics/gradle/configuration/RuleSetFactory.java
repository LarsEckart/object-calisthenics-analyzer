package com.larseckart.objectcalisthenics.gradle.configuration;

import com.larseckart.objectcalisthenics.analyzer.Rule;
import com.larseckart.objectcalisthenics.analyzer.RuleSet;

public final class RuleSetFactory {

  private RuleSetFactory() {
    // utility class
  }

  public static RuleSet from(ObjectCalisthenicsRules rules) {
    RuleSet.Builder builder = RuleSet.builder()
        .maxClassLines(rules.getMaxClassLines().get())
        .maxFieldsPerClass(rules.getMaxFieldsPerClass().get())
        .maxMethodNesting(rules.getMaxMethodNesting().get())
        .fluentChainMethods(rules.getFluentChainMethods().get())
        .safeChainRoots(rules.getSafeChainRoots().get());
    configure(builder, Rule.INCLUDE_RECORD_COMPONENTS_IN_FIELD_RULE, rules.getIncludeRecordComponentsInFieldRule().get());
    configure(builder, Rule.FORBID_ELSE, rules.getForbidElse().get());
    configure(builder, Rule.FORBID_GETTERS, rules.getForbidGetters().get());
    configure(builder, Rule.FORBID_SETTERS, rules.getForbidSetters().get());
    configure(builder, Rule.FORBID_NON_FIRST_CLASS_COLLECTIONS, rules.getForbidNonFirstClassCollections().get());
    configure(builder, Rule.STRICT_GETTER_NAMES, rules.getStrictGetterNames().get());
    configure(builder, Rule.FORBID_TRAVERSAL_CHAINS, rules.getForbidTraversalChains().get());
    configure(builder, Rule.ONE_TOP_LEVEL_TYPE_PER_FILE, rules.getOneTopLevelTypePerFile().get());
    return builder.build();
  }

  // Gradle keeps its boolean properties; only this adapter translates them to rule switches.
  private static void configure(RuleSet.Builder builder, Rule rule, boolean enabled) {
    if (enabled) {
      builder.enable(rule);
      return;
    }
    builder.disable(rule);
  }
}
