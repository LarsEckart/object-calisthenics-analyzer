package com.github.larseckart.objectcalisthenics.analyzer;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.IfStmt;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Checks method-level Object Calisthenics rules.
 */
class MethodChecker {

  private final RuleSet rules;
  private final GetterChecker getterChecker;
  private final NestingDepthCalculator nestingDepthCalculator;

  MethodChecker(RuleSet rules) {
    this.rules = rules;
    this.getterChecker = new GetterChecker(rules.strictGetterNames());
    this.nestingDepthCalculator = new NestingDepthCalculator();
  }

  void check(MethodDeclaration method, Path file, Consumer<Violation> violations) {
    if (!method.getBody().isPresent() || !isPublic(method)) {
      return;
    }

    checkElse(method, file, violations);
    checkNesting(method, file, violations);
    getterChecker.check(method, file, violations);
    checkSetter(method, file, violations);
  }

  private static boolean isPublic(MethodDeclaration method) {
    return method.isPublic()
        || method.findAncestor(ClassOrInterfaceDeclaration.class)
            .map(ClassOrInterfaceDeclaration::isInterface)
            .orElse(false);
  }

  private void checkElse(MethodDeclaration method, Path file, Consumer<Violation> violations) {
    if (!rules.forbidElse() || !containsElse(method)) {
      return;
    }
    violations.accept(methodViolation(method, file, "else-used", "uses the else keyword"));
  }

  private static boolean containsElse(MethodDeclaration method) {
    return method.findAll(IfStmt.class).stream().anyMatch(ifStmt -> ifStmt.getElseStmt().isPresent());
  }

  private void checkNesting(MethodDeclaration method, Path file, Consumer<Violation> violations) {
    if (rules.maxMethodNesting() < 0 || method.isAbstract()) {
      return;
    }

    int depth = nestingDepthCalculator.compute(method);
    if (depth <= rules.maxMethodNesting()) {
      return;
    }

    violations.accept(
        new Violation(
            file,
            method.getBegin().map(position -> position.line).orElse(0),
            "method-over-nested",
            method.getNameAsString(),
            "Method '%s' nests %d levels deep (limit %d)"
                .formatted(method.getNameAsString(), depth, rules.maxMethodNesting())));
  }

  private void checkSetter(MethodDeclaration method, Path file, Consumer<Violation> violations) {
    if (!rules.forbidSetters() || !looksLikeSetter(method)) {
      return;
    }
    violations.accept(methodViolation(method, file, "setter", "looks like a setter"));
  }

  private static Violation methodViolation(
      MethodDeclaration method, Path file, String rule, String description) {
    return new Violation(
        file,
        method.getBegin().map(position -> position.line).orElse(0),
        rule,
        method.getNameAsString(),
        "Method '%s' %s".formatted(method.getNameAsString(), description));
  }

  private static boolean looksLikeSetter(MethodDeclaration method) {
    String name = method.getNameAsString();
    return name.startsWith("set")
        && name.length() > 3
        && Character.isUpperCase(name.charAt(3))
        && method.getType().isVoidType()
        && method.getParameters().size() == 1;
  }
}
