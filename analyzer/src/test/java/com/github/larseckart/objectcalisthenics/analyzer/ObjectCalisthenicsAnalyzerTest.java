package com.github.larseckart.objectcalisthenics.analyzer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectCalisthenicsAnalyzerTest {

  private ObjectCalisthenicsAnalyzer analyzer;
  private Path samplesDir;

  @BeforeEach
  void setUp() throws URISyntaxException {
    analyzer = new ObjectCalisthenicsAnalyzer();
    samplesDir = Paths.get(getClass().getResource("/samples").toURI());
  }

  @Test
  void detectsClassThatIsTooLong() {
    AnalysisResult result = analyzer.analyze(samplesDir.resolve("TooLongClass.java").getParent());
    List<Violation> classTooLong = result.violations().stream()
        .filter(v -> v.rule().equals("class-too-long"))
        .toList();

    assertThat(classTooLong).hasSize(1);
    assertThat(classTooLong.get(0).message()).contains("TooLongClass");
  }

  @Test
  void detectsTooManyInstanceFields() {
    AnalysisResult result = analyzer.analyze(samplesDir.resolve("TooManyFieldsClass.java").getParent());
    List<Violation> fieldViolations = result.violations().stream()
        .filter(v -> v.rule().equals("too-many-instance-fields"))
        .toList();

    assertThat(fieldViolations).hasSize(1);
    assertThat(fieldViolations.get(0).message()).contains("TooManyFieldsClass");
  }

  @Test
  void detectsElseKeyword() {
    AnalysisResult result = analyzer.analyze(samplesDir.resolve("ElseMethod.java").getParent());

    assertThat(result.violations())
        .anyMatch(v -> v.rule().equals("else-used") && v.message().contains("sign"));
  }

  @Test
  void detectsDeeplyNestedMethod() {
    AnalysisResult result = analyzer.analyze(samplesDir.resolve("DeepNestingMethod.java").getParent());

    assertThat(result.violations())
        .anyMatch(v -> v.rule().equals("method-over-nested") && v.message().contains("handle"));
  }

  @Test
  void detectsGettersAndSetters() {
    AnalysisResult result = analyzer.analyze(samplesDir.resolve("GetterSetterClass.java").getParent());
    List<Violation> getterSetter = result.violations().stream()
        .filter(v -> v.rule().equals("getter") || v.rule().equals("setter"))
        .toList();

    assertThat(getterSetter).hasSize(2);
  }

  @Test
  void detectsNonFirstClassCollectionClass() {
    ObjectCalisthenicsAnalyzer analyzerWithCollectionRule = new ObjectCalisthenicsAnalyzer(
        new RuleSet(50, 2, true, 1, true, true, true));

    AnalysisResult result = analyzerWithCollectionRule.analyze(
        List.of(samplesDir.resolve("NonFirstClassCollectionClass.java")));

    assertThat(result.violations())
        .hasSize(1)
        .allMatch(v -> v.rule().equals("non-first-class-collection"));
  }

  @Test
  void detectsNonFirstClassArrayClass() {
    ObjectCalisthenicsAnalyzer analyzerWithCollectionRule = new ObjectCalisthenicsAnalyzer(
        new RuleSet(50, 2, true, 1, true, true, true));

    AnalysisResult result = analyzerWithCollectionRule.analyze(
        List.of(samplesDir.resolve("NonFirstClassArrayClass.java")));

    assertThat(result.violations())
        .hasSize(1)
        .allMatch(v -> v.rule().equals("non-first-class-collection"));
  }

  @Test
  void cleanFirstClassCollectionAndArrayClassesHaveNoCollectionViolation() {
    ObjectCalisthenicsAnalyzer analyzerWithCollectionRule = new ObjectCalisthenicsAnalyzer(
        new RuleSet(50, 2, true, 1, true, true, true));

    AnalysisResult result = analyzerWithCollectionRule.analyze(List.of(
        samplesDir.resolve("FirstClassCollectionClass.java"),
        samplesDir.resolve("FirstClassArrayClass.java")));

    assertThat(result.violations()).isEmpty();
  }

  @Test
  void cleanClassHasNoViolations() {
    Path cleanDir = samplesDir.resolve("../clean").normalize();
    AnalysisResult result = analyzer.analyze(cleanDir);

    assertThat(result.violations()).isEmpty();
  }
}
