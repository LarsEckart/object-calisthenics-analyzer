package com.github.larseckart.objectcalisthenics.gradle;

import com.github.larseckart.objectcalisthenics.analyzer.Advice;
import com.github.larseckart.objectcalisthenics.analyzer.AnalysisResult;
import com.github.larseckart.objectcalisthenics.analyzer.ExcludedClass;
import com.github.larseckart.objectcalisthenics.analyzer.Violation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes the analysis result as JSON for the report task.
 */
final class JsonReportWriter {

  private JsonReportWriter() {
    // utility class
  }

  static void write(AnalysisResult result, Path file) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, toJson(result));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write report to " + file, e);
    }
  }

  private static String toJson(AnalysisResult result) {
    List<Violation> violations = result.violations();
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"violations\": ").append(result.totalViolations()).append(",\n");
    sb.append("  \"metrics\": {\n");
    sb.append("    \"classes_over_50\": ").append(count(violations, "class-too-long")).append(",\n");
    sb.append("    \"classes_over_2_fields\": ")
        .append(count(violations, "too-many-instance-fields") + count(violations, "too-many-record-components"))
        .append(",\n");
    sb.append("    \"methods_with_else\": ").append(count(violations, "else-used")).append(",\n");
    sb.append("    \"methods_over_nested\": ").append(count(violations, "method-over-nested")).append(",\n");
    sb.append("    \"getter_setter_methods\": ")
        .append(count(violations, "getter") + count(violations, "setter"))
        .append(",\n");
    sb.append("    \"non_first_class_collections\": ")
        .append(count(violations, "non-first-class-collection"))
        .append(",\n");
    sb.append("    \"traversal_chains\": ")
        .append(count(violations, "traversal-chain"))
        .append("\n");
    sb.append("  },\n");
    sb.append("  \"excluded_classes\": [\n");
    appendExcludedClasses(sb, result.excludedClasses());
    sb.append("  ],\n");
    sb.append("  \"details\": [\n");
    for (int i = 0; i < violations.size(); i++) {
      Violation v = violations.get(i);
      sb.append("    {\n");
      sb.append("      \"file\": \"").append(jsonEscape(v.file().toString())).append("\",\n");
      sb.append("      \"line\": ").append(v.line()).append(",\n");
      sb.append("      \"rule\": \"").append(jsonEscape(v.rule())).append("\",\n");
      sb.append("      \"subject\": \"").append(jsonEscape(v.subject())).append("\",\n");
      sb.append("      \"message\": \"").append(jsonEscape(v.message())).append("\",\n");
      appendAdvice(sb, v.advice());
      sb.append("    }");
      if (i < violations.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ]\n");
    sb.append("}\n");
    return sb.toString();
  }

  private static void appendAdvice(StringBuilder sb, Advice advice) {
    sb.append("      \"advice\": {\n");
    sb.append("        \"principle\": \"").append(jsonEscape(advice.principle())).append("\",\n");
    sb.append("        \"options\": [");
    for (int i = 0; i < advice.options().size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append("\"").append(jsonEscape(advice.options().get(i))).append("\"");
    }
    sb.append("],\n");
    sb.append("        \"caution\": \"").append(jsonEscape(advice.caution())).append("\"\n");
    sb.append("      }\n");
  }

  private static void appendExcludedClasses(StringBuilder sb, List<ExcludedClass> excludedClasses) {
    for (int i = 0; i < excludedClasses.size(); i++) {
      ExcludedClass excludedClass = excludedClasses.get(i);
      sb.append("    {\n");
      sb.append("      \"file\": \"").append(jsonEscape(excludedClass.file().toString())).append("\",\n");
      sb.append("      \"class_name\": \"").append(jsonEscape(excludedClass.className())).append("\",\n");
      sb.append("      \"matched_patterns\": [");
      for (int j = 0; j < excludedClass.matchedPatterns().size(); j++) {
        if (j > 0) {
          sb.append(", ");
        }
        sb.append("\"").append(jsonEscape(excludedClass.matchedPatterns().get(j))).append("\"");
      }
      sb.append("]\n");
      sb.append("    }");
      if (i < excludedClasses.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
  }

  private static long count(List<Violation> violations, String rule) {
    return violations.stream().filter(v -> v.rule().equals(rule)).count();
  }

  private static String jsonEscape(String value) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < ' ') {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }
}
