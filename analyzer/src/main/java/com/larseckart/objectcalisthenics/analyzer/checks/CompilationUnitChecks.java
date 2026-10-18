package com.larseckart.objectcalisthenics.analyzer.checks;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.larseckart.objectcalisthenics.analyzer.ExcludedClass;
import com.larseckart.objectcalisthenics.analyzer.PrimitiveObsessionConfig;
import com.larseckart.objectcalisthenics.analyzer.RuleSet;
import com.larseckart.objectcalisthenics.analyzer.Violation;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Walks a parsed Java file and applies the configured checks. */
public final class CompilationUnitChecks {

  private final RuleSet rules;
  private final List<ClassNamePattern> classNamePatterns;
  private final ClassLengthChecker classLengthChecker;
  private final FieldChecker fieldChecker;
  private final MethodChecker methodChecker;
  private final TraversalChainChecker traversalChainChecker;
  private final PrimitiveObsessionChecker primitiveObsessionChecker;
  private final RawTypePolicyChecker rawTypePolicyChecker;

  private record ClassNamePattern(String source, Pattern pattern) {}

  public CompilationUnitChecks(
      RuleSet rules, PrimitiveObsessionConfig primitiveObsession, List<String> classNamePatterns) {
    this.rules = rules;
    this.classNamePatterns =
        classNamePatterns.stream()
            .map(pattern -> new ClassNamePattern(pattern, Pattern.compile(pattern)))
            .toList();
    this.classLengthChecker = new ClassLengthChecker(rules.maxClassLines());
    this.fieldChecker = new FieldChecker(rules);
    this.methodChecker = new MethodChecker(rules);
    this.traversalChainChecker = new TraversalChainChecker(rules);
    this.primitiveObsessionChecker = new PrimitiveObsessionChecker(primitiveObsession.threshold());
    this.rawTypePolicyChecker = new RawTypePolicyChecker(primitiveObsession);
  }

  public void check(
      CompilationUnit unit, List<Violation> violations, List<ExcludedClass> excludedClasses) {
    Path file =
        unit.getStorage()
            .map(CompilationUnit.Storage::getPath)
            .orElseThrow(
                () -> new IllegalStateException("Parsed compilation unit has no file path"));

    if (rules.oneTopLevelTypePerFile()) {
      checkTopLevelTypes(unit, file, violations);
    }

    for (TypeDeclaration<?> type : unit.getTypes()) {
      checkTypeAndMembers(type, file, violations, excludedClasses);
    }

    if (rules.forbidTraversalChains()) {
      Set<String> excludedClassNames =
          excludedClasses.stream()
              .map(ExcludedClass::className)
              .collect(HashSet::new, HashSet::add, HashSet::addAll);
      traversalChainChecker.check(unit, file, excludedClassNames, violations::add);
    }
  }

  private void checkTopLevelTypes(CompilationUnit unit, Path file, List<Violation> violations) {
    if (unit.getTypes().size() < 2) {
      return;
    }
    // Keep the type named by the file, or the first type when none matches.
    String fileName = file.getFileName().toString();
    TypeDeclaration<?> primary =
        unit.getTypes().stream()
            .filter(type -> fileName.equals(type.getNameAsString() + ".java"))
            .findFirst()
            .orElse(unit.getType(0));
    for (TypeDeclaration<?> type : unit.getTypes()) {
      if (type != primary && matchingPatterns(type.getNameAsString()).isEmpty()) {
        violations.add(
            new Violation(
                file,
                type.getBegin().map(position -> position.line).orElse(1),
                "multiple-top-level-types",
                type.getNameAsString(),
                "%s must live in its own source file (one top-level type per file)"
                    .formatted(type.getNameAsString())));
      }
    }
  }

  private void checkTypeAndMembers(
      TypeDeclaration<?> type,
      Path file,
      List<Violation> violations,
      List<ExcludedClass> excludedClasses) {
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
    if (rules.forbidNonFirstClassCollections()) {
      new CollectionSignatureChecker().check(type, file, violations::add);
    }
    primitiveObsessionChecker.check(type, file, violations::add);
    rawTypePolicyChecker.check(type, file, violations::add);
  }
}
