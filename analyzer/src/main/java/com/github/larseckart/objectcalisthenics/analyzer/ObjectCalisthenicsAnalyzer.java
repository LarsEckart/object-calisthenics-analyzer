package com.github.larseckart.objectcalisthenics.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

  // Simple name matching for JDK collection types and their common implementations.
  // This intentionally does not require JavaParser's symbol solver.
  private static final Set<String> JDK_COLLECTION_TYPE_NAMES = Set.of(
      "Collection",
      "List", "ArrayList", "LinkedList", "CopyOnWriteArrayList",
      "Set", "HashSet", "LinkedHashSet", "TreeSet", "SortedSet", "NavigableSet", "EnumSet", "CopyOnWriteArraySet",
      "Map", "HashMap", "LinkedHashMap", "TreeMap", "SortedMap", "NavigableMap", "EnumMap",
      "ConcurrentMap", "ConcurrentHashMap", "ConcurrentNavigableMap",
      "Hashtable", "Properties", "Dictionary",
      "Vector", "Stack",
      "Queue", "Deque", "ArrayDeque", "PriorityQueue",
      "BlockingQueue", "LinkedBlockingQueue", "ArrayBlockingQueue", "PriorityBlockingQueue",
      "DelayQueue", "SynchronousQueue", "LinkedTransferQueue", "TransferQueue",
      "ConcurrentLinkedQueue", "ConcurrentLinkedDeque", "LinkedBlockingDeque", "BlockingDeque",
      "Iterable"
  );

  private record FieldInfo(String name, Type type) {
  }

  private record ChainStep(String name, boolean method) {
  }

  private record TraversalChain(String root, List<ChainStep> steps) {
  }

  private record ClassNamePattern(String source, Pattern pattern) {
  }

  public ObjectCalisthenicsAnalyzer(RuleSet rules, List<String> classNamePatterns) {
    this.rules = rules;
    this.classNamePatterns = classNamePatterns.stream()
        .map(pattern -> new ClassNamePattern(pattern, Pattern.compile(pattern)))
        .toList();
    this.parserConfiguration = new ParserConfiguration()
        .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_26);
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
      checkTraversalChains(unit, file, violations, excludedClassNames);
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
          checkMethod(method, file, violations);
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
    if (type instanceof RecordDeclaration record) {
      checkClassLength(record, file, violations);
      if (rules.includeRecordComponentsInFieldRule() && !suppressesFieldRule(record)) {
        checkRecordFields(record, file, violations);
      }
      checkFirstClassCollections(record, file, violations);
    } else if (type instanceof ClassOrInterfaceDeclaration classDecl) {
      checkClassLength(classDecl, file, violations);
      if (!suppressesFieldRule(classDecl)) {
        checkClassFields(classDecl, file, violations);
      }
      checkFirstClassCollections(classDecl, file, violations);
    }
  }

  private boolean suppressesFieldRule(TypeDeclaration<?> type) {
    return type.getAnnotations().stream()
        .filter(annotation -> annotation.getName().getIdentifier().equals("SuppressWarnings"))
        .flatMap(annotation -> annotation.findAll(StringLiteralExpr.class).stream())
        .anyMatch(value -> value.asString().equals("calisthenics:fields"));
  }

  // Reproduces the Python script's class/record detection, including modifiers
  // and annotations that may appear on the same line as the keyword.
  private static final java.util.regex.Pattern TYPE_DECLARATION_PATTERN =
      java.util.regex.Pattern.compile(
          "^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:(?:public|private|protected|static|final)\\s+)*"
              + "(class|record|interface|enum)\\b");

  private void checkClassLength(TypeDeclaration<?> type, Path file, List<Violation> violations) {
    Optional<com.github.javaparser.Position> begin = type.getBegin();
    Optional<com.github.javaparser.Position> end = type.getEnd();
    if (begin.isEmpty() || end.isEmpty()) {
      return;
    }

    int declarationLine = findDeclarationLine(type, file);
    int endLine = end.get().line;
    int meaningfulLines = countMeaningfulLines(file, declarationLine, endLine);

    if (meaningfulLines > rules.maxClassLines()) {
      violations.add(new Violation(
          file,
          declarationLine,
          "class-too-long",
          type.getNameAsString(),
          "%s has %d meaningful lines (limit %d)".formatted(
              type.getNameAsString(), meaningfulLines, rules.maxClassLines())
      ));
    }
  }

  private int findDeclarationLine(TypeDeclaration<?> type, Path file) {
    int beginLine = type.getBegin().map(p -> p.line).orElse(1);
    int endLine = type.getEnd().map(p -> p.line).orElse(beginLine);
    List<String> lines;
    try {
      lines = Files.readAllLines(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read file: " + file, e);
    }

    for (int i = beginLine - 1; i < Math.min(endLine, lines.size()); i++) {
      if (TYPE_DECLARATION_PATTERN.matcher(lines.get(i)).find()) {
        return i + 1;
      }
    }
    return beginLine;
  }

  private int countMeaningfulLines(Path file, int startLine, int endLine) {
    List<String> lines;
    try {
      lines = Files.readAllLines(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read file: " + file, e);
    }

    int count = 0;
    for (int i = startLine - 1; i < Math.min(endLine, lines.size()); i++) {
      String trimmed = lines.get(i).trim();
      if (!trimmed.isEmpty() && !trimmed.startsWith("//")) {
        count++;
      }
    }
    return count;
  }

  private void checkClassFields(ClassOrInterfaceDeclaration type, Path file, List<Violation> violations) {
    long instanceFields = type.getFields().stream()
        .filter(field -> !field.isStatic())
        .mapToLong(field -> field.getVariables().size())
        .sum();

    if (instanceFields > rules.maxFieldsPerClass()) {
      violations.add(new Violation(
          file,
          type.getBegin().map(p -> p.line).orElse(0),
          "too-many-instance-fields",
          type.getNameAsString(),
          "%s has %d instance fields (limit %d)".formatted(
              type.getNameAsString(), instanceFields, rules.maxFieldsPerClass())
      ));
    }
  }

  private void checkRecordFields(RecordDeclaration record, Path file, List<Violation> violations) {
    int components = record.getParameters().size();
    if (components > rules.maxFieldsPerClass()) {
      violations.add(new Violation(
          file,
          record.getBegin().map(p -> p.line).orElse(0),
          "too-many-record-components",
          record.getNameAsString(),
          "%s has %d record components (limit %d)".formatted(
              record.getNameAsString(), components, rules.maxFieldsPerClass())
      ));
    }
  }

  private void checkFirstClassCollections(TypeDeclaration<?> type, Path file, List<Violation> violations) {
    if (!rules.forbidNonFirstClassCollections()) {
      return;
    }

    List<FieldInfo> fields = new ArrayList<>();
    if (type instanceof ClassOrInterfaceDeclaration classDecl && !classDecl.isInterface()) {
      for (FieldDeclaration field : classDecl.getFields()) {
        if (field.isStatic()) {
          continue;
        }
        for (VariableDeclarator variable : field.getVariables()) {
          fields.add(new FieldInfo(variable.getNameAsString(), variable.getType()));
        }
      }
    } else if (type instanceof RecordDeclaration record) {
      for (Parameter parameter : record.getParameters()) {
        fields.add(new FieldInfo(parameter.getNameAsString(), parameter.getType()));
      }
    }

    long collectionFields = fields.stream().filter(f -> isCollectionType(f.type())).count();
    long otherFields = fields.size() - collectionFields;

    if (collectionFields > 0 && (collectionFields > 1 || otherFields > 0)) {
      violations.add(new Violation(
          file,
          type.getBegin().map(p -> p.line).orElse(0),
          "non-first-class-collection",
          type.getNameAsString(),
          "%s is not a first-class collection: %d collection field(s) and %d other instance field(s)".formatted(
              type.getNameAsString(), collectionFields, otherFields)
      ));
    }
  }

  private boolean isCollectionType(Type type) {
    if (type.isArrayType()) {
      return true;
    }
    if (type.isClassOrInterfaceType()) {
      String simpleName = getSimpleName(type.asClassOrInterfaceType());
      return JDK_COLLECTION_TYPE_NAMES.contains(simpleName);
    }
    return false;
  }

  private static String getSimpleName(ClassOrInterfaceType type) {
    String raw = type.asString();
    int genericStart = raw.indexOf('<');
    if (genericStart >= 0) {
      raw = raw.substring(0, genericStart);
    }
    int lastDot = raw.lastIndexOf('.');
    return lastDot >= 0 ? raw.substring(lastDot + 1) : raw;
  }

  private void checkMethod(MethodDeclaration method, Path file, List<Violation> violations) {
    if (!method.getBody().isPresent() || !isPublic(method)) {
      return;
    }

    if (rules.forbidElse() && containsElse(method)) {
      violations.add(new Violation(
          file,
          method.getBegin().map(p -> p.line).orElse(0),
          "else-used",
          method.getNameAsString(),
          "Method '%s' uses the else keyword".formatted(method.getNameAsString())
      ));
    }

    if (rules.maxMethodNesting() >= 0 && !method.isAbstract()) {
      int depth = computeNestingDepth(method);
      if (depth > rules.maxMethodNesting()) {
        violations.add(new Violation(
            file,
            method.getBegin().map(p -> p.line).orElse(0),
            "method-over-nested",
            method.getNameAsString(),
            "Method '%s' nests %d levels deep (limit %d)".formatted(
                method.getNameAsString(), depth, rules.maxMethodNesting())
        ));
      }
    }

    if (rules.forbidGetters() && looksLikeGetter(method)) {
      violations.add(new Violation(
          file,
          method.getBegin().map(p -> p.line).orElse(0),
          "getter",
          method.getNameAsString(),
          "Method '%s' looks like a getter".formatted(method.getNameAsString())
      ));
    }

    if (rules.forbidSetters() && looksLikeSetter(method)) {
      violations.add(new Violation(
          file,
          method.getBegin().map(p -> p.line).orElse(0),
          "setter",
          method.getNameAsString(),
          "Method '%s' looks like a setter".formatted(method.getNameAsString())
      ));
    }
  }

  private boolean isPublic(MethodDeclaration method) {
    if (method.isPublic()) {
      return true;
    }
    // Interface methods without an explicit modifier are public by default
    return method.findAncestor(ClassOrInterfaceDeclaration.class)
        .map(ClassOrInterfaceDeclaration::isInterface)
        .orElse(false);
  }

  private boolean containsElse(MethodDeclaration method) {
    return method.findAll(IfStmt.class).stream()
        .anyMatch(ifStmt -> ifStmt.getElseStmt().isPresent());
  }

  /**
   * Compute the maximum nesting depth inside a method body.
   *
   * <p>The method body itself is depth 0. Each nested block (including lambda
   * bodies and local/anonymous classes) adds one level.</p>
   */
  private int computeNestingDepth(MethodDeclaration method) {
    return method.getBody()
        .map(body -> {
          NestingDepthVisitor visitor = new NestingDepthVisitor();
          visitor.visit(body, 0);
          return visitor.maxDepth;
        })
        .orElse(0);
  }

  private boolean looksLikeGetter(MethodDeclaration method) {
    String name = method.getNameAsString();
    int prefixLength = name.startsWith("is") ? 2 : 3;
    boolean rightPrefix = (name.startsWith("get") || name.startsWith("is"))
        && name.length() > prefixLength
        && Character.isUpperCase(name.charAt(prefixLength));
    if (!rightPrefix || method.getType().isVoidType() || !method.getParameters().isEmpty()) {
      return false;
    }
    if (rules.strictGetterNames()) {
      return true;
    }

    String property = decapitalize(name.substring(prefixLength));
    return declaresProperty(method, property) || directlyReturnsState(method);
  }

  private String decapitalize(String name) {
    if (name.length() > 1 && Character.isUpperCase(name.charAt(0))
        && Character.isUpperCase(name.charAt(1))) {
      return name;
    }
    return name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1);
  }

  private boolean declaresProperty(MethodDeclaration method, String property) {
    Optional<TypeDeclaration<?>> owner = declaringType(method);
    if (owner.isEmpty()) {
      return false;
    }
    if (owner.get() instanceof RecordDeclaration record) {
      return record.getParameters().stream()
          .anyMatch(parameter -> parameter.getNameAsString().equals(property));
    }
    if (owner.get() instanceof ClassOrInterfaceDeclaration type) {
      return type.getFields().stream()
          .flatMap(field -> field.getVariables().stream())
          .anyMatch(variable -> variable.getNameAsString().equals(property));
    }
    return false;
  }

  private Optional<TypeDeclaration<?>> declaringType(MethodDeclaration method) {
    Optional<Node> current = method.getParentNode();
    while (current.isPresent()) {
      if (current.get() instanceof TypeDeclaration<?> type) {
        return Optional.of(type);
      }
      current = current.get().getParentNode();
    }
    return Optional.empty();
  }

  private boolean directlyReturnsState(MethodDeclaration method) {
    if (method.getBody().isEmpty() || method.getBody().get().getStatements().size() != 1) {
      return false;
    }
    return method.getBody().get().getStatement(0).toReturnStmt()
        .flatMap(ReturnStmt::getExpression)
        .map(this::unwrap)
        .map(expression -> expression instanceof NameExpr
            || expression instanceof FieldAccessExpr field
            && field.getScope() instanceof ThisExpr)
        .orElse(false);
  }

  private void checkTraversalChains(
      CompilationUnit unit,
      Path file,
      List<Violation> violations,
      Set<String> excludedClassNames
  ) {
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
    return node.findAncestor(TypeDeclaration.class).map(n -> (TypeDeclaration<?>) n);
  }

  private void addTraversalViolation(
      Expression expression,
      Path file,
      List<Violation> violations
  ) {
    TraversalChain chain = traversalChain(expression);
    int safeRootSteps = safeRootSteps(chain);
    int traversalSteps = chain.steps().size() - safeRootSteps;
    for (int i = safeRootSteps; i < chain.steps().size() - 1; i++) {
      ChainStep step = chain.steps().get(i);
      if (step.method() && rules.fluentChainMethods().contains(step.name())) {
        traversalSteps--;
      }
    }
    if (traversalSteps <= 1) {
      return;
    }

    String rendered = expression.toString().replaceAll("\\s+", " ");
    String subject = expression.findAncestor(MethodDeclaration.class)
        .map(MethodDeclaration::getNameAsString)
        .orElseGet(() -> expression.findAncestor(TypeDeclaration.class)
            .map(TypeDeclaration::getNameAsString)
            .orElse(""));
    violations.add(new Violation(
        file,
        expression.getBegin().map(position -> position.line).orElse(0),
        "traversal-chain",
        subject,
        "Traversal chain has %d steps: %s".formatted(traversalSteps, rendered)
    ));
  }

  private boolean isReceiverPrefix(Expression expression) {
    Expression current = expression;
    while (current.getParentNode().isPresent()) {
      var parent = current.getParentNode().get();
      if (parent instanceof EnclosedExpr enclosed && enclosed.getInner() == current
          || parent instanceof CastExpr cast && cast.getExpression() == current
          || parent instanceof ArrayAccessExpr array && array.getName() == current) {
        current = (Expression) parent;
        continue;
      }
      if (parent instanceof MethodCallExpr call) {
        return call.getScope().isPresent() && call.getScope().get() == current;
      }
      if (parent instanceof FieldAccessExpr field) {
        return field.getScope() == current;
      }
      return false;
    }
    return false;
  }

  private TraversalChain traversalChain(Expression expression) {
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

  private Expression unwrap(Expression expression) {
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
    if ((chain.root().equals("this") || chain.root().equals("super"))
        && !chain.steps().isEmpty() && !chain.steps().get(0).method()) {
      return 1;
    }

    int bestMatch = 0;
    String path = chain.root();
    for (int i = 0; i < chain.steps().size(); i++) {
      ChainStep step = chain.steps().get(i);
      if (step.method()) {
        break;
      }
      path += "." + step.name();
      if (rules.safeChainRoots().contains(path)) {
        bestMatch = i + 1;
      }
    }
    return bestMatch;
  }

  private boolean looksLikeSetter(MethodDeclaration method) {
    String name = method.getNameAsString();
    boolean rightPrefix = name.startsWith("set")
        && name.length() > 3
        && Character.isUpperCase(name.charAt(3));
    return rightPrefix
        && method.getType().isVoidType()
        && method.getParameters().size() == 1;
  }

  /**
   * Visitor that tracks how deeply block scopes are nested.
   */
  private static class NestingDepthVisitor extends GenericVisitorAdapter<Void, Integer> {
    private int maxDepth = 0;

    @Override
    public Void visit(BlockStmt n, Integer depth) {
      int childDepth = depth;
      boolean isMethodBody = n.getParentNode()
          .map(parent -> parent instanceof MethodDeclaration)
          .orElse(false);
      if (!isMethodBody) {
        childDepth = depth + 1;
        maxDepth = Math.max(maxDepth, childDepth);
      }
      return super.visit(n, childDepth);
    }

    @Override
    public Void visit(com.github.javaparser.ast.expr.LambdaExpr n, Integer depth) {
      int childDepth = depth + 1;
      maxDepth = Math.max(maxDepth, childDepth);
      return super.visit(n, childDepth);
    }

    @Override
    public Void visit(ClassOrInterfaceDeclaration n, Integer depth) {
      int childDepth = depth + 1;
      maxDepth = Math.max(maxDepth, childDepth);
      return super.visit(n, childDepth);
    }

    @Override
    public Void visit(RecordDeclaration n, Integer depth) {
      int childDepth = depth + 1;
      maxDepth = Math.max(maxDepth, childDepth);
      return super.visit(n, childDepth);
    }
  }
}
