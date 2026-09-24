package com.github.larseckart.objectcalisthenics.analyzer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectCalisthenicsAnalyzerTest {

  private ObjectCalisthenicsAnalyzer analyzer;
  private Path samplesDir;

  @TempDir
  Path tempDir;

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
    assertThat(classTooLong.get(0).subject()).isEqualTo("TooLongClass");
    assertThat(classTooLong.get(0).message()).contains("TooLongClass");
  }

  @Test
  void givesRuleSpecificAdvice() {
    AnalysisResult result = analyzer.analyze(List.of(samplesDir.resolve("ElseMethod.java")));

    Violation violation = result.violations().stream()
        .filter(v -> v.rule().equals("else-used"))
        .findFirst()
        .orElseThrow();

    assertThat(violation.advice().principle()).contains("conditional logic");
    assertThat(violation.advice().options()).anyMatch(option -> option.contains("guard clause"));
    assertThat(violation.advice().caution()).contains("domain behaviour");
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
  void detectsTooManyRecordComponentsByDefault() {
    AnalysisResult result = analyzer.analyze(List.of(
        samplesDir.resolve("../field-rule/TooManyComponentsRecord.java").normalize()));

    assertThat(result.violations())
        .singleElement()
        .matches(v -> v.rule().equals("too-many-record-components"));
  }

  @Test
  void canExcludeRecordComponentsFromFieldRule() {
    ObjectCalisthenicsAnalyzer analyzerWithoutRecordComponents = new ObjectCalisthenicsAnalyzer(
        new RuleSet(50, 2, false, true, 1, true, true, false));

    AnalysisResult result = analyzerWithoutRecordComponents.analyze(List.of(
        samplesDir.resolve("../field-rule/TooManyComponentsRecord.java").normalize(),
        samplesDir.resolve("TooManyFieldsClass.java")));

    assertThat(result.violations())
        .singleElement()
        .matches(v -> v.rule().equals("too-many-instance-fields"));
  }

  @Test
  void suppressesFieldRulePerType() {
    AnalysisResult result = analyzer.analyze(List.of(
        samplesDir.resolve("../field-rule/SuppressedFieldsClass.java").normalize(),
        samplesDir.resolve("../field-rule/SuppressedComponentsRecord.java").normalize()));

    assertThat(result.violations()).isEmpty();
  }

  @Test
  void detectsElseKeyword() {
    AnalysisResult result = analyzer.analyze(samplesDir.resolve("ElseMethod.java").getParent());

    assertThat(result.violations())
        .anyMatch(v -> v.rule().equals("else-used") && v.subject().equals("sign"));
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
  void getterRuleAllowsComputedQueriesWithoutBackingState() throws IOException {
    Path source = writeSource("Queries.java", """
        class Queries {
          private String name;
          private String URL;

          public boolean isIdle() { return calculateIdle(); }
          public boolean isZero() { return x() == 0; }
          public int getTotal() { return subtotal() + tax(); }
          public String getName() { return name; }
          public String getAlias() { return (this.name); }
          public String getURL() { return URL.toLowerCase(); }
          public String getNested() { log(); return name; }
        }

        record Status(boolean active) {
          public boolean isActive() { return !active; }
        }
        """);

    List<String> getters = analyzer.analyze(List.of(source)).violations().stream()
        .filter(violation -> violation.rule().equals("getter"))
        .map(Violation::message)
        .toList();

    assertThat(getters).containsExactlyInAnyOrder(
        "Method 'getName' looks like a getter",
        "Method 'getAlias' looks like a getter",
        "Method 'getURL' looks like a getter",
        "Method 'isActive' looks like a getter");
  }

  @Test
  void strictGetterNamesRestoresNameOnlyMatching() throws IOException {
    Path source = writeSource("ComputedQuery.java", """
        class ComputedQuery {
          public boolean isIdle() { return velocity().isZero(); }
        }
        """);
    ObjectCalisthenicsAnalyzer strictAnalyzer = new ObjectCalisthenicsAnalyzer(new RuleSet(
        50, 2, true, true, 1, true, true, false,
        true, false, Set.of(), Set.of()));

    assertThat(strictAnalyzer.analyze(List.of(source)).violations())
        .anyMatch(violation -> violation.rule().equals("getter"));
  }

  @Test
  void detectsReceiverTraversalButAllowsIndependentAndConfiguredFluentCalls() throws IOException {
    Path source = writeSource("Chains.java", """
        class Chains {
          private Service service;

          Chains() {
            customer().address();
            service.execute();
            this.service.execute();
            a.b(c.d());
            a.b(c.d().e());
            System.out.println(customer().address());
            vector.minus(other).times(scale).dot(axis);
            ball.position().minus(other);
          }
        }
        """);
    ObjectCalisthenicsAnalyzer chainAnalyzer = new ObjectCalisthenicsAnalyzer(new RuleSet(
        50, 2, true, true, 1, true, true, false,
        false, true, Set.of("minus", "times"),
        Set.of("System.out", "System.err", "java.lang.System.out", "java.lang.System.err")));

    List<Violation> chains = chainAnalyzer.analyze(List.of(source)).violations().stream()
        .filter(violation -> violation.rule().equals("traversal-chain"))
        .toList();

    assertThat(chains).hasSize(4);
    assertThat(chains).extracting(Violation::message).containsExactlyInAnyOrder(
        "Traversal chain has 2 steps: customer().address()",
        "Traversal chain has 2 steps: c.d().e()",
        "Traversal chain has 2 steps: customer().address()",
        "Traversal chain has 2 steps: ball.position().minus(other)");
    assertThat(chains).extracting(Violation::line).contains(5, 9, 10, 12);
    assertThat(chains.get(0).advice().principle()).contains("immediate collaborator");
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

  private Path writeSource(String fileName, String source) throws IOException {
    Path file = tempDir.resolve(fileName);
    Files.writeString(file, source);
    return file;
  }
}
