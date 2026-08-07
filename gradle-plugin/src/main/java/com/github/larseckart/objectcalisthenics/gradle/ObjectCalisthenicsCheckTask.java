package com.github.larseckart.objectcalisthenics.gradle;

import com.github.larseckart.objectcalisthenics.analyzer.AnalysisResult;
import com.github.larseckart.objectcalisthenics.analyzer.ObjectCalisthenicsAnalyzer;
import com.github.larseckart.objectcalisthenics.analyzer.RuleSet;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
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

    if (!result.violations().isEmpty()) {
      throw new GradleException(
          "Object Calisthenics violations found: " + result.totalViolations());
    }
  }
}
