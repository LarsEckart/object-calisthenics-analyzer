package com.github.larseckart.objectcalisthenics.gradle;

import com.github.larseckart.objectcalisthenics.analyzer.Advice;
import com.github.larseckart.objectcalisthenics.analyzer.AnalysisResult;
import com.github.larseckart.objectcalisthenics.analyzer.Violation;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Prints concise findings and the backwards-compatible {@code METRIC} lines
 * used by the original Python measurement script.
 */
final class MetricsPrinter {

  private MetricsPrinter() {
    // utility class
  }

  static void print(AnalysisResult result, Path projectDirectory, PrintStream out) {
    long classesOver50 = result.violations().stream()
        .filter(v -> v.rule().equals("class-too-long"))
        .count();
    long classesOver2Fields = result.violations().stream()
        .filter(v -> v.rule().equals("too-many-instance-fields") || v.rule().equals("too-many-record-components"))
        .count();
    long methodsWithElse = result.violations().stream()
        .filter(v -> v.rule().equals("else-used"))
        .count();
    long methodsOverNested = result.violations().stream()
        .filter(v -> v.rule().equals("method-over-nested"))
        .count();
    long getterSetters = result.violations().stream()
        .filter(v -> v.rule().equals("getter") || v.rule().equals("setter"))
        .count();
    long nonFirstClassCollections = result.violations().stream()
        .filter(v -> v.rule().equals("non-first-class-collection"))
        .count();
    long traversalChains = result.violations().stream()
        .filter(v -> v.rule().equals("traversal-chain"))
        .count();

    out.println("METRIC violations=" + result.totalViolations());
    out.println("METRIC classes_over_50=" + classesOver50);
    out.println("METRIC classes_over_2_fields=" + classesOver2Fields);
    out.println("METRIC methods_with_else=" + methodsWithElse);
    out.println("METRIC methods_over_nested=" + methodsOverNested);
    out.println("METRIC getter_setter_methods=" + getterSetters);
    out.println("METRIC non_first_class_collections=" + nonFirstClassCollections);
    out.println("METRIC traversal_chains=" + traversalChains);

    List<Violation> violations = result.violations().stream()
        .sorted(Comparator.comparing(Violation::rule)
            .thenComparing(violation -> relativePath(violation.file(), projectDirectory))
            .thenComparingInt(Violation::line)
            .thenComparing(Violation::subject)
            .thenComparing(Violation::message))
        .toList();

    for (Violation violation : violations) {
      out.println(violation.rule() + " | "
          + relativePath(violation.file(), projectDirectory) + ":" + violation.line() + " | "
          + violation.message());
    }

    violations.stream()
        .map(Violation::rule)
        .distinct()
        .forEach(rule -> printAdvice(rule, Advice.forRule(rule), out));
  }

  private static void printAdvice(String rule, Advice advice, PrintStream out) {
    out.println("ADVICE " + rule);
    out.println("  Why: " + advice.principle());
    for (String option : advice.options()) {
      out.println("  Try: " + option);
    }
    out.println("  Note: " + advice.caution());
  }

  private static String relativePath(Path file, Path projectDirectory) {
    Path absoluteFile = file.toAbsolutePath().normalize();
    Path absoluteProjectDirectory = projectDirectory.toAbsolutePath().normalize();
    Path displayedPath = absoluteFile.startsWith(absoluteProjectDirectory)
        ? absoluteProjectDirectory.relativize(absoluteFile)
        : absoluteFile;
    return displayedPath.toString().replace(file.getFileSystem().getSeparator(), "/");
  }
}
