package com.github.larseckart.objectcalisthenics.gradle;

import com.github.larseckart.objectcalisthenics.analyzer.AnalysisResult;
import com.github.larseckart.objectcalisthenics.analyzer.ObjectCalisthenicsAnalyzer;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Gradle task that records all current violations so checks only fail on new ones.
 */
@CacheableTask
public abstract class ObjectCalisthenicsBaselineTask extends DefaultTask {

  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract ConfigurableFileCollection getSourceFiles();

  @Nested
  public abstract ObjectCalisthenicsRules getRules();

  @OutputFile
  public abstract RegularFileProperty getBaselineFile();

  @Internal
  public abstract DirectoryProperty getProjectDirectory();

  @TaskAction
  public void createBaseline() {
    ObjectCalisthenicsAnalyzer analyzer = new ObjectCalisthenicsAnalyzer(
        RuleSetFactory.from(getRules())
    );
    List<Path> files = getSourceFiles().getFiles().stream()
        .map(File::toPath)
        .filter(path -> path.toString().endsWith(".java"))
        .toList();
    AnalysisResult result = analyzer.analyze(files);
    Path baselineFile = getBaselineFile().get().getAsFile().toPath();

    Baseline.write(
        baselineFile,
        getProjectDirectory().get().getAsFile().toPath(),
        result.violations()
    );
    getLogger().lifecycle(
        "Wrote {} Object Calisthenics baseline entr{} to {}",
        result.totalViolations(),
        result.totalViolations() == 1 ? "y" : "ies",
        baselineFile
    );
  }
}
