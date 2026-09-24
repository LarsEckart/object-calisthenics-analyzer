package com.github.larseckart.objectcalisthenics.analyzer;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.expr.ThisExpr;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Detects methods that look like getters.
 */
class GetterChecker {

  private final boolean strictNames;

  GetterChecker(boolean strictNames) {
    this.strictNames = strictNames;
  }

  void check(MethodDeclaration method, Path file, Consumer<Violation> violations) {
    if (!looksLikeGetter(method)) {
      return;
    }

    violations.accept(
        new Violation(
            file,
            method.getBegin().map(position -> position.line).orElse(0),
            "getter",
            method.getNameAsString(),
            "Method '%s' looks like a getter".formatted(method.getNameAsString())));
  }

  boolean looksLikeGetter(MethodDeclaration method) {
    if (!hasGetterPrefix(method.getNameAsString())) {
      return false;
    }
    if (method.getType().isVoidType() || !method.getParameters().isEmpty()) {
      return false;
    }
    if (strictNames) {
      return true;
    }

    String property = propertyName(method.getNameAsString());
    return declaresProperty(method, property) || directlyReturnsState(method);
  }

  private boolean hasGetterPrefix(String name) {
    if (name.startsWith("get")) {
      return isCapitalAt(name, 3);
    }
    if (name.startsWith("is")) {
      return isCapitalAt(name, 2);
    }
    return false;
  }

  private static boolean isCapitalAt(String name, int index) {
    return name.length() > index && Character.isUpperCase(name.charAt(index));
  }

  private static String propertyName(String methodName) {
    int prefixLength = methodName.startsWith("is") ? 2 : 3;
    return decapitalize(methodName.substring(prefixLength));
  }

  private static String decapitalize(String name) {
    if (hasTwoUppercasePrefix(name)) {
      return name;
    }
    return name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1);
  }

  private static boolean hasTwoUppercasePrefix(String name) {
    return name.length() > 1
        && Character.isUpperCase(name.charAt(0))
        && Character.isUpperCase(name.charAt(1));
  }

  private boolean declaresProperty(MethodDeclaration method, String property) {
    return declaringType(method)
        .map(type -> hasProperty(type, property))
        .orElse(false);
  }

  private static boolean hasProperty(TypeDeclaration<?> type, String property) {
    if (type instanceof RecordDeclaration record) {
      return record.getParameters().stream()
          .map(Parameter::getNameAsString)
          .anyMatch(property::equals);
    }
    if (type instanceof ClassOrInterfaceDeclaration classType) {
      return classType.getFields().stream()
          .flatMap(field -> field.getVariables().stream())
          .anyMatch(variable -> variable.getNameAsString().equals(property));
    }
    return false;
  }

  private static Optional<TypeDeclaration<?>> declaringType(MethodDeclaration method) {
    Optional<Node> current = method.getParentNode();
    while (current.isPresent()) {
      if (current.get() instanceof TypeDeclaration<?> type) {
        return Optional.of(type);
      }
      current = current.get().getParentNode();
    }
    return Optional.empty();
  }

  private static boolean directlyReturnsState(MethodDeclaration method) {
    if (method.getBody().isEmpty() || method.getBody().get().getStatements().size() != 1) {
      return false;
    }
    return method.getBody().get().getStatement(0)
        .toReturnStmt()
        .flatMap(ReturnStmt::getExpression)
        .map(GetterChecker::unwrap)
        .map(expression -> expression instanceof NameExpr
            || expression instanceof FieldAccessExpr field
            && field.getScope() instanceof ThisExpr)
        .orElse(false);
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
}
