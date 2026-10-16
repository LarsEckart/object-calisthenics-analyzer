package com.github.larseckart.objectcalisthenics.analyzer;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.Type;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Finds bare collections crossing method boundaries instead of owning their behaviour. */
class CollectionSignatureChecker {

  void check(TypeDeclaration<?> owner, Path file, Consumer<Violation> violations) {
    if (suppressed(owner)) {
      return;
    }
    for (var member : owner.getMembers()) {
      if (member instanceof CallableDeclaration<?> callable && !suppressed(callable)) {
        checkCallable(owner, callable, file, violations);
      }
    }
  }

  private void checkCallable(TypeDeclaration<?> owner, CallableDeclaration<?> callable,
      Path file, Consumer<Violation> violations) {
    String subject = owner.getNameAsString() + "." + callable.getNameAsString();
    checkReturnType(callable, file, subject, violations);
    // Wrappers need a way to receive the collection they own. Other constructors
    // still expose collections, just like ordinary methods do.
    if (callable instanceof ConstructorDeclaration && isWrapper(owner)) {
      return;
    }
    checkParameters(callable, file, subject, violations);
  }

  private void checkReturnType(CallableDeclaration<?> callable, Path file, String subject,
      Consumer<Violation> violations) {
    if (callable instanceof MethodDeclaration method
        && CollectionTypes.isCollectionType(method.getType())) {
      violations.accept(finding(method.getType(), file, subject,
          "returns bare collection type " + method.getType()));
    }
  }

  private void checkParameters(CallableDeclaration<?> callable, Path file, String subject,
      Consumer<Violation> violations) {
    for (var parameter : callable.getParameters()) {
      if (parameter.isVarArgs() || CollectionTypes.isCollectionType(parameter.getType())) {
        violations.accept(finding(parameter, file, subject,
            "accepts bare collection parameter '" + parameter.getNameAsString()
                + "' of type " + parameter.getType() + (parameter.isVarArgs() ? "..." : "")));
      }
    }
  }

  private boolean isWrapper(TypeDeclaration<?> owner) {
    List<Type> state;
    if (owner instanceof RecordDeclaration record) {
      state = record.getParameters().stream().map(parameter -> parameter.getType()).toList();
    } else {
      state = owner.getFields().stream().filter(field -> !field.isStatic())
          .flatMap(field -> field.getVariables().stream()).map(variable -> variable.getType()).toList();
    }
    return state.size() == 1 && CollectionTypes.isCollectionType(state.get(0));
  }

  private boolean suppressed(Node declaration) {
    return declaration.getChildNodes().stream()
        .filter(AnnotationExpr.class::isInstance).map(AnnotationExpr.class::cast)
        .filter(annotation -> annotation.getName().getIdentifier().equals("SuppressWarnings"))
        .flatMap(annotation -> annotation.findAll(StringLiteralExpr.class).stream())
        .anyMatch(value -> value.asString().equals("calisthenics:unwrapped-collection"));
  }

  private Violation finding(Node location, Path file, String subject, String detail) {
    return new Violation(file, location.getBegin().map(position -> position.line).orElse(1),
        "unwrapped-collection", subject,
        subject + " " + detail + "; consider an object that owns the collection and its operations");
  }
}
