package com.larseckart.objectcalisthenics.gradle.tasks;

import com.larseckart.objectcalisthenics.analyzer.AnalysisResult;
import com.larseckart.objectcalisthenics.analyzer.ObjectCalisthenicsAnalyzer;
import com.larseckart.objectcalisthenics.analyzer.PrimitiveObsessionConfig;
import com.larseckart.objectcalisthenics.analyzer.checks.CompilationUnitChecks;
import com.larseckart.objectcalisthenics.analyzer.parsing.JavaSourceParser;
import com.larseckart.objectcalisthenics.gradle.baseline.Baseline;
import com.larseckart.objectcalisthenics.gradle.configuration.ObjectCalisthenicsRules;
import com.larseckart.objectcalisthenics.gradle.configuration.RuleSetFactory;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Gradle task that records all current violations so checks only fail on new ones. */
@CacheableTask
public abstract class ObjectCalisthenicsBaselineTask extends DefaultTask {

  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract ConfigurableFileCollection getSourceFiles();

  @Nested
  public abstract ObjectCalisthenicsRules getRules();

  @Input
  public abstract ListProperty<String> getClassNamePatterns();

  @OutputFile
  public abstract RegularFileProperty getBaselineFile();

  @Internal
  public abstract DirectoryProperty getProjectDirectory();

  @TaskAction
  public void createBaseline() {
    ObjectCalisthenicsAnalyzer analyzer =
        new ObjectCalisthenicsAnalyzer(
            new JavaSourceParser(),
            new CompilationUnitChecks(
                RuleSetFactory.from(getRules()),
                new PrimitiveObsessionConfig(
                    getRules().getPrimitiveObsessionThreshold().get(),
                    getRules().getAllowedRawTypes().get(),
                    getRules().getBoundaryClassNamePatterns().get()),
                getClassNamePatterns().get()));
    List<Path> files =
        getSourceFiles().getFiles().stream()
            .map(File::toPath)
            .filter(path -> path.toString().endsWith(".java"))
            .toList();
    AnalysisResult result = analyzer.analyze(files);
    Path baselineFile = getBaselineFile().get().getAsFile().toPath();

    Baseline.write(
        baselineFile, getProjectDirectory().get().getAsFile().toPath(), result.violations());
    getLogger()
        .lifecycle(
            "Wrote {} Object Calisthenics baseline entr{} to {}",
            result.totalViolations(),
            result.totalViolations() == 1 ? "y" : "ies",
            baselineFile);
  }
}
