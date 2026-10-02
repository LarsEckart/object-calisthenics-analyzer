package com.github.larseckart.objectcalisthenics.gradle;

import com.github.larseckart.objectcalisthenics.analyzer.AnalysisResult;
import com.github.larseckart.objectcalisthenics.analyzer.Violation;
import com.github.larseckart.objectcalisthenics.analyzer.ViolationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonReportWriterTest {

  @TempDir
  Path tempDirectory;

  @Test
  void writesStructuredContextOnlyWhenPresent() throws IOException {
    Violation contextual = new Violation(
        Path.of("Order.java"),
        4,
        "too-many-instance-fields",
        "Order",
        "Order has 3 fields")
        .withContext(new ViolationContext(
            "field-list",
            "The type declares 3 instance fields.",
            List.of("Customer customer", "Money total"),
            "Look for a cohesive subset.",
            "Only domain knowledge can identify one."));
    Violation withoutContext = new Violation(
        Path.of("Branch.java"), 8, "else-used", "choose", "choose uses else");
    Path report = tempDirectory.resolve("report.json");

    JsonReportWriter.write(new AnalysisResult(List.of(contextual, withoutContext)), report);

    assertThat(Files.readString(report))
        .containsOnlyOnce("\"context\": {")
        .contains(
            "\"kind\": \"field-list\"",
            "\"summary\": \"The type declares 3 instance fields.\"",
            "\"related_code\": [\"Customer customer\", \"Money total\"]",
            "\"suggestion\": \"Look for a cohesive subset.\"",
            "\"caution\": \"Only domain knowledge can identify one.\"")
        .contains("\"advice\": {");
  }
}
