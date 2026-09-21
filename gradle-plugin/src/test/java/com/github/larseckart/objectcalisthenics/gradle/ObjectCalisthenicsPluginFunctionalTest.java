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
            id("com.github.larseckart.object-calisthenics")
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
  void reportTaskWritesJsonWithoutFailing() throws IOException {
    writeSettings();
    writeBadJavaSource();

    Path buildScript = projectDir.resolve("build.gradle.kts");
    Files.writeString(buildScript, """
        plugins {
            java
            id("com.github.larseckart.object-calisthenics")
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
            id("com.github.larseckart.object-calisthenics")
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

  private void writeSettings() throws IOException {
    Files.writeString(projectDir.resolve("settings.gradle.kts"), """
        rootProject.name = "functional-test"
        """);
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
