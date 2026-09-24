package com.github.larseckart.objectcalisthenics.analyzer;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Checks field-related Object Calisthenics rules for a type declaration.
 *
 * <p>This includes too many instance fields/record components, suppressed field
 * rules, and first-class collection violations.</p>
 */
class FieldChecker {

  // Simple name matching for JDK collection types and their common implementations.
  // This intentionally does not require JavaParser's symbol solver.
  private static final Set<String> JDK_COLLECTION_TYPE_NAMES =
      Set.of(
          "Collection",
          "List", "ArrayList", "LinkedList", "CopyOnWriteArrayList",
          "Set", "HashSet", "LinkedHashSet", "TreeSet", "SortedSet", "NavigableSet", "EnumSet",
          "CopyOnWriteArraySet",
          "Map", "HashMap", "LinkedHashMap", "TreeMap", "SortedMap", "NavigableMap", "EnumMap",
          "ConcurrentMap", "ConcurrentHashMap", "ConcurrentNavigableMap",
          "Hashtable", "Properties", "Dictionary",
          "Vector", "Stack",
          "Queue", "Deque", "ArrayDeque", "PriorityQueue",
          "BlockingQueue", "LinkedBlockingQueue", "ArrayBlockingQueue", "PriorityBlockingQueue",
          "DelayQueue", "SynchronousQueue", "LinkedTransferQueue", "TransferQueue",
          "ConcurrentLinkedQueue", "ConcurrentLinkedDeque", "LinkedBlockingDeque", "BlockingDeque",
          "Iterable");

  private final RuleSet rules;

  FieldChecker(RuleSet rules) {
    this.rules = rules;
  }

  void check(TypeDeclaration<?> type, Path file, Consumer<Violation> violations) {
    if (suppressesFieldRule(type)) {
      return;
    }

    if (type instanceof ClassOrInterfaceDeclaration classType && !classType.isInterface()) {
      checkClassFields(classType, file, violations);
    } else if (type instanceof RecordDeclaration record && rules.includeRecordComponentsInFieldRule()) {
      checkRecordFields(record, file, violations);
    }

    checkFirstClassCollections(type, file, violations);
  }

  private boolean suppressesFieldRule(TypeDeclaration<?> type) {
    return type.getAnnotations().stream()
        .filter(annotation -> annotation.getName().getIdentifier().equals("SuppressWarnings"))
        .flatMap(annotation -> annotation.findAll(StringLiteralExpr.class).stream())
        .anyMatch(value -> value.asString().equals("calisthenics:fields"));
  }

  private void checkClassFields(
      ClassOrInterfaceDeclaration type, Path file, Consumer<Violation> violations) {
    long instanceFields = instanceFieldCount(type);
    if (instanceFields <= rules.maxFieldsPerClass()) {
      return;
    }

    violations.accept(
        new Violation(
            file,
            type.getBegin().map(position -> position.line).orElse(0),
            "too-many-instance-fields",
            type.getNameAsString(),
            "%s has %d instance fields (limit %d)"
                .formatted(type.getNameAsString(), instanceFields, rules.maxFieldsPerClass())));
  }

  private static long instanceFieldCount(ClassOrInterfaceDeclaration type) {
    return type.getFields().stream()
        .filter(field -> !field.isStatic())
        .mapToLong(field -> field.getVariables().size())
        .sum();
  }

  private void checkRecordFields(
      RecordDeclaration record, Path file, Consumer<Violation> violations) {
    int components = record.getParameters().size();
    if (components <= rules.maxFieldsPerClass()) {
      return;
    }

    violations.accept(
        new Violation(
            file,
            record.getBegin().map(position -> position.line).orElse(0),
            "too-many-record-components",
            record.getNameAsString(),
            "%s has %d record components (limit %d)"
                .formatted(record.getNameAsString(), components, rules.maxFieldsPerClass())));
  }

  private void checkFirstClassCollections(
      TypeDeclaration<?> type, Path file, Consumer<Violation> violations) {
    if (!rules.forbidNonFirstClassCollections()) {
      return;
    }

    List<FieldInfo> fields = fieldInfos(type);
    long collectionFields = fields.stream().filter(field -> isCollectionType(field.type())).count();
    long otherFields = fields.size() - collectionFields;

    if (collectionFields == 0) {
      return;
    }
    if (otherFields == 0 && collectionFields == 1) {
      return;
    }

    violations.accept(
        new Violation(
            file,
            type.getBegin().map(position -> position.line).orElse(0),
            "non-first-class-collection",
            type.getNameAsString(),
            "%s is not a first-class collection: %d collection field(s) and %d other instance field(s)"
                .formatted(type.getNameAsString(), collectionFields, otherFields)));
  }

  private List<FieldInfo> fieldInfos(TypeDeclaration<?> type) {
    if (type instanceof ClassOrInterfaceDeclaration classType && !classType.isInterface()) {
      return classType.getFields().stream()
          .filter(field -> !field.isStatic())
          .flatMap(field -> field.getVariables().stream())
          .map(variable -> new FieldInfo(variable.getNameAsString(), variable.getType()))
          .toList();
    }
    if (type instanceof RecordDeclaration record) {
      return record.getParameters().stream()
          .map(parameter -> new FieldInfo(parameter.getNameAsString(), parameter.getType()))
          .toList();
    }
    return List.of();
  }

  private boolean isCollectionType(Type type) {
    if (type.isArrayType()) {
      return true;
    }
    if (type.isClassOrInterfaceType()) {
      String simpleName = simpleName(type.asClassOrInterfaceType());
      return JDK_COLLECTION_TYPE_NAMES.contains(simpleName);
    }
    return false;
  }

  private static String simpleName(ClassOrInterfaceType type) {
    String raw = type.asString();
    int genericStart = raw.indexOf('<');
    if (genericStart >= 0) {
      raw = raw.substring(0, genericStart);
    }
    int lastDot = raw.lastIndexOf('.');
    return lastDot >= 0 ? raw.substring(lastDot + 1) : raw;
  }

  private record FieldInfo(String name, Type type) {
  }
}
