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
          public int getValue() {
            return 1;
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
