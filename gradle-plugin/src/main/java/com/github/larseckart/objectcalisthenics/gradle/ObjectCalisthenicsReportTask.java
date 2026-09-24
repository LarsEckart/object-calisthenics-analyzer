package com.github.larseckart.objectcalisthenics.gradle;

import com.github.larseckart.objectcalisthenics.analyzer.AnalysisResult;
import com.github.larseckart.objectcalisthenics.analyzer.ObjectCalisthenicsAnalyzer;
import com.github.larseckart.objectcalisthenics.analyzer.RuleSet;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Gradle task that analyses Java source and writes a JSON report.
 *
 * <p>Unlike {@link ObjectCalisthenicsCheckTask}, this task never fails the
 * build, so it can be used to inspect the current state of a legacy codebase.</p>
 */
@CacheableTask
public abstract class ObjectCalisthenicsReportTask extends DefaultTask {

  @InputFiles
  @SkipWhenEmpty
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract ConfigurableFileCollection getSourceFiles();

  @Nested
  public abstract ObjectCalisthenicsRules getRules();

  @Input
  public abstract ListProperty<String> getClassNamePatterns();

  @OutputFile
  public abstract RegularFileProperty getJson();

  @Input
  public abstract Property<Boolean> getConsoleSummary();

  @Internal
  public abstract DirectoryProperty getProjectDirectory();

  @TaskAction
  public void report() {
    RuleSet ruleSet = RuleSetFactory.from(getRules());
    ObjectCalisthenicsAnalyzer analyzer = new ObjectCalisthenicsAnalyzer(
        ruleSet,
        getClassNamePatterns().get()
    );

    List<Path> files = getSourceFiles().getFiles().stream()
        .map(File::toPath)
        .filter(p -> p.toString().endsWith(".java"))
        .toList();

    AnalysisResult result = analyzer.analyze(files);

    JsonReportWriter.write(result, getJson().get().getAsFile().toPath());

    if (getConsoleSummary().get()) {
      MetricsPrinter.print(
          result,
          getProjectDirectory().get().getAsFile().toPath(),
          System.out
      );
    }
  }
}
