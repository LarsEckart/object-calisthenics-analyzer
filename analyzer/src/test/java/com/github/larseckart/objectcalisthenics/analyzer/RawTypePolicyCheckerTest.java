package com.github.larseckart.objectcalisthenics.analyzer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RawTypePolicyCheckerTest {

  @TempDir
  Path tempDir;

  @Test
  void reportsRawDeclarationsWhoseTypesAreNotAllowedByPolicy() throws IOException {
    Path source = writeSource("Customer.java", """
        class Customer {
          private String email;

          Customer(String email, boolean active) {
            this.email = email;
          }
        }
        """);
    PrimitiveObsessionConfig config = new PrimitiveObsessionConfig(
        -1, Set.of("boolean"), List.of());

    List<Violation> findings = rawTypeFindings(analyzer(config).analyze(List.of(source)));

    assertThat(findings)
        .extracting(Violation::subject)
        .containsExactly("email", "email");
    assertThat(findings)
        .allSatisfy(finding -> {
          assertThat(finding.rule()).isEqualTo("raw-domain-primitive");
          assertThat(finding.message()).contains("raw String").contains("not allowed by the configured policy");
          assertThat(finding.advice().caution()).contains("boundary");
        });
  }

  @Test
  void defaultConfigurationLeavesStrictRawTypePolicyDisabled() throws IOException {
    Path source = writeSource("Customer.java", """
        record Customer(String email, int age) {
        }
        """);

    List<Violation> findings = rawTypeFindings(
        analyzer(PrimitiveObsessionConfig.defaults()).analyze(List.of(source)));

    assertThat(findings).isEmpty();
  }

  private ObjectCalisthenicsAnalyzer analyzer(PrimitiveObsessionConfig config) {
    return new ObjectCalisthenicsAnalyzer(RuleSet.defaults(), config, List.of());
  }

  private List<Violation> rawTypeFindings(AnalysisResult result) {
    return result.violations().stream()
        .filter(violation -> violation.rule().equals("raw-domain-primitive"))
        .toList();
  }

  private Path writeSource(String fileName, String source) throws IOException {
    Path file = tempDir.resolve(fileName);
    Files.writeString(file, source);
    return file;
  }
}
