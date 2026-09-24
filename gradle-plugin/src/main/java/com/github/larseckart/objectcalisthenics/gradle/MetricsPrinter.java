package com.github.larseckart.objectcalisthenics.gradle;

import com.github.larseckart.objectcalisthenics.analyzer.AnalysisResult;
import com.github.larseckart.objectcalisthenics.analyzer.Violation;

import java.io.PrintStream;

/**
 * Prints the backwards-compatible {@code METRIC} lines used by the original
 * Python measurement script.
 */
final class MetricsPrinter {

  private MetricsPrinter() {
    // utility class
  }

  static void print(AnalysisResult result, PrintStream out) {
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

    for (Violation violation : result.violations()) {
      out.println(violation.file() + ":" + violation.line() + " " + violation.rule() + " - " + violation.message());
      out.println("  Why: " + violation.advice().principle());
      for (String option : violation.advice().options()) {
        out.println("  Try: " + option);
      }
      out.println("  Note: " + violation.advice().caution());
    }
  }
}
