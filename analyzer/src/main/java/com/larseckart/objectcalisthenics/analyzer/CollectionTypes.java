package com.larseckart.objectcalisthenics.analyzer;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.util.Set;

/** Shared syntax-only recognition for collection ownership and signatures. */
final class CollectionTypes {

  private CollectionTypes() {}

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

  static boolean isCollectionType(Type type) {
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

}
