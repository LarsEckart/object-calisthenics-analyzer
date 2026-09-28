package com.github.larseckart.objectcalisthenics.analyzer;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.AssertStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Finds repeated primitive concepts that have accumulated domain behaviour.
 *
 * <p>This is deliberately a heuristic rather than a ban on primitives. A
 * concept must flow between declarations and participate in validation,
 * normalization, literal comparison, or numeric arithmetic before it can be reported.</p>
 */
class PrimitiveObsessionChecker {

  private static final Set<String> BOXED_PRIMITIVES = Set.of(
      "Boolean", "Byte", "Character", "Double", "Float", "Integer", "Long", "Short", "String");
  private static final Set<String> LOW_INFORMATION_CONCEPT_NAMES = Set.of("value");
  private static final Set<String> ARRAY_POSITION_NAMES = Set.of("length", "offset");
  private static final Set<String> NON_NUMERIC_TYPES = Set.of("boolean", "Boolean", "String");
  private static final Map<Class<?>, String> LITERAL_TYPES = Map.of(
      StringLiteralExpr.class, "String",
      com.github.javaparser.ast.expr.BooleanLiteralExpr.class, "boolean",
      CharLiteralExpr.class, "char",
      IntegerLiteralExpr.class, "int",
      LongLiteralExpr.class, "long");
  private static final Set<String> VALIDATION_METHODS = Set.of(
      "isBlank", "isEmpty", "matches", "requireNonNull", "checkArgument", "checkState");
  private static final Set<String> TRANSFORMATION_METHODS = Set.of(
      "trim", "strip", "stripLeading", "stripTrailing", "toLowerCase", "toUpperCase",
      "substring", "replace", "replaceAll", "split", "parseBoolean", "parseByte",
      "parseDouble", "parseFloat", "parseInt", "parseLong", "parseShort", "format");
  private static final Set<BinaryExpr.Operator> ARITHMETIC_OPERATORS = Set.of(
      BinaryExpr.Operator.PLUS,
      BinaryExpr.Operator.MINUS,
      BinaryExpr.Operator.MULTIPLY,
      BinaryExpr.Operator.DIVIDE,
      BinaryExpr.Operator.REMAINDER);
  private static final Set<BinaryExpr.Operator> COMPARISON_OPERATORS = Set.of(
      BinaryExpr.Operator.EQUALS,
      BinaryExpr.Operator.NOT_EQUALS,
      BinaryExpr.Operator.LESS,
      BinaryExpr.Operator.LESS_EQUALS,
      BinaryExpr.Operator.GREATER,
      BinaryExpr.Operator.GREATER_EQUALS);

  private final int threshold;

  PrimitiveObsessionChecker(int threshold) {
    this.threshold = threshold;
  }

  void check(TypeDeclaration<?> type, Path file, Consumer<Violation> violations) {
    if (threshold < 0) {
      return;
    }
    if (alreadyEncapsulated(type)) {
      return;
    }

    Map<Concept, Evidence> evidenceByConcept = declarations(type);
    List<CallableDeclaration<?>> callables = callables(type);
    markParameterFlow(type, callables, evidenceByConcept);
    findTravellingConcepts(callables, evidenceByConcept);
    evidenceByConcept.forEach((concept, evidence) -> inspectCallables(concept, evidence, callables));

    evidenceByConcept.entrySet().stream()
        .filter(entry -> !LOW_INFORMATION_CONCEPT_NAMES.contains(entry.getKey().name()))
        .filter(entry -> !entry.getValue().isArrayPosition(entry.getKey()))
        .filter(entry -> entry.getValue().declarationCount() > 1)
        .filter(entry -> entry.getValue().hasBehaviour())
        .filter(entry -> entry.getValue().score() >= threshold)
        .sorted(Map.Entry.comparingByKey(
            Comparator.comparing(Concept::name).thenComparing(Concept::type)))
        .forEach(entry -> violations.accept(violation(file, entry.getKey(), entry.getValue())));
  }

  private static boolean alreadyEncapsulated(TypeDeclaration<?> type) {
    if (type.isEnumDeclaration()) {
      return true;
    }
    if (suppressesRule(type)) {
      return true;
    }
    return isPrimitiveWrapper(type);
  }

  private static Map<Concept, Evidence> declarations(TypeDeclaration<?> type) {
    Map<Concept, Evidence> evidence = new LinkedHashMap<>();
    type.getMembers().stream()
        .filter(FieldDeclaration.class::isInstance)
        .map(FieldDeclaration.class::cast)
        .filter(field -> !field.isStatic())
        .flatMap(field -> field.getVariables().stream())
        .forEach(variable -> addDeclaration(evidence, variable));

    if (type instanceof RecordDeclaration record) {
      record.getParameters().forEach(parameter -> addDeclaration(evidence, parameter, null));
    }

    callables(type).forEach(callable -> callable.getParameters()
        .forEach(parameter -> addDeclaration(evidence, parameter, callable)));
    return evidence;
  }

  private static void addDeclaration(
      Map<Concept, Evidence> evidence,
      VariableDeclarator variable) {
    primitiveType(variable.getType()).ifPresent(type -> registerDeclaration(
        evidence, new Concept(variable.getNameAsString(), type), variable, null));
  }

  private static void addDeclaration(
      Map<Concept, Evidence> evidence,
      Parameter parameter,
      CallableDeclaration<?> callable) {
    primitiveType(parameter.getType()).ifPresent(type -> registerDeclaration(
        evidence, new Concept(parameter.getNameAsString(), type), parameter, callable));
  }

  private static void registerDeclaration(
      Map<Concept, Evidence> evidence,
      Concept concept,
      Node declaration,
      CallableDeclaration<?> callable) {
    Evidence conceptEvidence = evidence.computeIfAbsent(concept, ignored -> new Evidence());
    conceptEvidence.declarations.add(declaration);
    conceptEvidence.registerParameter(callable, declaration);
  }

  private static List<CallableDeclaration<?>> callables(TypeDeclaration<?> type) {
    return type.getMembers().stream()
        .filter(CallableDeclaration.class::isInstance)
        .<CallableDeclaration<?>>map(member -> (CallableDeclaration<?>) member)
        .toList();
  }

  private static void markParameterFlow(
      TypeDeclaration<?> owner,
      List<CallableDeclaration<?>> callables,
      Map<Concept, Evidence> evidenceByConcept) {
    FlowContext context = new FlowContext(owner, callables, evidenceByConcept);
    for (CallableDeclaration<?> caller : callables) {
      for (MethodCallExpr call : caller.findAll(MethodCallExpr.class)) {
        markParameterFlow(context, caller, call);
      }
    }
  }

  private static void markParameterFlow(
      FlowContext context, CallableDeclaration<?> caller, MethodCallExpr call) {
    if (!isLocalCall(call, context.owner())) {
      return;
    }
    matchingCallee(context.callables(), caller, call)
        .ifPresent(callee -> markFlowingArguments(
            caller, call, callee, context.evidenceByConcept()));
  }

  private static Optional<CallableDeclaration<?>> matchingCallee(
      List<CallableDeclaration<?>> callables,
      CallableDeclaration<?> caller,
      MethodCallExpr call) {
    List<CallableDeclaration<?>> candidates = callables.stream()
        .filter(MethodDeclaration.class::isInstance)
        .filter(callee -> callee.getNameAsString().equals(call.getNameAsString()))
        .filter(callee -> callee.getParameters().size() == call.getArguments().size())
        .filter(callee -> argumentsCouldMatch(caller, call, callee))
        .toList();
    return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
  }

  private static boolean isLocalCall(MethodCallExpr call, TypeDeclaration<?> owner) {
    return call.getScope().isEmpty()
        || call.getScope().filter(ThisExpr.class::isInstance).isPresent()
        || call.getScope().filter(NameExpr.class::isInstance)
            .map(NameExpr.class::cast)
            .map(NameExpr::getNameAsString)
            .filter(owner.getNameAsString()::equals)
            .isPresent();
  }

  private static boolean argumentsCouldMatch(
      CallableDeclaration<?> caller, MethodCallExpr call, CallableDeclaration<?> callee) {
    for (int index = 0; index < call.getArguments().size(); index++) {
      Optional<String> argumentType = inferredPrimitiveType(call.getArgument(index), caller);
      if (argumentType.isPresent()
          && primitiveType(callee.getParameter(index).getType())
              .filter(argumentType.get()::equals)
              .isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private static Optional<String> inferredPrimitiveType(
      Expression expression, CallableDeclaration<?> caller) {
    String literalType = LITERAL_TYPES.get(expression.getClass());
    if (literalType != null) {
      return Optional.of(literalType);
    }
    if (expression.isNameExpr()) {
      return parameterType(expression.asNameExpr(), caller);
    }
    if (expression.isDoubleLiteralExpr()) {
      return Optional.of(floatingPointType(expression.asDoubleLiteralExpr()));
    }
    return Optional.empty();
  }

  private static Optional<String> parameterType(
      NameExpr expression, CallableDeclaration<?> caller) {
    return caller.getParameters().stream()
        .filter(parameter -> parameter.getNameAsString().equals(expression.getNameAsString()))
        .findFirst()
        .flatMap(parameter -> primitiveType(parameter.getType()));
  }

  private static String floatingPointType(DoubleLiteralExpr expression) {
    return expression.getValue().toLowerCase().endsWith("f") ? "float" : "double";
  }

  private static void markFlowingArguments(
      CallableDeclaration<?> caller,
      MethodCallExpr call,
      CallableDeclaration<?> callee,
      Map<Concept, Evidence> evidenceByConcept) {
    for (int index = 0; index < call.getArguments().size(); index++) {
      int argumentIndex = index;
      caller.getParameters().stream()
          .filter(parameter -> referencesName(
              call.getArgument(argumentIndex), parameter.getNameAsString()))
          .forEach(parameter -> primitiveType(parameter.getType()).ifPresent(type -> {
            var calleeParameter = callee.getParameter(argumentIndex);
            if (calleeParameter.getNameAsString().equals(parameter.getNameAsString())
                && primitiveType(calleeParameter.getType()).filter(type::equals).isPresent()) {
              evidenceByConcept.get(new Concept(parameter.getNameAsString(), type))
                  .markFlow(caller, callee);
            }
          }));
    }
  }

  private static void inspectCallables(
      Concept concept, Evidence evidence, List<CallableDeclaration<?>> callables) {
    for (CallableDeclaration<?> callable : callables) {
      inspectCallable(concept, evidence, callable);
    }
  }

  private static void inspectCallable(
      Concept concept, Evidence evidence, CallableDeclaration<?> callable) {
    boolean declaresConcept = declares(callable, concept);
    if (!evidence.includes(callable, declaresConcept)) {
      return;
    }
    evidence.recordPrivateHelper(callable, declaresConcept);
    if (!references(callable, concept, callable)) {
      return;
    }
    evidence.recordValidation(callable, hasKnownValidation(callable, concept, callable));
    evidence.recordConditional(callable, hasConditionalUse(callable, concept, callable));
    evidence.transformation |= hasTransformation(callable, concept, callable);
    evidence.literalComparison |= comparesWithLiteral(callable, concept, callable);
    evidence.arithmetic |= usesArithmetic(callable, concept, callable);
  }

  private static boolean declares(CallableDeclaration<?> callable, Concept concept) {
    return callable.getParameters().stream().anyMatch(parameter ->
        parameter.getNameAsString().equals(concept.name())
            && primitiveType(parameter.getType()).filter(concept.type()::equals).isPresent());
  }

  private static boolean hasKnownValidation(
      Node node, Concept concept, CallableDeclaration<?> callable) {
    return node.findAll(MethodCallExpr.class).stream()
        .anyMatch(call -> VALIDATION_METHODS.contains(call.getNameAsString())
            && references(call, concept, callable));
  }

  private static boolean hasConditionalUse(
      Node node, Concept concept, CallableDeclaration<?> callable) {
    return node.findAll(IfStmt.class).stream()
        .anyMatch(statement -> references(statement.getCondition(), concept, callable)
            && (abrupt(statement.getThenStmt())
                || statement.getElseStmt().map(PrimitiveObsessionChecker::abrupt).orElse(false)))
        || node.findAll(AssertStmt.class).stream()
            .anyMatch(statement -> references(statement.getCheck(), concept, callable));
  }

  private static boolean abrupt(Node node) {
    return node.findAll(ThrowStmt.class).stream().anyMatch(statement -> belongsTo(statement, node))
        || node.findAll(ReturnStmt.class).stream()
            .filter(statement -> statement.getExpression().isEmpty())
            .anyMatch(statement -> belongsTo(statement, node));
  }

  private static boolean belongsTo(Node descendant, Node branch) {
    Node current = descendant;
    while (current != branch) {
      current = current.getParentNode().orElse(null);
      if (current == null) {
        return false;
      }
      if (current instanceof LambdaExpr) {
        return false;
      }
      if (current instanceof CallableDeclaration) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasTransformation(
      Node node, Concept concept, CallableDeclaration<?> callable) {
    return node.findAll(MethodCallExpr.class).stream()
        .anyMatch(call -> TRANSFORMATION_METHODS.contains(call.getNameAsString())
            && references(call, concept, callable));
  }

  private static boolean comparesWithLiteral(
      Node node, Concept concept, CallableDeclaration<?> callable) {
    return node.findAll(BinaryExpr.class).stream()
        .anyMatch(expression -> COMPARISON_OPERATORS.contains(expression.getOperator())
            && references(expression, concept, callable)
            && !expression.findAll(LiteralExpr.class).isEmpty())
        || node.findAll(MethodCallExpr.class).stream()
            .filter(call -> call.getNameAsString().equals("equals")
                || call.getNameAsString().equals("equalsIgnoreCase"))
            .anyMatch(call -> references(call, concept, callable)
                && !call.findAll(LiteralExpr.class).isEmpty());
  }

  private static boolean usesArithmetic(
      Node node, Concept concept, CallableDeclaration<?> callable) {
    return node.findAll(BinaryExpr.class).stream()
        .anyMatch(expression -> ARITHMETIC_OPERATORS.contains(expression.getOperator())
            && references(expression, concept, callable)
            && (expression.getOperator() != BinaryExpr.Operator.PLUS
                || isNumericExpression(expression.getLeft(), concept, callable)
                    && isNumericExpression(expression.getRight(), concept, callable)));
  }

  private static boolean isNumericExpression(
      Expression expression, Concept concept, CallableDeclaration<?> callable) {
    NumericContext context = new NumericContext(concept, callable);
    return Boolean.TRUE.equals(expression.accept(new NumericExpressionVisitor(), context));
  }

  private static boolean isNumericType(String type) {
    return !NON_NUMERIC_TYPES.contains(type);
  }

  private static boolean references(
      Node node, Concept concept, CallableDeclaration<?> callable) {
    boolean shadowed = hasShadowingDeclaration(callable, concept);
    if (!shadowed && hasNameReference(node, concept)) {
      return true;
    }
    return hasThisFieldReference(node, concept);
  }

  private static boolean hasNameReference(Node node, Concept concept) {
    return node.findAll(NameExpr.class).stream()
        .anyMatch(expression -> expression.getNameAsString().equals(concept.name()));
  }

  private static boolean hasThisFieldReference(Node node, Concept concept) {
    return node.findAll(FieldAccessExpr.class).stream()
        .filter(expression -> expression.getScope() instanceof ThisExpr)
        .anyMatch(expression -> expression.getNameAsString().equals(concept.name()));
  }

  private static boolean hasShadowingDeclaration(
      CallableDeclaration<?> callable, Concept concept) {
    boolean parameterWithDifferentType = callable.getParameters().stream()
        .filter(parameter -> parameter.getNameAsString().equals(concept.name()))
        .anyMatch(parameter -> primitiveType(parameter.getType()).filter(concept.type()::equals).isEmpty());
    boolean localVariable = callable.findAll(VariableDeclarator.class).stream()
        .anyMatch(variable -> variable.getNameAsString().equals(concept.name()));
    boolean nestedParameter = callable.findAll(Parameter.class).stream()
        .filter(parameter -> callable.getParameters().stream().noneMatch(own -> own == parameter))
        .anyMatch(parameter -> parameter.getNameAsString().equals(concept.name()));
    if (parameterWithDifferentType) {
      return true;
    }
    if (localVariable) {
      return true;
    }
    return nestedParameter;
  }

  private static boolean referencesName(Node node, String name) {
    return node.findAll(NameExpr.class).stream()
        .anyMatch(expression -> expression.getNameAsString().equals(name));
  }

  private static void findTravellingConcepts(
      List<CallableDeclaration<?>> callables, Map<Concept, Evidence> evidenceByConcept) {
    Map<ConceptPair, Integer> pairCounts = new HashMap<>();
    for (CallableDeclaration<?> callable : callables) {
      List<Concept> concepts = callable.getParameters().stream()
          .map(parameter -> primitiveType(parameter.getType())
              .map(type -> new Concept(parameter.getNameAsString(), type)))
          .flatMap(Optional::stream)
          .filter(concept -> evidenceByConcept.get(concept).participates(callable))
          .distinct()
          .sorted(Comparator.comparing(Concept::name).thenComparing(Concept::type))
          .toList();
      for (int left = 0; left < concepts.size(); left++) {
        for (int right = left + 1; right < concepts.size(); right++) {
          pairCounts.merge(new ConceptPair(concepts.get(left), concepts.get(right)), 1, Integer::sum);
        }
      }
    }

    pairCounts.entrySet().stream()
        .filter(entry -> entry.getValue() > 2)
        .forEach(entry -> {
          ConceptPair pair = entry.getKey();
          evidenceByConcept.get(pair.left()).travelsWith.add(pair.right().name());
          evidenceByConcept.get(pair.right()).travelsWith.add(pair.left().name());
        });
  }

  private static Optional<String> primitiveType(Type type) {
    if (type.isPrimitiveType()) {
      return Optional.of(type.asString());
    }
    if (!type.isClassOrInterfaceType()) {
      return Optional.empty();
    }
    String simpleName = simpleName(type.asClassOrInterfaceType());
    return BOXED_PRIMITIVES.contains(simpleName) ? Optional.of(simpleName) : Optional.empty();
  }

  private static String simpleName(ClassOrInterfaceType type) {
    String name = type.getNameWithScope();
    int lastDot = name.lastIndexOf('.');
    return lastDot >= 0 ? name.substring(lastDot + 1) : name;
  }

  private static boolean suppressesRule(TypeDeclaration<?> type) {
    return type.getAnnotations().stream()
        .filter(annotation -> annotation.getName().getIdentifier().equals("SuppressWarnings"))
        .flatMap(annotation -> annotation.findAll(StringLiteralExpr.class).stream())
        .anyMatch(value -> value.asString().equals("calisthenics:primitive-obsession"));
  }

  private static boolean isPrimitiveWrapper(TypeDeclaration<?> type) {
    return type instanceof RecordDeclaration record
        && record.getParameters().size() == 1
        && primitiveType(record.getParameter(0).getType()).isPresent();
  }

  private static Violation violation(Path file, Concept concept, Evidence evidence) {
    int line = evidence.relevantDeclarations().stream()
        .map(PrimitiveObsessionChecker::line)
        .min(Integer::compareTo)
        .orElse(0);
    return new Violation(
        file,
        line,
        "primitive-obsession",
        concept.name(),
        "Primitive concept '%s' uses %s in %d declarations (score %d): %s"
            .formatted(
                concept.name(),
                concept.type(),
                evidence.declarationCount(),
                evidence.score(),
                String.join("; ", evidence.descriptions())));
  }

  private static int line(Node node) {
    return node.getBegin().map(position -> position.line).orElse(0);
  }

  private record Concept(String name, String type) {
  }

  private record ConceptPair(Concept left, Concept right) {
  }

  private record FlowContext(
      TypeDeclaration<?> owner,
      List<CallableDeclaration<?>> callables,
      Map<Concept, Evidence> evidenceByConcept) {
  }

  private record NumericContext(Concept concept, CallableDeclaration<?> callable) {
  }

  private static final class NumericExpressionVisitor
      extends GenericVisitorAdapter<Boolean, NumericContext> {

    @Override
    public Boolean visit(IntegerLiteralExpr expression, NumericContext context) {
      return true;
    }

    @Override
    public Boolean visit(LongLiteralExpr expression, NumericContext context) {
      return true;
    }

    @Override
    public Boolean visit(DoubleLiteralExpr expression, NumericContext context) {
      return true;
    }

    @Override
    public Boolean visit(CharLiteralExpr expression, NumericContext context) {
      return true;
    }

    @Override
    public Boolean visit(NameExpr expression, NumericContext context) {
      if (expression.getNameAsString().equals(context.concept().name())) {
        return numericConceptReference(expression, context);
      }
      return parameterType(expression, context.callable())
          .filter(PrimitiveObsessionChecker::isNumericType)
          .isPresent();
    }

    private static boolean numericConceptReference(
        NameExpr expression, NumericContext context) {
      return references(expression, context.concept(), context.callable())
          && isNumericType(context.concept().type());
    }

    @Override
    public Boolean visit(FieldAccessExpr expression, NumericContext context) {
      if (!(expression.getScope() instanceof ThisExpr)) {
        return false;
      }
      if (!expression.getNameAsString().equals(context.concept().name())) {
        return false;
      }
      return isNumericType(context.concept().type());
    }

    @Override
    public Boolean visit(EnclosedExpr expression, NumericContext context) {
      return expression.getInner().accept(this, context);
    }

    @Override
    public Boolean visit(CastExpr expression, NumericContext context) {
      return primitiveType(expression.getType())
          .filter(PrimitiveObsessionChecker::isNumericType)
          .isPresent();
    }

    @Override
    public Boolean visit(UnaryExpr expression, NumericContext context) {
      return expression.getExpression().accept(this, context);
    }

    @Override
    public Boolean visit(BinaryExpr expression, NumericContext context) {
      if (!ARITHMETIC_OPERATORS.contains(expression.getOperator())) {
        return false;
      }
      if (!Boolean.TRUE.equals(expression.getLeft().accept(this, context))) {
        return false;
      }
      return Boolean.TRUE.equals(expression.getRight().accept(this, context));
    }
  }

  private static final class Evidence {
    private final Set<Node> declarations = identitySet();
    private final Map<CallableDeclaration<?>, Node> parameterDeclarations = new IdentityHashMap<>();
    private final Set<CallableDeclaration<?>> flowCallables = identitySet();
    private final Set<Node> flowDeclarations = identitySet();
    private final Set<String> privateHelpers = new LinkedHashSet<>();
    private final Set<String> validationMethods = new LinkedHashSet<>();
    private final Set<String> conditionalMethods = new LinkedHashSet<>();
    private final Set<String> travelsWith = new LinkedHashSet<>();
    private boolean fieldBacked;
    private boolean transformation;
    private boolean literalComparison;
    private boolean arithmetic;

    private void registerParameter(CallableDeclaration<?> callable, Node declaration) {
      if (callable == null) {
        fieldBacked = true;
        return;
      }
      parameterDeclarations.put(callable, declaration);
    }

    private void markFlow(CallableDeclaration<?> caller, CallableDeclaration<?> callee) {
      flowCallables.add(caller);
      flowCallables.add(callee);
      Optional.ofNullable(parameterDeclarations.get(caller)).ifPresent(flowDeclarations::add);
      Optional.ofNullable(parameterDeclarations.get(callee)).ifPresent(flowDeclarations::add);
    }

    private boolean includes(CallableDeclaration<?> callable, boolean declaresConcept) {
      if (!participates(callable)) {
        return false;
      }
      return declaresConcept || fieldBacked;
    }

    private boolean participates(CallableDeclaration<?> callable) {
      return fieldBacked || flowCallables.contains(callable);
    }

    private void recordPrivateHelper(
        CallableDeclaration<?> callable, boolean declaresConcept) {
      if (!(callable instanceof MethodDeclaration method)) {
        return;
      }
      if (!method.isPrivate()) {
        return;
      }
      if (declaresConcept) {
        privateHelpers.add(method.getNameAsString());
      }
    }

    private void recordValidation(CallableDeclaration<?> callable, boolean found) {
      recordMethod(validationMethods, callable, found);
    }

    private void recordConditional(CallableDeclaration<?> callable, boolean found) {
      recordMethod(conditionalMethods, callable, found);
    }

    private static void recordMethod(
        Set<String> methods, CallableDeclaration<?> callable, boolean found) {
      if (found) {
        methods.add(callable.getNameAsString());
      }
    }

    private boolean isArrayPosition(Concept concept) {
      return !fieldBacked
          && ARRAY_POSITION_NAMES.contains(concept.name())
          && flowCallables.stream().anyMatch(callable -> callable.getParameters().stream()
              .anyMatch(parameter -> parameter.getType().isArrayType()));
    }

    private Set<Node> relevantDeclarations() {
      return fieldBacked ? declarations : flowDeclarations;
    }

    private int declarationCount() {
      return relevantDeclarations().size();
    }

    private boolean hasBehaviour() {
      return !behaviourMethods().isEmpty() || transformation || literalComparison || arithmetic;
    }

    private int score() {
      Set<String> behaviourMethods = behaviourMethods();
      int score = points(declarationCount() > 1, 2);
      score += points(!privateHelpers.isEmpty(), 2);
      score += points(!behaviourMethods.isEmpty(), 3);
      score += points(transformation, 2);
      score += points(literalComparison, 2);
      score += points(arithmetic, 2);
      score += points(behaviourMethods.size() > 1, 3);
      score += points(!travelsWith.isEmpty(), 2);
      return score;
    }

    private static int points(boolean applies, int points) {
      return applies ? points : 0;
    }

    private Set<String> behaviourMethods() {
      Set<String> methods = new LinkedHashSet<>(validationMethods);
      methods.addAll(conditionalMethods);
      return methods;
    }

    private List<String> descriptions() {
      List<String> descriptions = new ArrayList<>();
      descriptions.add("repeated declaration");
      Set<String> conditionalOnly = new LinkedHashSet<>(conditionalMethods);
      conditionalOnly.removeAll(validationMethods);
      addDescription(descriptions, !privateHelpers.isEmpty(),
          "private helper(s) " + String.join(", ", privateHelpers));
      addDescription(descriptions, !validationMethods.isEmpty(),
          "validation in " + String.join(", ", validationMethods));
      addDescription(descriptions, !conditionalOnly.isEmpty(),
          "conditional use in " + String.join(", ", conditionalOnly));
      addDescription(descriptions, behaviourMethods().size() > 1,
          "validation or conditional use repeated across methods");
      addDescription(descriptions, transformation, "normalization, parsing, or formatting");
      addDescription(descriptions, literalComparison, "comparison with a literal");
      addDescription(descriptions, arithmetic, "numeric arithmetic");
      addDescription(descriptions, !travelsWith.isEmpty(),
          "repeatedly travels with " + String.join(", ", travelsWith));
      return descriptions;
    }

    private static void addDescription(
        List<String> descriptions, boolean applies, String description) {
      if (applies) {
        descriptions.add(description);
      }
    }

    private static <T> Set<T> identitySet() {
      return Collections.newSetFromMap(new IdentityHashMap<>());
    }
  }
}
