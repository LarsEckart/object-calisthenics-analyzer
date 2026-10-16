package com.larseckart.objectcalisthenics.analyzer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopLevelTypesTest {

  @TempDir
  Path sourceRoot;

  @Test
  public void staysOffByDefaultAndForAnUntouchedBuilder() throws IOException {
    Path file = source("Main.java", "class Main {} class Helper {}");
    assertThat(new ObjectCalisthenicsAnalyzer().analyze(List.of(file)).violations()).isEmpty();
    assertThat(RuleSet.builder().build().oneTopLevelTypePerFile()).isFalse();
  }

  @Test
  public void reportsEveryExtraTypeWithItsNameAndLine() throws IOException {
    Path file = source("Main.java", """
        class Main {}
        class Helper {}
        record Value() {}
        enum Kind { ONLY }
        interface Role {}
        @interface Marker {}
        """);

    List<Violation> findings = analyzer().analyze(List.of(file)).violations();

    assertThat(findings).extracting(Violation::subject)
        .containsExactly("Helper", "Value", "Kind", "Role", "Marker");
    assertThat(findings).extracting(Violation::line).containsExactly(2, 3, 4, 5, 6);
    assertThat(findings).allSatisfy(finding -> {
      assertThat(finding.file()).isEqualTo(file);
      assertThat(finding.rule()).isEqualTo("multiple-top-level-types");
      assertThat(finding.message()).contains("own source file");
      assertThat(finding.advice().principle()).contains("source file");
    });
  }

  @Test
  public void keepsTheFileNamedTypeEvenWhenItComesLast() throws IOException {
    source("Main.java", "class Helper {}\nclass Main {}");
    assertThat(analyzer().analyze(sourceRoot).violations())
        .extracting(Violation::subject).containsExactly("Helper");
  }

  @Test
  public void keepsFirstTypeWhenNoNameMatches() throws IOException {
    Path file = source("Other.java", "class First {}\nclass Second {}");
    assertThat(analyzer().analyze(List.of(file)).violations())
        .extracting(Violation::subject).containsExactly("Second");
  }

  @Test
  public void ignoresNestedLocalAndAnonymousTypes() throws IOException {
    Path file = source("Main.java", """
        class Main {
          static class Nested {}
          void work() { class Local {} }
          Runnable task = new Runnable() { public void run() {} };
        }
        """);
    assertThat(analyzer().analyze(List.of(file)).violations()).isEmpty();
  }

  @Test
  public void acceptsFilesWithoutTypes() throws IOException {
    Path file = source("package-info.java", "package example;");
    assertThat(analyzer().analyze(List.of(file)).violations()).isEmpty();
  }

  @Test
  public void honorsClassExclusionsWithoutChangingThePrimaryType() throws IOException {
    Path file = source("Main.java", "class Main {}\nclass Ignored {}\nclass Helper {}");
    AnalysisResult result = new ObjectCalisthenicsAnalyzer(rules(), List.of("Ignored"))
        .analyze(List.of(file));
    assertThat(result.violations()).extracting(Violation::subject).containsExactly("Helper");
    assertThat(result.excludedClasses()).extracting(ExcludedClass::className).containsExactly("Ignored");
  }

  private ObjectCalisthenicsAnalyzer analyzer() {
    return new ObjectCalisthenicsAnalyzer(rules());
  }

  private RuleSet rules() {
    return RuleSet.builder().enable(Rule.ONE_TOP_LEVEL_TYPE_PER_FILE).build();
  }

  private Path source(String name, String content) throws IOException {
    return Files.writeString(sourceRoot.resolve(name), content);
  }
}
