package com.github.larseckart.objectcalisthenics.analyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.expr.ThisExpr;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Detects traversal chains that violate the Law of Demeter.
 */
class TraversalChainChecker {

  private final RuleSet rules;

  TraversalChainChecker(RuleSet rules) {
    this.rules = rules;
  }

  void check(
      CompilationUnit unit,
      Path file,
      Set<String> excludedClassNames,
      Consumer<Violation> violations) {
    if (!rules.forbidTraversalChains()) {
      return;
    }

    List<Expression> candidates = new ArrayList<>();
    candidates.addAll(unit.findAll(MethodCallExpr.class));
    candidates.addAll(unit.findAll(FieldAccessExpr.class));

    candidates.stream()
        .filter(expression -> !isReceiverPrefix(expression))
        .filter(expression -> !insideExcludedType(expression, excludedClassNames))
        .forEach(expression -> addTraversalViolation(expression, file, violations));
  }

  private boolean insideExcludedType(Expression expression, Set<String> excludedClassNames) {
    Optional<TypeDeclaration<?>> current = typeAncestor(expression);
    while (current.isPresent()) {
      if (excludedClassNames.contains(current.get().getNameAsString())) {
        return true;
      }
      current = typeAncestor(current.get());
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private static Optional<TypeDeclaration<?>> typeAncestor(Node node) {
    return node.findAncestor(TypeDeclaration.class).map(ancestor -> (TypeDeclaration<?>) ancestor);
  }

  private void addTraversalViolation(
      Expression expression, Path file, Consumer<Violation> violations) {
    TraversalChain chain = buildTraversalChain(expression);
    int traversalSteps = countTraversalSteps(chain);
    if (traversalSteps <= 1) {
      return;
    }

    String rendered = expression.toString().replaceAll("\\s+", " ");
    String subject = subjectOf(expression);
    violations.accept(
        new Violation(
            file,
            expression.getBegin().map(position -> position.line).orElse(0),
            "traversal-chain",
            subject,
            "Traversal chain has %d steps: %s".formatted(traversalSteps, rendered)));
  }

  private static String subjectOf(Expression expression) {
    return expression.findAncestor(MethodDeclaration.class)
        .map(MethodDeclaration::getNameAsString)
        .orElseGet(
            () -> expression.findAncestor(TypeDeclaration.class)
                .map(TypeDeclaration::getNameAsString)
                .orElse(""));
  }

  private int countTraversalSteps(TraversalChain chain) {
    int safeRootSteps = safeRootSteps(chain);
    int steps = chain.steps().size() - safeRootSteps;

    for (int i = safeRootSteps; i < chain.steps().size() - 1; i++) {
      ChainStep step = chain.steps().get(i);
      if (step.method() && rules.fluentChainMethods().contains(step.name())) {
        steps--;
      }
    }
    return steps;
  }

  private boolean isReceiverPrefix(Expression expression) {
    Optional<Expression> current = Optional.of(expression);
    Optional<Expression> transparent;
    while ((transparent = current.flatMap(TraversalChainChecker::transparentParent)).isPresent()) {
      current = transparent;
    }

    Expression finalCurrent = current.orElse(expression);
    return finalCurrent.getParentNode()
        .map(parent -> isReceiverIn(parent, finalCurrent))
        .orElse(false);
  }

  private static Optional<Expression> transparentParent(Expression expression) {
    return expression.getParentNode()
        .filter(parent -> isTransparentParent(parent, expression))
        .map(parent -> (Expression) parent);
  }

  private static boolean isTransparentParent(Node parent, Expression expression) {
    return (parent instanceof EnclosedExpr enclosed && enclosed.getInner() == expression)
        || (parent instanceof CastExpr cast && cast.getExpression() == expression)
        || (parent instanceof ArrayAccessExpr array && array.getName() == expression);
  }

  private static boolean isReceiverIn(Node parent, Expression expression) {
    if (parent instanceof MethodCallExpr call) {
      return call.getScope().isPresent() && call.getScope().get() == expression;
    }
    if (parent instanceof FieldAccessExpr field) {
      return field.getScope() == expression;
    }
    return false;
  }

  private TraversalChain buildTraversalChain(Expression expression) {
    List<ChainStep> steps = new ArrayList<>();
    String root = collectTraversal(expression, steps);
    return new TraversalChain(root, List.copyOf(steps));
  }

  private String collectTraversal(Expression expression, List<ChainStep> steps) {
    Expression unwrapped = unwrap(expression);

    if (unwrapped instanceof MethodCallExpr call) {
      String root = call.getScope()
          .map(scope -> collectTraversal(scope, steps))
          .orElse("");
      steps.add(new ChainStep(call.getNameAsString(), true));
      return root;
    }

    if (unwrapped instanceof FieldAccessExpr field) {
      String root = collectTraversal(field.getScope(), steps);
      steps.add(new ChainStep(field.getNameAsString(), false));
      return root;
    }

    if (unwrapped instanceof ArrayAccessExpr array) {
      return collectTraversal(array.getName(), steps);
    }

    if (unwrapped instanceof NameExpr name) {
      return name.getNameAsString();
    }

    if (unwrapped instanceof ThisExpr) {
      return "this";
    }

    if (unwrapped instanceof SuperExpr) {
      return "super";
    }

    return unwrapped.toString();
  }

  private static Expression unwrap(Expression expression) {
    Expression current = expression;
    while (current instanceof EnclosedExpr || current instanceof CastExpr) {
      if (current instanceof EnclosedExpr enclosed) {
        current = enclosed.getInner();
      } else {
        current = current.asCastExpr().getExpression();
      }
    }
    return current;
  }

  private int safeRootSteps(TraversalChain chain) {
    int fromThisOrSuper = safePrefixFromThisOrSuper(chain.root(), chain.steps());
    return Math.max(fromThisOrSuper, bestSafeRootMatch(chain));
  }

  private static int safePrefixFromThisOrSuper(String root, List<ChainStep> steps) {
    if (!root.equals("this") && !root.equals("super")) {
      return 0;
    }
    if (steps.isEmpty()) {
      return 0;
    }
    return steps.get(0).method() ? 0 : 1;
  }

  private int bestSafeRootMatch(TraversalChain chain) {
    int bestMatch = 0;
    StringBuilder path = new StringBuilder(chain.root());

    int stepIndex = 0;
    for (ChainStep step : chain.steps()) {
      if (step.method()) {
        break;
      }
      path.append('.').append(step.name());
      if (rules.safeChainRoots().contains(path.toString())) {
        bestMatch = stepIndex + 1;
      }
      stepIndex++;
    }
    return bestMatch;
  }

  private record ChainStep(String name, boolean method) {
  }

  private record TraversalChain(String root, List<ChainStep> steps) {
  }
}
