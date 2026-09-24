package com.github.larseckart.objectcalisthenics.analyzer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Small command-line entry point for analyzing a directory of Java source.
 *
 * <p>Usage: {@code object-calisthenics-analyzer <source-root>}</p>
 */
public class ObjectCalisthenicsAnalyzerCli {

  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("Usage: object-calisthenics-analyzer <source-root>");
      System.exit(1);
    }

    Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
    ObjectCalisthenicsAnalyzer analyzer = new ObjectCalisthenicsAnalyzer();
    AnalysisResult result = analyzer.analyze(sourceRoot);

    long classesTooLong = result.violations().stream()
        .filter(v -> v.rule().equals("class-too-long"))
        .count();
    long tooManyFields = result.violations().stream()
        .filter(v -> v.rule().equals("too-many-instance-fields") || v.rule().equals("too-many-record-components"))
        .count();
    long methodsWithElse = result.violations().stream()
        .filter(v -> v.rule().equals("else-used"))
        .count();
    long methodsOverNested = result.violations().stream()
        .filter(v -> v.rule().equals("method-over-nested"))
        .count();
    long gettersSetters = result.violations().stream()
        .filter(v -> v.rule().equals("getter") || v.rule().equals("setter"))
        .count();
    long traversalChains = result.violations().stream()
        .filter(v -> v.rule().equals("traversal-chain"))
        .count();

    System.out.println("METRIC violations=" + result.totalViolations());
    System.out.println("METRIC classes_over_50=" + classesTooLong);
    System.out.println("METRIC classes_over_2_fields=" + tooManyFields);
    System.out.println("METRIC methods_with_else=" + methodsWithElse);
    System.out.println("METRIC methods_over_nested=" + methodsOverNested);
    System.out.println("METRIC getter_setter_methods=" + gettersSetters);
    System.out.println("METRIC traversal_chains=" + traversalChains);

    for (Violation violation : result.violations()) {
      System.out.println(violation.file() + ":" + violation.line() + " " + violation.rule() + " - " + violation.message());
    }
  }
}
