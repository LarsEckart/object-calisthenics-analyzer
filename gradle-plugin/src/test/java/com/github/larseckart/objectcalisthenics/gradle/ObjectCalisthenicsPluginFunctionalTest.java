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

    Path buildScript = projectDir.resolve("build.gradle.kts");
    Files.writeString(buildScript, """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }
        """);

    BuildResult result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("objectCalisthenicsCheck", "--stacktrace")
        .buildAndFail();

    assertThat(result.getOutput()).contains("Object Calisthenics violations found");
    assertThat(result.getOutput()).contains("METRIC violations=");
    assertThat(result.getOutput()).contains("Why: Keep each class focused on one responsibility.");
  }

  @Test
  void ignoreFailuresReportsViolationsWithoutFailing() throws IOException {
    writeSettings();
    writeBadJavaSource();
    Files.writeString(projectDir.resolve("build.gradle.kts"), """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }

        objectCalisthenics {
            ignoreFailures.set(true)
        }
        """);

    BuildResult result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("objectCalisthenicsCheck", "--stacktrace")
        .build();

    assertThat(result.getOutput()).contains("METRIC violations=2");
    assertThat(result.getOutput()).contains("ignoring failures as configured");
  }

  @Test
  void baselineIgnoresExistingViolationsAndLineChangesButRejectsNewOnes() throws IOException {
    writeSettings();
    writeBadJavaSource();
    Files.writeString(projectDir.resolve("build.gradle.kts"), """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }
        """);

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
    runner("objectCalisthenicsCheck").build();

    Files.writeString(projectDir.resolve("src/main/java/NewViolation.java"), """
        public class NewViolation {
          private int value;

          public int getValue() {
            return value;
          }
        }
        """);

    BuildResult failedCheck = runner("objectCalisthenicsCheck").buildAndFail();
    assertThat(failedCheck.getOutput())
        .contains("Object Calisthenics violations found: 1 new, 2 baselined");
  }

  @Test
  void checkReportsStaleBaselineEntries() throws IOException {
    writeSettings();
    writeBadJavaSource();
    Files.writeString(projectDir.resolve("build.gradle.kts"), """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }
        """);
    runner("objectCalisthenicsBaseline").build();

    Files.delete(projectDir.resolve("src/main/java/Bad.java"));
    Files.writeString(projectDir.resolve("src/main/java/Clean.java"), "class Clean {}\n");

    BuildResult result = runner("objectCalisthenicsCheck").build();

    assertThat(result.getOutput())
        .contains("baseline has 2 stale entries")
        .contains("objectCalisthenicsBaseline");
  }

  @Test
  void baselineRespectsClassNameExclusions() throws IOException {
    writeSettings();
    writeBadJavaSource();
    Files.writeString(projectDir.resolve("build.gradle.kts"), """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }

        objectCalisthenics {
            exclusions {
                classNamePatterns.add("Bad")
            }
        }
        """);

    BuildResult baselineResult = runner("objectCalisthenicsBaseline").build();
    assertThat(baselineResult.getOutput()).contains("Wrote 0 Object Calisthenics baseline entries");

    Path baseline = projectDir.resolve("object-calisthenics-baseline.txt");
    assertThat(baseline).exists();
    assertThat(Files.readString(baseline)).doesNotContain("Bad.java");

    BuildResult checkResult = runner("objectCalisthenicsCheck").build();
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
    Files.writeString(projectDir.resolve("build.gradle.kts"), """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }

        objectCalisthenics {
            exclusions {
                classNamePatterns.add(".*Response$")
            }
        }
        """);

    BuildResult baselineResult = runner("objectCalisthenicsBaseline").build();
    assertThat(baselineResult.getOutput()).contains("Wrote 0 Object Calisthenics baseline entries");

    Path baseline = projectDir.resolve("object-calisthenics-baseline.txt");
    assertThat(baseline).exists();
    assertThat(Files.readString(baseline)).doesNotContain("ExcludedResponse");

    BuildResult checkResult = runner("objectCalisthenicsCheck").build();
    assertThat(checkResult.getOutput())
        .contains("violations=0")
        .doesNotContain("stale");
  }

  @Test
  void reportTaskWritesJsonWithoutFailing() throws IOException {
    writeSettings();
    writeBadJavaSource();

    Path buildScript = projectDir.resolve("build.gradle.kts");
    Files.writeString(buildScript, """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }
        """);

    BuildResult result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("objectCalisthenicsReport", "--stacktrace")
        .build();

    Path report = projectDir.resolve("build/reports/calisthenics/calisthenics.json");
    assertThat(report).exists();
    String json = Files.readString(report);
    assertThat(json).contains("\"violations\":");
    assertThat(json).contains("class-too-long");
    assertThat(json).contains("\"subject\": \"Bad\"");
    assertThat(json).contains("\"advice\"");
    assertThat(json).contains("Keep each class focused on one responsibility.");
  }

  @Test
  void reportTaskAppliesConfiguredFirstClassCollectionRule() throws IOException {
    writeSettings();
    Path sourceDir = projectDir.resolve("src/main/java/");
    Files.createDirectories(sourceDir);
    Files.writeString(sourceDir.resolve("CollectionOwner.java"), """
        import java.util.List;

        class CollectionOwner {
          private List<String> values;
          private int count;
        }
        """);

    Files.writeString(projectDir.resolve("build.gradle.kts"), """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }

        objectCalisthenics {
            rules {
                forbidNonFirstClassCollections.set(true)
            }
        }
        """);

    GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("objectCalisthenicsReport", "--stacktrace")
        .build();

    String json = Files.readString(projectDir.resolve("build/reports/calisthenics/calisthenics.json"));
    assertThat(json).contains("\"non_first_class_collections\": 1");
    assertThat(json).contains("\"rule\": \"non-first-class-collection\"");
  }

  @Test
  void reportTaskCanExcludeRecordComponentsFromFieldRule() throws IOException {
    writeSettings();
    Path sourceDir = projectDir.resolve("src/main/java/");
    Files.createDirectories(sourceDir);
    Files.writeString(sourceDir.resolve("Configuration.java"), """
        record Configuration(String host, int port, boolean secure) {
        }
        """);

    Files.writeString(projectDir.resolve("build.gradle.kts"), """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }

        objectCalisthenics {
            rules {
                includeRecordComponentsInFieldRule.set(false)
            }
        }
        """);

    GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("objectCalisthenicsReport", "--stacktrace")
        .build();

    String json = Files.readString(projectDir.resolve("build/reports/calisthenics/calisthenics.json"));
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

    Files.writeString(projectDir.resolve("build.gradle.kts"), """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }

        objectCalisthenics {
            rules {
                strictGetterNames.set(true)
                forbidTraversalChains.set(true)
                fluentChainMethods.set(setOf("with", "build"))
                safeChainRoots.set(setOf("System.out"))
            }
        }
        """);

    GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("objectCalisthenicsReport", "--stacktrace")
        .build();

    String json = Files.readString(projectDir.resolve("build/reports/calisthenics/calisthenics.json"));
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
    Files.writeString(projectDir.resolve("build.gradle.kts"), """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }

        objectCalisthenics {
            exclusions {
                classNamePatterns.add(".*Response$")
            }
        }
        """);

    BuildResult check = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("objectCalisthenicsCheck", "--stacktrace")
        .buildAndFail();

    assertThat(check.getOutput()).contains("Object Calisthenics violations found");
    assertThat(check.getOutput()).contains("JoinMatchCommand has 3 instance fields");

    GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("objectCalisthenicsReport", "--stacktrace")
        .build();

    String json = Files.readString(projectDir.resolve("build/reports/calisthenics/calisthenics.json"));
    assertThat(json).contains("\"classes_over_2_fields\": 1");
    assertThat(json).contains("\"class_name\": \"JoinMatchResponse\"");
    assertThat(json).contains("\"matched_patterns\": [\".*Response$\"]");
    assertThat(details(json)).doesNotContain("JoinMatchResponse");
    assertThat(details(json)).contains("JoinMatchCommand");
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
    Files.writeString(projectDir.resolve("build.gradle.kts"), """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }

        objectCalisthenics {
            exclusions {
                classNamePatterns.add(".*Response$")
                classNamePatterns.add("Api.*")
                classNamePatterns.add(".*Model$")
            }
        }
        """);

    GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("objectCalisthenicsReport", "--stacktrace")
        .build();

    String json = Files.readString(projectDir.resolve("build/reports/calisthenics/calisthenics.json"));
    assertThat(json).contains("\"class_name\": \"ApiResponse\"");
    assertThat(json).contains("\"class_name\": \"GeneratedModel\"");
    assertThat(json).contains("\"matched_patterns\": [\".*Response$\", \"Api.*\"]");
    assertThat(json).contains("\"matched_patterns\": [\".*Model$\"]");
    assertThat(details(json)).contains("ActiveRequest");
    assertThat(details(json)).doesNotContain("ApiResponse", "GeneratedModel");
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
    Files.writeString(projectDir.resolve("build.gradle.kts"), """
        plugins {
            java
            id("com.larseckart.object-calisthenics")
        }

        objectCalisthenics {
            exclusions {
                classNamePatterns.add(".*Response$")
            }
        }
        """);

    GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("objectCalisthenicsReport", "--stacktrace")
        .build();

    String json = Files.readString(projectDir.resolve("build/reports/calisthenics/calisthenics.json"));
    assertThat(json).contains("\"class_name\": \"NestedResponse\"");
    assertThat(json).contains("\"class_name\": \"ReceiptResponse\"");
    assertThat(json).contains("\"classes_over_2_fields\": 2");
    assertThat(details(json)).contains("NestedRequest", "ReceiptRequest");
    assertThat(details(json)).doesNotContain("NestedResponse", "ReceiptResponse");
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

  private GradleRunner runner(String task) {
    return GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(task, "--stacktrace");
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
