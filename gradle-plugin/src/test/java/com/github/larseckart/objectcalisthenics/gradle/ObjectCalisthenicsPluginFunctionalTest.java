package com.github.larseckart.objectcalisthenics.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectCalisthenicsPluginFunctionalTest {

  @TempDir
  Path projectDir;

  @Test
  void checkTaskFailsWhenViolationsAreFound() throws IOException {
    writeSettings();
    writeBadJavaSource();
    writeBuildScript();

    BuildResult result = runCheckAndFail();

    assertCheckOutputContainsHeadlineFailure(result);
    assertThat(projectDir.resolve("build/reports/calisthenics/calisthenics.json"))
        .doesNotExist();
  }

  @Test
  void ignoreFailuresReportsViolationsWithoutFailing() throws IOException {
    writeSettings();
    writeBadJavaSource();
    writeBuildScript("""
        ignoreFailures.set(true)
        """);

    BuildResult result = runCheck();

    assertThat(result.getOutput()).contains("METRIC violations=2");
    assertThat(result.getOutput()).contains("ignoring failures as configured");
  }

  @Test
  void baselineIgnoresExistingViolationsAndLineChangesButRejectsNewOnes() throws IOException {
    writeSettings();
    writeBadJavaSource();
    writeBuildScript();

    BuildResult baselineResult = runner("objectCalisthenicsBaseline").build();

    Path baseline = projectDir.resolve("object-calisthenics-baseline.txt");
    assertThat(baseline).exists();
    assertThat(Files.readString(baseline))
        .contains("src/main/java/Bad.java")
        .contains("class-too-long")
        .contains("too-many-instance-fields");
    assertThat(baselineResult.getOutput()).contains("Wrote 2 Object Calisthenics baseline entries");

    Path badSource = projectDir.resolve("src/main/java/Bad.java");
    Files.writeString(badSource, "\n\n" + Files.readString(badSource));
    runCheck();

    Files.writeString(projectDir.resolve("src/main/java/NewViolation.java"), """
        public class NewViolation {
          private int value;

          public int getValue() {
            return value;
          }
        }
        """);

    BuildResult failedCheck = runCheckAndFail();
    assertThat(failedCheck.getOutput())
        .contains("Object Calisthenics violations found: 1 new, 2 baselined");
  }

  @Test
  void checkReportsStaleBaselineEntries() throws IOException {
    writeSettings();
    writeBadJavaSource();
    writeBuildScript();
    runner("objectCalisthenicsBaseline").build();

    Files.delete(projectDir.resolve("src/main/java/Bad.java"));
    Files.writeString(projectDir.resolve("src/main/java/Clean.java"), "class Clean {}\n");

    BuildResult result = runCheck();

    assertThat(result.getOutput())
        .contains("baseline has 2 stale entries")
        .contains("objectCalisthenicsBaseline");
  }

  @Test
  void baselineRespectsClassNameExclusions() throws IOException {
    writeSettings();
    writeBadJavaSource();
    writeBuildScript("""
        exclusions {
            classNamePatterns.add("Bad")
        }
        """);

    BuildResult baselineResult = runner("objectCalisthenicsBaseline").build();
    assertThat(baselineResult.getOutput()).contains("Wrote 0 Object Calisthenics baseline entries");

    Path baseline = projectDir.resolve("object-calisthenics-baseline.txt");
    assertThat(baseline).exists();
    assertThat(Files.readString(baseline)).doesNotContain("Bad.java");

    BuildResult checkResult = runCheck();
    assertThat(checkResult.getOutput()).doesNotContain("stale");
  }

  @Test
  void excludedResponseDoesNotCreateStaleBaselineEntry() throws IOException {
    writeSettings();
    writeJavaSource("ExcludedResponse.java", """
        class ExcludedResponse {
          private int first;
          private int second;
          private int third;

          public int getFirst() {
            return first;
          }
        }
        """);
    writeBuildScript("""
        exclusions {
            classNamePatterns.add(".*Response$")
        }
        """);

    BuildResult baselineResult = runner("objectCalisthenicsBaseline").build();
    assertThat(baselineResult.getOutput()).contains("Wrote 0 Object Calisthenics baseline entries");

    Path baseline = projectDir.resolve("object-calisthenics-baseline.txt");
    assertThat(baseline).exists();
    assertThat(Files.readString(baseline)).doesNotContain("ExcludedResponse");

    BuildResult checkResult = runCheck();
    assertThat(checkResult.getOutput())
        .contains("violations=0")
        .doesNotContain("stale");
  }

  @Test
  void reportTaskWritesJsonWithoutFailing() throws IOException {
    writeSettings();
    writeBadJavaSource();
    writeBuildScript();

    runReport();

    assertJsonReportContainsClassViolation(reportJson());
  }

  @Test
  void independentlyRegisteredReportTaskUsesProjectDirectoryByDefault() throws IOException {
    writeSettings();
    writeBadJavaSource();
    Files.writeString(projectDir.resolve("build.gradle.kts"), """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }

        tasks.register<com.github.larseckart.objectcalisthenics.gradle.ObjectCalisthenicsReportTask>("customReport") {
            sourceFiles.from(sourceSets.main.get().allJava)
            classNamePatterns.set(emptyList())
            json.set(layout.buildDirectory.file("reports/calisthenics/custom.json"))
            consoleSummary.set(true)
        }
        """);

    BuildResult result = runner("customReport").build();

    assertThat(result.getOutput()).contains("class-too-long | src/main/java/Bad.java:1 |");
    assertThat(projectDir.resolve("build/reports/calisthenics/custom.json")).exists();
  }

  @Test
  void reportTaskAppliesConfiguredFirstClassCollectionRule() throws IOException {
    String json = runReportTaskWithSourceAndConfig(
        "CollectionOwner.java",
        """
            import java.util.List;

            class CollectionOwner {
              private List<String> values;
              private int count;
            }
            """,
        """
            rules {
                forbidNonFirstClassCollections.set(true)
            }
            """);

    assertThat(json).contains("\"non_first_class_collections\": 1");
    assertThat(json).contains("\"rule\": \"non-first-class-collection\"");
  }

  @Test
  void reportTaskCanExcludeRecordComponentsFromFieldRule() throws IOException {
    String json = runReportTaskWithSourceAndConfig(
        "Configuration.java",
        """
            record Configuration(String host, int port, boolean secure) {
            }
            """,
        """
            rules {
                includeRecordComponentsInFieldRule.set(false)
            }
            """);

    assertThat(json).contains("\"violations\": 0");
    assertThat(json).doesNotContain("too-many-record-components");
  }

  @Test
  void reportTaskAppliesGetterAndTraversalConfiguration() throws IOException {
    writeSettings();
    Path sourceDir = projectDir.resolve("src/main/java/");
    Files.createDirectories(sourceDir);
    Files.writeString(sourceDir.resolve("ConfiguredRules.java"), """
        class ConfiguredRules {
          public boolean isReady() { return calculateReadiness(); }
          public Object address() { return customer().address(); }
        }
        """);
    writeBuildScript("""
        rules {
            strictGetterNames.set(true)
            forbidTraversalChains.set(true)
            fluentChainMethods.set(setOf("with", "build"))
            safeChainRoots.set(setOf("System.out"))
        }
        """);

    runReport();
    String json = reportJson();

    assertThat(json).contains("\"getter_setter_methods\": 1");
    assertThat(json).contains("\"traversal_chains\": 1");
    assertThat(json).contains("\"rule\": \"traversal-chain\"");
  }

  @Test
  void exclusionsIgnoreMatchingClassesAndKeepNonMatchingFailures() throws IOException {
    writeSettings();
    writeJavaSource("ApiTypes.java", """
        class JoinMatchResponse {
          private int first;
          private int second;
          private int third;

          public int getFirst() {
            return first;
          }
        }

        class JoinMatchCommand {
          private int first;
          private int second;
          private int third;
        }
        """);
    writeBuildScript("""
        exclusions {
            classNamePatterns.add(".*Response$")
        }
        """);

    BuildResult check = runCheckAndFail();
    assertThat(check.getOutput()).contains("JoinMatchCommand has 3 instance fields");

    runReport();
    String json = reportJson();
    assertReportContainsExclusion(json, "JoinMatchResponse", "[\".*Response$\"]");
    assertThat(json).contains("\"classes_over_2_fields\": 1");
    assertDetailsIncludeExclude(json, "JoinMatchCommand", "JoinMatchResponse");
  }

  @Test
  void canCombineSeveralClassNamePatterns() throws IOException {
    writeSettings();
    writeJavaSource("Types.java", """
        class ApiResponse {
          private int first;
          private int second;
          private int third;
        }

        class GeneratedModel {
          private int first;
          private int second;
          private int third;
        }

        class ActiveRequest {
          private int first;
          private int second;
          private int third;
        }
        """);
    writeBuildScript("""
        exclusions {
            classNamePatterns.add(".*Response$")
            classNamePatterns.add("Api.*")
            classNamePatterns.add(".*Model$")
        }
        """);

    runReport();
    String json = reportJson();
    assertReportContainsExclusion(json, "ApiResponse", "[\".*Response$\", \"Api.*\"]");
    assertReportContainsExclusion(json, "GeneratedModel", "[\".*Model$\"]");
    assertDetailsIncludeExclude(json, "ActiveRequest", "ApiResponse", "GeneratedModel");
  }

  @Test
  void exclusionsMatchNestedClassesAndRecordsBySimpleName() throws IOException {
    writeSettings();
    writeJavaSource("NestedTypes.java", """
        class Container {
          static class NestedResponse {
            private int first;
            private int second;
            private int third;

            public int getFirst() {
              return first;
            }
          }

          static class NestedRequest {
            private int first;
            private int second;
            private int third;
          }
        }

        record ReceiptResponse(int first, int second, int third) {
        }

        record ReceiptRequest(int first, int second, int third) {
        }
        """);
    writeBuildScript("""
        exclusions {
            classNamePatterns.add(".*Response$")
        }
        """);

    runReport();
    String json = reportJson();
    assertReportContainsExclusion(json, "NestedResponse", "[\".*Response$\"]");
    assertReportContainsExclusion(json, "ReceiptResponse", "[\".*Response$\"]");
    assertThat(json).contains("\"classes_over_2_fields\": 2");
    assertDetailsIncludeExclude(json, "NestedRequest", "NestedResponse");
    assertDetailsIncludeExclude(json, "ReceiptRequest", "ReceiptResponse");
  }

  private static String details(String json) {
    return json.substring(json.indexOf("\"details\":"));
  }

  private void writeJavaSource(String fileName, String source) throws IOException {
    Path sourceDir = projectDir.resolve("src/main/java/");
    Files.createDirectories(sourceDir);
    Files.writeString(sourceDir.resolve(fileName), source);
  }

  private void writeSettings() throws IOException {
    Files.writeString(projectDir.resolve("settings.gradle.kts"), """
        rootProject.name = "functional-test"
        """);
  }

  private void writeBuildScript() throws IOException {
    Files.writeString(projectDir.resolve("build.gradle.kts"), """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }
        """);
  }

  private void writeBuildScript(String objectCalisthenicsBlock) throws IOException {
    Files.writeString(projectDir.resolve("build.gradle.kts"), """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }

        objectCalisthenics {
            %s
        }
        """.formatted(objectCalisthenicsBlock.stripIndent()));
  }

  private GradleRunner runner(String task) {
    return GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(task, "--stacktrace");
  }

  private BuildResult runCheckAndFail() {
    return runner("objectCalisthenicsCheck").buildAndFail();
  }

  private BuildResult runCheck() {
    return runner("objectCalisthenicsCheck").build();
  }

  private BuildResult runReport() {
    return runner("objectCalisthenicsReport").build();
  }

  private String reportJson() throws IOException {
    return Files.readString(projectDir.resolve("build/reports/calisthenics/calisthenics.json"));
  }

  private String runReportTaskWithSourceAndConfig(String fileName, String source, String configBlock)
      throws IOException {
    writeSettings();
    writeJavaSource(fileName, source);
    writeBuildScript(configBlock);
    runReport();
    return reportJson();
  }

  private void assertCheckOutputContainsHeadlineFailure(BuildResult result) {
    assertThat(result.getOutput()).contains("Object Calisthenics violations found");
    assertThat(result.getOutput()).contains("METRIC violations=");
    assertThat(result.getOutput()).contains("class-too-long | src/main/java/Bad.java:1 |");
  }

  private void assertJsonReportContainsClassViolation(String json) {
    assertThat(json)
        .contains("class-too-long")
        .contains("\"subject\": \"Bad\"")
        .contains("\"advice\"", "Keep each class focused on one responsibility.");
  }

  private void assertReportContainsExclusion(
      String json, String className, String matchedPatterns) {
    assertThat(json).contains("\"class_name\": \"%s\"".formatted(className));
    assertThat(json).contains("\"matched_patterns\": %s".formatted(matchedPatterns));
  }

  private void assertDetailsIncludeExclude(String json, String included, String... excluded) {
    String details = details(json);
    assertThat(details).contains(included);
    assertThat(details).doesNotContain(excluded);
  }

  private void writeBadJavaSource() throws IOException {
    Path sourceDir = projectDir.resolve("src/main/java/");
    Files.createDirectories(sourceDir);
    StringBuilder builder = new StringBuilder();
    builder.append("public class Bad {\n");
    for (int i = 0; i < 60; i++) {
      builder.append("  private int field").append(i).append(" = ").append(i).append(";\n");
    }
    // Add enough fields to trigger the field rule but stay at 60 lines so the
    // class length violation is the headline failure.
    builder.append("}\n");
    Files.writeString(sourceDir.resolve("Bad.java"), builder.toString());
  }
}
