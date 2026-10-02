package com.github.larseckart.objectcalisthenics.analyzer;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Enforces the configured policy for raw primitive and String declarations. */
class RawTypePolicyChecker {

  private static final Set<String> BOXED_AND_STRING_TYPES = Set.of(
      "Boolean", "Byte", "Character", "Double", "Float", "Integer", "Long", "Short", "String");

  private final Set<String> allowedRawTypes;
  private final List<Pattern> boundaryClassNamePatterns;

  private record FindingContext(
      TypeDeclaration<?> owner,
      Path file,
      Consumer<Violation> violations
  ) {
  }

  RawTypePolicyChecker(PrimitiveObsessionConfig config) {
    allowedRawTypes = config.allowedRawTypes();
    boundaryClassNamePatterns = config.boundaryClassNamePatterns().stream()
        .map(Pattern::compile)
        .toList();
  }

  void check(TypeDeclaration<?> type, Path file, Consumer<Violation> violations) {
    if (isBoundary(type)) {
      return;
    }
    if (isSuppressed(type)) {
      return;
    }
    if (isPrimitiveWrapper(type)) {
      return;
    }

    FindingContext context = new FindingContext(type, file, violations);
    type.getFields().stream()
        .filter(field -> !field.isStatic())
        .forEach(field -> checkField(field, context));
    if (type instanceof RecordDeclaration record) {
      record.getParameters().forEach(parameter ->
          report(parameter.getType(), parameter.getNameAsString(), parameter, context));
    }
    type.getMembers().forEach(member -> {
      if (member instanceof CallableDeclaration<?> callable) {
        callable.getParameters().forEach(parameter ->
            report(parameter.getType(), parameter.getNameAsString(), parameter, context));
      }
    });
  }

  private void checkField(FieldDeclaration field, FindingContext context) {
    field.getVariables().forEach(variable ->
        report(variable.getType(), variable.getNameAsString(), variable, context));
  }

  private void report(
      Type declarationType,
      String name,
      com.github.javaparser.ast.Node declaration,
      FindingContext context
  ) {
    String rawType = rawTypeName(declarationType);
    if (rawType == null || allowedRawTypes.contains(rawType)) {
      return;
    }
    context.violations().accept(new Violation(
        context.file(),
        declaration.getBegin().map(position -> position.line).orElse(0),
        "raw-domain-primitive",
        name,
        "%s.%s uses raw %s, which is not allowed by the configured policy"
            .formatted(context.owner().getNameAsString(), name, rawType)));
  }

  private String rawTypeName(Type type) {
    if (type.isPrimitiveType()) {
      return type.asPrimitiveType().asString();
    }
    if (type instanceof ClassOrInterfaceType classType
        && BOXED_AND_STRING_TYPES.contains(classType.getNameAsString())) {
      return classType.getNameAsString();
    }
    return null;
  }

  private boolean isBoundary(TypeDeclaration<?> type) {
    return boundaryClassNamePatterns.stream()
        .anyMatch(pattern -> pattern.matcher(type.getNameAsString()).matches());
  }

  private boolean isSuppressed(TypeDeclaration<?> type) {
    return type.getAnnotations().stream()
        .filter(annotation -> annotation.getName().getIdentifier().equals("SuppressWarnings"))
        .flatMap(annotation -> annotation.findAll(StringLiteralExpr.class).stream())
        .anyMatch(value -> value.asString().equals("calisthenics:raw-domain-primitive"));
  }

  private boolean isPrimitiveWrapper(TypeDeclaration<?> type) {
    return type instanceof RecordDeclaration record
        && record.getParameters().size() == 1
        && rawTypeName(record.getParameter(0).getType()) != null;
  }
}
