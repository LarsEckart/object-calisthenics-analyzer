package com.github.larseckart.objectcalisthenics.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Supplies Git changes through Gradle so they can be read with the configuration cache on. */
public abstract class GitChangedJavaFiles implements ValueSource<Set<File>, GitChangedJavaFiles.Parameters> {

  public interface Parameters extends ValueSourceParameters {
    Property<String> getProjectDirectory();
  }

  @Inject
  protected abstract ExecOperations getExecOperations();

  @Override
  public Set<File> obtain() {
    Path directory = Path.of(getParameters().getProjectDirectory().get());
    Path root = Path.of(run(directory, "rev-parse", "--show-toplevel").trim());
    Set<Path> changed = new LinkedHashSet<>();
    addPaths(changed, root, run(root, "diff", "--name-only", "-z", "--diff-filter=ACMRT"));
    addPaths(changed, root, run(root, "diff", "--cached", "--name-only", "-z", "--diff-filter=ACMRT"));
    addPaths(changed, root, run(root, "ls-files", "--others", "--exclude-standard", "-z"));
    return changed.stream()
        .filter(Files::isRegularFile)
        .filter(file -> file.toString().endsWith(".java"))
        .map(Path::toFile)
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  static Set<File> inSourceSet(Set<File> changed, FileCollection sourceFiles) {
    Set<Path> sources = new HashSet<>();
    for (File file : sourceFiles.getFiles()) {
      sources.add(file.toPath().toAbsolutePath().normalize());
    }
    Set<File> selected = new LinkedHashSet<>();
    for (File file : changed) {
      if (sources.contains(file.toPath().toAbsolutePath().normalize())) {
        selected.add(file);
      }
    }
    return selected;
  }

  private static void addPaths(Set<Path> changed, Path root, String output) {
    Arrays.stream(output.split("\u0000"))
        .filter(path -> !path.isEmpty())
        .map(root::resolve)
        .map(Path::normalize)
        .forEach(changed::add);
  }

  private String run(Path directory, String... arguments) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream errors = new ByteArrayOutputStream();
    var result = getExecOperations().exec(spec -> {
      spec.commandLine("git", "-C", directory.toString());
      spec.args((Object[]) arguments);
      spec.setStandardOutput(output);
      spec.setErrorOutput(errors);
      spec.setIgnoreExitValue(true);
    });
    if (result.getExitValue() != 0) {
      throw new GradleException("Cannot select changed Java files: " + errors.toString(StandardCharsets.UTF_8).trim());
    }
    return output.toString(StandardCharsets.UTF_8);
  }
}
