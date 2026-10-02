package com.github.larseckart.objectcalisthenics.analyzer;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.nio.file.Path;
import java.util.List;
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

    NestingDepthCalculator.Nesting nesting = nestingDepthCalculator.measure(method);
    int depth = nesting.depth();
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
                .formatted(method.getNameAsString(), depth, rules.maxMethodNesting()))
            .withContext(nestingContext(nesting)));
  }

  private static ViolationContext nestingContext(NestingDepthCalculator.Nesting nesting) {
    String deepestSource = describeNestedSource(nesting.deepestNode());
    return new ViolationContext(
        "nested-block",
        "The method reaches nesting depth %d at %s.".formatted(nesting.depth(), deepestSource),
        List.of(deepestSource),
        "Consider extracting this block into a method named for the work it performs.",
        "Domain judgement is needed to choose a useful boundary and preserve side effects and error handling.");
  }

  private static String describeNestedSource(Node deepestNode) {
    Node source = deepestNode instanceof BlockStmt
        ? deepestNode.getParentNode().orElse(deepestNode)
        : deepestNode;
    String shape = source instanceof ClassOrInterfaceDeclaration || source instanceof RecordDeclaration
        ? localTypeShape(source)
        : sourceShape(source);
    int line = source.getBegin().map(position -> position.line).orElse(0);
    return "%s at line %d".formatted(shape, line);
  }

  private static String sourceShape(Node source) {
    if (source instanceof IfStmt statement) {
      return "if (" + statement.getCondition() + ")";
    }
    if (source instanceof WhileStmt statement) {
      return "while (" + statement.getCondition() + ")";
    }
    if (source instanceof DoStmt statement) {
      return "do ... while (" + statement.getCondition() + ")";
    }
    if (source instanceof ForEachStmt statement) {
      return "for (" + statement.getVariable() + " : " + statement.getIterable() + ")";
    }
    if (source instanceof ForStmt) {
      return "for loop";
    }
    if (source instanceof TryStmt) {
      return "try block";
    }
    if (source instanceof LambdaExpr) {
      return "lambda body";
    }
    return "nested " + source.getClass().getSimpleName();
  }

  private static String localTypeShape(Node source) {
    if (source instanceof ClassOrInterfaceDeclaration declaration) {
      return "local class " + declaration.getNameAsString();
    }
    return "local record " + ((RecordDeclaration) source).getNameAsString();
  }

  private void checkSetter(MethodDeclaration method, Path file, Consumer<Violation> violations) {
    if (!rules.forbidSetters() || !looksLikeSetter(method)) {
      return;
    }
    violations.accept(
        new Violation(
            file,
            method.getBegin().map(position -> position.line).orElse(0),
            "setter",
            method.getNameAsString(),
            "Method '%s' looks like a setter".formatted(method.getNameAsString()))
            .withContext(setterContext(method)));
  }

  private static ViolationContext setterContext(MethodDeclaration method) {
    String parameter = method.getParameter(0).getTypeAsString()
        + " " + method.getParameter(0).getNameAsString();
    return new ViolationContext(
        "accessor-pattern",
        "The method has a setter-shaped name, void return type, and one parameter.",
        List.of(parameter),
        "Consider reviewing callers to identify the operation they expect this state change to perform.",
        "Domain judgement is needed to name that operation and to recognize framework binding boundaries.");
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
