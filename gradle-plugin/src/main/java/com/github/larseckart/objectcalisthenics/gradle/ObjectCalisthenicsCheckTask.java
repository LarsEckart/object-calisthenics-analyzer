package com.github.larseckart.objectcalisthenics.gradle;

import com.github.larseckart.objectcalisthenics.analyzer.AnalysisResult;
import com.github.larseckart.objectcalisthenics.analyzer.ObjectCalisthenicsAnalyzer;
import com.github.larseckart.objectcalisthenics.analyzer.RuleSet;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Gradle task that analyses Java source and fails the build on violations.
 */
@CacheableTask
public abstract class ObjectCalisthenicsCheckTask extends DefaultTask {

  @InputFiles
  @SkipWhenEmpty
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract ConfigurableFileCollection getSourceFiles();

  @Nested
  public abstract ObjectCalisthenicsRules getRules();

  @Input
  public abstract Property<Boolean> getConsoleSummary();

  @Internal
  public abstract RegularFileProperty getBaselineFile();

  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract ConfigurableFileCollection getBaselineFiles();

  @Internal
  public abstract DirectoryProperty getProjectDirectory();

  @Input
  public abstract Property<Boolean> getIgnoreFailures();

  @TaskAction
  public void check() {
    RuleSet ruleSet = RuleSetFactory.from(getRules());
    ObjectCalisthenicsAnalyzer analyzer = new ObjectCalisthenicsAnalyzer(ruleSet);

    List<Path> files = getSourceFiles().getFiles().stream()
        .map(File::toPath)
        .filter(p -> p.toString().endsWith(".java"))
        .toList();

    AnalysisResult result = analyzer.analyze(files);

    if (getConsoleSummary().get()) {
      MetricsPrinter.print(result, System.out);
    }

    Baseline baseline = Baseline.load(
        getBaselineFile().get().getAsFile().toPath(),
        getProjectDirectory().get().getAsFile().toPath()
    );
    List<Baseline.Entry> current = baseline.entriesFor(result.violations());
    List<Baseline.Entry> newViolations = baseline.newEntries(current);
    List<Baseline.Entry> staleEntries = baseline.staleEntries(current);

    if (!staleEntries.isEmpty()) {
      getLogger().warn(
          "Object Calisthenics baseline has {} stale entr{}; regenerate it with objectCalisthenicsBaseline.",
          staleEntries.size(),
          staleEntries.size() == 1 ? "y" : "ies"
      );
    }

    if (!newViolations.isEmpty() && !getIgnoreFailures().get()) {
      throw new GradleException(
          "Object Calisthenics violations found: " + newViolations.size()
              + " new, " + (current.size() - newViolations.size()) + " baselined");
    }

    if (!newViolations.isEmpty()) {
      getLogger().warn(
          "Object Calisthenics violations found: {} new violation(s); ignoring failures as configured.",
          newViolations.size()
      );
    }
  }
}
