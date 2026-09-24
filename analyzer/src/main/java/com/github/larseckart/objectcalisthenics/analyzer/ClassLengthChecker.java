package com.github.larseckart.objectcalisthenics.analyzer;

import com.github.javaparser.ast.body.TypeDeclaration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Checks whether a type declaration exceeds the configured class length limit.
 *
 * <p>The limit is measured in "meaningful" source lines (non-blank and not
 * single-line comments).</p>
 */
class ClassLengthChecker {

  // Reproduces the Python script's class/record detection, including modifiers
  // and annotations that may appear on the same line as the keyword.
  private static final Pattern TYPE_DECLARATION_PATTERN =
      Pattern.compile(
          "^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:(?:public|private|protected|static|final)\\s+)*"
              + "(class|record|interface|enum)\\b");

  private final int maxClassLines;

  ClassLengthChecker(int maxClassLines) {
    this.maxClassLines = maxClassLines;
  }

  void check(TypeDeclaration<?> type, Path file, Consumer<Violation> violations) {
    if (type.getBegin().isEmpty() || type.getEnd().isEmpty()) {
      return;
    }

    int declarationLine = findDeclarationLine(type, file);
    int meaningfulLines = countMeaningfulLines(file, declarationLine, type.getEnd().get().line);
    if (meaningfulLines <= maxClassLines) {
      return;
    }

    violations.accept(
        new Violation(
            file,
            declarationLine,
            "class-too-long",
            type.getNameAsString(),
            "%s has %d meaningful lines (limit %d)"
                .formatted(type.getNameAsString(), meaningfulLines, maxClassLines)));
  }

  private int findDeclarationLine(TypeDeclaration<?> type, Path file) {
    int beginLine = type.getBegin().map(position -> position.line).orElse(1);
    int endLine = type.getEnd().map(position -> position.line).orElse(beginLine);
    List<String> lines = readLines(file);

    for (int i = beginLine - 1; i < Math.min(endLine, lines.size()); i++) {
      if (TYPE_DECLARATION_PATTERN.matcher(lines.get(i)).find()) {
        return i + 1;
      }
    }
    return beginLine;
  }

  private int countMeaningfulLines(Path file, int startLine, int endLine) {
    List<String> lines = readLines(file);
    int count = 0;
    for (int i = startLine - 1; i < Math.min(endLine, lines.size()); i++) {
      String trimmed = lines.get(i).trim();
      if (!trimmed.isEmpty() && !trimmed.startsWith("//")) {
        count++;
      }
    }
    return count;
  }

  private static List<String> readLines(Path file) {
    try {
      return Files.readAllLines(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read file: " + file, e);
    }
  }
}
