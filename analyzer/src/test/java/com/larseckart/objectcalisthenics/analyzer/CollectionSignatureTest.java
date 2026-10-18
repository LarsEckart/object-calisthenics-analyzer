package com.larseckart.objectcalisthenics.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import com.larseckart.objectcalisthenics.analyzer.checks.CompilationUnitChecks;
import com.larseckart.objectcalisthenics.analyzer.parsing.JavaSourceParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollectionSignatureTest {

  @TempDir Path sourceRoot;

  @Test
  public void reportsBothSidesOfTheTournamentSignature() throws IOException {
    List<Violation> findings =
        findings(
            """
                class ConfigureSearch {
                  public java.util.List<TournamentEvent> events(
                      java.util.Map<String, String> env, boolean scheduled) throws Exception { return null; }
                }
                """);
    assertThat(findings).extracting(Violation::line).containsExactly(2, 3);
    assertThat(findings).extracting(Violation::subject).containsOnly("ConfigureSearch.events");
    String returned =
        "ConfigureSearch.events returns bare collection type java.util.List<TournamentEvent>";
    String accepted =
        "ConfigureSearch.events accepts bare collection parameter 'env' of type java.util.Map<String, String>";
    assertThat(findings)
        .extracting(finding -> finding.message().split(";", 2)[0])
        .containsExactly(returned, accepted);
  }

  @Test
  public void providesCollectionDesignAdvice() {
    assertThat(Advice.forRule("unwrapped-collection").principle()).contains("collection");
  }

  @Test
  public void includesPrivateAbstractInterfaceAndNestedMethods() throws IOException {
    List<Violation> findings =
        findings(
            """
                abstract class Example {
                  private void helper(java.util.Set<String> names) {}
                  abstract java.util.Collection<String> values();
                  interface Nested { java.util.Map<String, String> read(); }
                }
                """);
    assertThat(findings)
        .extracting(Violation::subject)
        .containsExactly("Example.helper", "Example.values", "Nested.read");
  }

  @Test
  public void recognizesArraysVarargsAndCommonImplementations() throws IOException {
    List<Violation> findings =
        findings(
            """
                class Example {
                  int[] numbers(String... names) { return null; }
                  java.util.HashMap<String, String> read(java.util.ArrayList<String> lines) { return null; }
                }
                """);
    assertThat(findings).hasSize(4);
  }

  @Test
  public void exemptsWrapperConstructorsButNotTheirMethods() throws IOException {
    List<Violation> findings =
        findings(
            """
                class Environment {
                  static int ignored;
                  private java.util.Map<String, String> values;
                  Environment(java.util.Map<String, String> values) { this.values = values; }
                  java.util.Map<String, String> values() { return values; }
                }
                record Events(java.util.List<String> values) {
                  Events(java.util.List<String> values) { this.values = values; }
                }
                """);
    assertThat(findings).extracting(Violation::subject).containsExactly("Environment.values");
  }

  @Test
  public void checksNonWrapperConstructors() throws IOException {
    List<Violation> findings =
        findings(
            """
                class Example {
                  Example(java.util.List<String> lines) {}
                }
                """);
    assertThat(findings).extracting(Violation::subject).containsExactly("Example.Example");
  }

  @Test
  public void honorsMethodConstructorAndTypeSuppressions() throws IOException {
    List<Violation> findings =
        findings(
            """
                class Example {
                  @SuppressWarnings({"unused", "calisthenics:unwrapped-collection"})
                  java.util.List<String> values(java.util.Map<String, String> values) { return null; }
                  @SuppressWarnings("calisthenics:unwrapped-collection")
                  Example(String[] values) {}
                  java.util.List<String> unsuppressed() { return null; }
                }
                @SuppressWarnings("calisthenics:unwrapped-collection")
                interface Boundary { java.util.List<String> values(); }
                """);
    assertThat(findings).extracting(Violation::subject).containsExactly("Example.unsuppressed");
  }

  @Test
  public void leavesDomainTypesPrimitivesAndNestedGenericArgumentsAlone() throws IOException {
    List<Violation> findings =
        findings(
            """
                class Example {
                  Environment read(java.nio.file.Path path) { return null; }
                  String required(String key) { return key; }
                  boolean empty() { return true; }
                  java.util.Optional<java.util.List<String>> optional() { return null; }
                }
                record Environment(java.util.Map<String, String> values) {}
                """);
    assertThat(findings).isEmpty();
  }

  @Test
  public void honorsExistingRuleSwitchAndClassExclusions() throws IOException {
    Path file = source("class Example { java.util.List<String> values() { return null; } }");
    RuleSet disabled = RuleSet.builder().disable(Rule.FORBID_NON_FIRST_CLASS_COLLECTIONS).build();
    assertThat(
            signatureFindings(
                new ObjectCalisthenicsAnalyzer(
                    new JavaSourceParser(),
                    new CompilationUnitChecks(
                        disabled, PrimitiveObsessionConfig.defaults(), List.of())),
                file))
        .isEmpty();
    assertThat(
            signatureFindings(
                new ObjectCalisthenicsAnalyzer(
                    new JavaSourceParser(),
                    new CompilationUnitChecks(
                        RuleSet.defaults(),
                        PrimitiveObsessionConfig.defaults(),
                        List.of("Example"))),
                file))
        .isEmpty();
  }

  private List<Violation> findings(String code) throws IOException {
    return signatureFindings(new ObjectCalisthenicsAnalyzer(), source(code));
  }

  private List<Violation> signatureFindings(ObjectCalisthenicsAnalyzer analyzer, Path file) {
    return analyzer.analyze(List.of(file)).violations().stream()
        .filter(violation -> violation.rule().equals("unwrapped-collection"))
        .toList();
  }

  private Path source(String code) throws IOException {
    return Files.writeString(sourceRoot.resolve("Example.java"), code);
  }
}
