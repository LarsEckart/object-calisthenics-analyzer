package com.larseckart.objectcalisthenics.analyzer;

import com.larseckart.objectcalisthenics.analyzer.checks.CompilationUnitChecks;
import com.larseckart.objectcalisthenics.analyzer.parsing.JavaSourceParser;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Analyzes Java source files for Object Calisthenics violations.
 *
 * <p>The analyzer is independent of Gradle or any build tool. It parses the supplied files, applies
 * the configured checks, and returns the findings.
 */
public class ObjectCalisthenicsAnalyzer {

  private final JavaSourceParser parser;
  private final CompilationUnitChecks checks;

  public ObjectCalisthenicsAnalyzer(RuleSet rules, List<String> classNamePatterns) {
    this(rules, PrimitiveObsessionConfig.defaults(), classNamePatterns);
  }

  public ObjectCalisthenicsAnalyzer(
      RuleSet rules, PrimitiveObsessionConfig primitiveObsession, List<String> classNamePatterns) {
    this.parser = new JavaSourceParser();
    this.checks = new CompilationUnitChecks(rules, primitiveObsession, classNamePatterns);
  }

  public ObjectCalisthenicsAnalyzer(RuleSet rules) {
    this(rules, List.of());
  }

  public ObjectCalisthenicsAnalyzer() {
    this(RuleSet.defaults());
  }

  /** Analyzes all Java files under the given directory. */
  public AnalysisResult analyze(Path sourceRoot) {
    List<Violation> violations = Collections.synchronizedList(new ArrayList<>());
    List<ExcludedClass> excludedClasses = Collections.synchronizedList(new ArrayList<>());
    parser.parse(sourceRoot, unit -> checks.check(unit, violations, excludedClasses));
    return new AnalysisResult(violations, excludedClasses);
  }

  /** Analyzes the supplied Java source files directly. */
  public AnalysisResult analyze(List<Path> files) {
    List<Violation> violations = new ArrayList<>();
    List<ExcludedClass> excludedClasses = new ArrayList<>();
    parser.parse(files, unit -> checks.check(unit, violations, excludedClasses));
    return new AnalysisResult(violations, excludedClasses);
  }
}
