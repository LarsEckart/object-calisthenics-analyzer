package com.github.larseckart.objectcalisthenics.gradle;

import com.github.larseckart.objectcalisthenics.analyzer.AnalysisResult;
import com.github.larseckart.objectcalisthenics.analyzer.Violation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsPrinterTest {

  @TempDir
  Path projectDirectory;

  @Test
  void printsSortedRelativeFindingsAndAdviceOncePerRule() {
    AnalysisResult result = new AnalysisResult(List.of(
        violation("src/main/java/Zebra.java", 20, "too-many-instance-fields", "late", "late finding"),
        violation("src/main/java/Zebra.java", 3, "too-many-instance-fields", "early", "early finding"),
        violation("src/main/java/Branch.java", 20, "else-used", "choose", "choose uses else"),
        violation("src/main/java/Apple.java", 12, "too-many-instance-fields", "Apple", "Apple has 3 fields")
    ));
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    MetricsPrinter.print(result, projectDirectory, new PrintStream(bytes, true, StandardCharsets.UTF_8));

    String output = bytes.toString(StandardCharsets.UTF_8);
    assertThat(output)
        .doesNotContain(projectDirectory.toString())
        .containsSubsequence(
            "else-used | src/main/java/Branch.java:20 | choose uses else",
            "too-many-instance-fields | src/main/java/Apple.java:12 | Apple has 3 fields",
            "too-many-instance-fields | src/main/java/Zebra.java:3 | early finding",
            "too-many-instance-fields | src/main/java/Zebra.java:20 | late finding",
            "ADVICE else-used",
            "ADVICE too-many-instance-fields"
        )
        .containsOnlyOnce("ADVICE too-many-instance-fields")
        .containsOnlyOnce("Why: Keep related state and its behaviour together.");
  }

  @Test
  void printsSiblingSourcesRelativeToTheProject() {
    Path siblingSource = projectDirectory.resolveSibling("shared/Shared.java");
    AnalysisResult result = new AnalysisResult(List.of(
        new Violation(siblingSource, 7, "else-used", "choose", "choose uses else")
    ));
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    MetricsPrinter.print(result, projectDirectory, new PrintStream(bytes, true, StandardCharsets.UTF_8));

    assertThat(bytes.toString(StandardCharsets.UTF_8))
        .contains("else-used | ../shared/Shared.java:7 | choose uses else")
        .doesNotContain(siblingSource.toString());
  }

  private Violation violation(String file, int line, String rule, String subject, String message) {
    return new Violation(projectDirectory.resolve(file), line, rule, subject, message);
  }
}
