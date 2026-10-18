package com.larseckart.objectcalisthenics.analyzer.parsing;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.utils.SourceRoot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reads Java files and passes their syntax trees to the next analysis step.
 */
public final class JavaSourceParser {

  private final ParserConfiguration configuration =
      new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_26);

  /**
   * Parses a source tree, skipping files that JavaParser cannot parse successfully.
   */
  public void parse(Path sourceRoot, Consumer<CompilationUnit> compilationUnits) {
    SourceRoot root = new SourceRoot(sourceRoot, configuration);
    root.tryToParseParallelized()
        .forEach(
            parseResult -> {
              if (parseResult.isSuccessful()) {
                parseResult.getResult().ifPresent(compilationUnits);
              }
            });
  }

  /**
   * Parses selected files, including any partial syntax trees JavaParser returns.
   */
  public void parse(List<Path> files, Consumer<CompilationUnit> compilationUnits) {
    JavaParser parser = new JavaParser(configuration);
    for (Path file : files) {
      try {
        parser.parse(file).getResult().ifPresent(compilationUnits);
      } catch (IOException exception) {
        throw new UncheckedIOException("Failed to parse " + file, exception);
      }
    }
  }
}
