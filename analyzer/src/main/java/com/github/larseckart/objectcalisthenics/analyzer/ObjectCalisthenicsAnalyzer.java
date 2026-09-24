package com.github.larseckart.objectcalisthenics.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Analyzes Java source files for Object Calisthenics violations.
 *
 * <p>The analyzer is intentionally independent of Gradle or any build tool.
 * Give it a source root directory and a {@link RuleSet}; it returns every
 * violation it finds.</p>
 */
public class ObjectCalisthenicsAnalyzer {

  private final RuleSet rules;
  private final List<ClassNamePattern> classNamePatterns;
  private final ParserConfiguration parserConfiguration;
  private final ClassLengthChecker classLengthChecker;
  private final FieldChecker fieldChecker;
  private final MethodChecker methodChecker;
  private final TraversalChainChecker traversalChainChecker;

  private record ClassNamePattern(String source, Pattern pattern) {
  }

  public ObjectCalisthenicsAnalyzer(RuleSet rules, List<String> classNamePatterns) {
    this.rules = rules;
    this.classNamePatterns = classNamePatterns.stream()
        .map(pattern -> new ClassNamePattern(pattern, Pattern.compile(pattern)))
        .toList();
    this.parserConfiguration = new ParserConfiguration()
        .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_26);
    this.classLengthChecker = new ClassLengthChecker(rules.maxClassLines());
    this.fieldChecker = new FieldChecker(rules);
    this.methodChecker = new MethodChecker(rules);
    this.traversalChainChecker = new TraversalChainChecker(rules);
  }

  public ObjectCalisthenicsAnalyzer(RuleSet rules) {
    this(rules, List.of());
  }

  public ObjectCalisthenicsAnalyzer() {
    this(RuleSet.defaults());
  }

  /**
   * Analyze all {@code .java} files under the given directory.
   */
  public AnalysisResult analyze(Path sourceRoot) {
    SourceRoot root = new SourceRoot(sourceRoot, parserConfiguration);
    List<Violation> violations = Collections.synchronizedList(new ArrayList<>());
    List<ExcludedClass> excludedClasses = Collections.synchronizedList(new ArrayList<>());

    root.tryToParseParallelized().forEach(result -> {
      if (result.isSuccessful() && result.getResult().isPresent()) {
        CompilationUnit unit = result.getResult().get();
        checkCompilationUnit(unit, violations, excludedClasses);
      }
    });

    return new AnalysisResult(violations, excludedClasses);
  }

  /**
   * Analyze the supplied Java source files directly.
   */
  public AnalysisResult analyze(List<Path> files) {
    JavaParser parser = new JavaParser(parserConfiguration);
    List<Violation> violations = new ArrayList<>();
    List<ExcludedClass> excludedClasses = new ArrayList<>();

    for (Path file : files) {
      try {
        parser.parse(file).getResult().ifPresent(unit -> checkCompilationUnit(unit, violations, excludedClasses));
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to parse " + file, e);
      }
    }

    return new AnalysisResult(violations, excludedClasses);
  }

  private void checkCompilationUnit(
      CompilationUnit unit,
      List<Violation> violations,
      List<ExcludedClass> excludedClasses
  ) {
    Path file = unit.getStorage()
        .map(CompilationUnit.Storage::getPath)
        .orElseThrow(() -> new IllegalStateException("Parsed compilation unit has no file path"));

    for (TypeDeclaration<?> type : unit.getTypes()) {
      checkTypeAndMembers(type, file, violations, excludedClasses);
    }

    if (rules.forbidTraversalChains()) {
      Set<String> excludedClassNames = excludedClasses.stream()
          .map(ExcludedClass::className)
          .collect(HashSet::new, HashSet::add, HashSet::addAll);
      traversalChainChecker.check(unit, file, excludedClassNames, violations::add);
    }
  }

  private void checkTypeAndMembers(
      TypeDeclaration<?> type,
      Path file,
      List<Violation> violations,
      List<ExcludedClass> excludedClasses
  ) {
    List<String> matchedPatterns = matchingPatterns(type.getNameAsString());
    if (matchedPatterns.isEmpty()) {
      checkType(type, file, violations);
      for (BodyDeclaration<?> member : type.getMembers()) {
        if (member instanceof MethodDeclaration method) {
          methodChecker.check(method, file, violations::add);
        } else if (member instanceof TypeDeclaration<?> nestedType) {
          checkTypeAndMembers(nestedType, file, violations, excludedClasses);
        }
      }
    } else {
      excludedClasses.add(new ExcludedClass(file, type.getNameAsString(), matchedPatterns));
    }
  }

  private List<String> matchingPatterns(String simpleClassName) {
    return classNamePatterns.stream()
        .filter(pattern -> pattern.pattern().matcher(simpleClassName).matches())
        .map(ClassNamePattern::source)
        .toList();
  }

  private void checkType(TypeDeclaration<?> type, Path file, List<Violation> violations) {
    classLengthChecker.check(type, file, violations::add);
    fieldChecker.check(type, file, violations::add);
  }
}
