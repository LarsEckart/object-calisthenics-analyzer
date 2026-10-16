package com.larseckart.objectcalisthenics.analyzer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleSetTest {

  @Test
  public void preservesDefaultLimits() {
    RuleSet rules = RuleSet.defaults();
    assertThat(rules).extracting(RuleSet::maxClassLines, RuleSet::maxFieldsPerClass,
        RuleSet::maxMethodNesting).containsExactly(50, 2, 1);
    assertThat(RuleSet.builder().build()).isEqualTo(rules).hasSameHashCodeAs(rules);
  }

  @Test
  public void preservesDefaultPolicies() {
    RuleSet rules = RuleSet.defaults();
    assertThat(rules).extracting(RuleSet::includeRecordComponentsInFieldRule, RuleSet::forbidElse,
        RuleSet::forbidGetters, RuleSet::forbidSetters, RuleSet::forbidNonFirstClassCollections,
        RuleSet::forbidTraversalChains, RuleSet::strictGetterNames, RuleSet::oneTopLevelTypePerFile)
        .containsExactly(true, true, true, true, true, true, false, false);
  }

  @Test
  public void preservesDefaultChainExceptions() {
    RuleSet rules = RuleSet.defaults();
    assertThat(rules.fluentChainMethods()).isEmpty();
    assertThat(rules.safeChainRoots()).containsExactlyInAnyOrder(
        "System.out", "System.err", "java.lang.System.out", "java.lang.System.err");
  }

  @Test
  public void overridesNamedLimitsWithoutChangingDefaults() {
    RuleSet rules = RuleSet.builder().maxClassLines(80).maxFieldsPerClass(4).maxMethodNesting(3).build();
    assertThat(rules).extracting(RuleSet::maxClassLines, RuleSet::maxFieldsPerClass,
        RuleSet::maxMethodNesting).containsExactly(80, 4, 3);
    assertThat(RuleSet.defaults().maxClassLines()).isEqualTo(50);
  }

  @Test
  public void overridesEveryPolicyByName() {
    RuleSet rules = RuleSet.builder()
        .disable(Rule.INCLUDE_RECORD_COMPONENTS_IN_FIELD_RULE)
        .disable(Rule.FORBID_ELSE)
        .disable(Rule.FORBID_GETTERS)
        .disable(Rule.FORBID_SETTERS)
        .disable(Rule.FORBID_NON_FIRST_CLASS_COLLECTIONS)
        .disable(Rule.FORBID_TRAVERSAL_CHAINS)
        .enable(Rule.STRICT_GETTER_NAMES)
        .enable(Rule.ONE_TOP_LEVEL_TYPE_PER_FILE)
        .build();
    assertThat(rules).extracting(RuleSet::includeRecordComponentsInFieldRule, RuleSet::forbidElse,
        RuleSet::forbidGetters, RuleSet::forbidSetters, RuleSet::forbidNonFirstClassCollections,
        RuleSet::forbidTraversalChains, RuleSet::strictGetterNames, RuleSet::oneTopLevelTypePerFile)
        .containsExactly(false, false, false, false, false, false, true, true);
  }

  @Test
  public void snapshotsTheBuilderAtEachBuild() {
    RuleSet.Builder builder = RuleSet.builder();
    RuleSet first = builder.build();
    RuleSet second = builder.maxClassLines(80).enable(Rule.ONE_TOP_LEVEL_TYPE_PER_FILE).build();
    assertThat(first).isEqualTo(RuleSet.defaults());
    assertThat(second.maxClassLines()).isEqualTo(80);
    assertThat(second.oneTopLevelTypePerFile()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(Rule.class)
  public void canEnableAndDisableEveryRuleWithoutChangingExistingSnapshots(Rule rule) {
    RuleSet.Builder builder = RuleSet.builder();
    RuleSet enabled = builder.enable(rule).enable(rule).build();
    RuleSet disabled = builder.disable(rule).disable(rule).build();
    assertThat(enabled.enabled(rule)).isTrue();
    assertThat(disabled.enabled(rule)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(Rule.class)
  public void switchingOneRuleLeavesOtherDefaultsAlone(Rule rule) {
    RuleSet defaults = RuleSet.defaults();
    RuleSet changed = RuleSet.builder().disable(rule).build();
    for (Rule other : Rule.values()) {
      if (other != rule) {
        assertThat(changed.enabled(other)).isEqualTo(defaults.enabled(other));
      }
    }
  }

  @Test
  public void rejectsNullRules() {
    assertThatNullPointerException().isThrownBy(() -> RuleSet.builder().enable(null));
    assertThatNullPointerException().isThrownBy(() -> RuleSet.builder().disable(null));
    assertThatNullPointerException().isThrownBy(() -> RuleSet.defaults().enabled(null));
  }

  @Test
  public void buildersDoNotShareChanges() {
    RuleSet.builder().maxClassLines(80).build();
    assertThat(RuleSet.builder().build().maxClassLines()).isEqualTo(50);
  }

  @Test
  public void copiesCallerOwnedSetsWhenConfigured() {
    Set<String> methods = new HashSet<>(Set.of("map"));
    Set<String> roots = new HashSet<>(Set.of("Output"));
    RuleSet.Builder builder = RuleSet.builder().fluentChainMethods(methods).safeChainRoots(roots);
    methods.clear();
    roots.clear();
    RuleSet rules = builder.build();
    assertThat(rules.fluentChainMethods()).containsExactly("map");
    assertThat(rules.safeChainRoots()).containsExactly("Output");
  }

  @Test
  public void builderSetChangesDoNotReachExistingSnapshots() {
    RuleSet.Builder builder = RuleSet.builder().fluentChainMethods(Set.of("map")).safeChainRoots(Set.of("Output"));
    RuleSet rules = builder.build();
    builder.fluentChainMethods(Set.of("flatMap")).safeChainRoots(Set.of("Logger"));
    assertThat(rules.fluentChainMethods()).containsExactly("map");
    assertThat(rules.safeChainRoots()).containsExactly("Output");
  }

  @Test
  public void exposesReadOnlySets() {
    RuleSet rules = RuleSet.defaults();
    assertThatThrownBy(() -> rules.fluentChainMethods().add("map"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> rules.safeChainRoots().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void rejectsNullSetsAndElements() {
    assertThatNullPointerException().isThrownBy(() -> RuleSet.builder().fluentChainMethods(null));
    assertThatNullPointerException().isThrownBy(() -> RuleSet.builder().safeChainRoots(null));
    Set<String> invalid = new HashSet<>();
    invalid.add(null);
    assertThatNullPointerException().isThrownBy(() -> RuleSet.builder().fluentChainMethods(invalid));
    assertThatNullPointerException().isThrownBy(() -> RuleSet.builder().safeChainRoots(invalid));
  }

  @ParameterizedTest
  @MethodSource("singleOverrides")
  public void everySettingTakesPartInValueEquality(RuleSet rules) {
    assertThat(rules).isNotEqualTo(RuleSet.defaults());
    assertThat(RuleSet.defaults()).isNotEqualTo(rules);
  }

  private static Stream<RuleSet> singleOverrides() {
    return Stream.of(
        RuleSet.builder().maxClassLines(80).build(),
        RuleSet.builder().maxFieldsPerClass(4).build(),
        RuleSet.builder().maxMethodNesting(3).build(),
        RuleSet.builder().disable(Rule.INCLUDE_RECORD_COMPONENTS_IN_FIELD_RULE).build(),
        RuleSet.builder().disable(Rule.FORBID_ELSE).build(),
        RuleSet.builder().disable(Rule.FORBID_GETTERS).build(),
        RuleSet.builder().disable(Rule.FORBID_SETTERS).build(),
        RuleSet.builder().disable(Rule.FORBID_NON_FIRST_CLASS_COLLECTIONS).build(),
        RuleSet.builder().enable(Rule.STRICT_GETTER_NAMES).build(),
        RuleSet.builder().disable(Rule.FORBID_TRAVERSAL_CHAINS).build(),
        RuleSet.builder().fluentChainMethods(Set.of("map")).build(),
        RuleSet.builder().safeChainRoots(Set.of()).build(),
        RuleSet.builder().enable(Rule.ONE_TOP_LEVEL_TYPE_PER_FILE).build());
  }

  @Test
  public void comparesValuesRatherThanBuilderIdentity() {
    RuleSet first = RuleSet.builder().maxClassLines(80).fluentChainMethods(Set.of("map")).build();
    RuleSet equal = RuleSet.builder().maxClassLines(80).fluentChainMethods(Set.of("map")).build();
    assertThat(first).isEqualTo(equal).hasSameHashCodeAs(equal);
    assertThat(first).isNotEqualTo(RuleSet.defaults()).isNotEqualTo(null).isNotEqualTo("rules");
    assertThat(first.toString()).contains("maxClassLines=80", "fluentChainMethods=[map]");
  }
}
