package com.github.larseckart.objectcalisthenics.analyzer;

/** Switches available when building a rule set; numeric limits have named settings. */
public enum Rule {
  INCLUDE_RECORD_COMPONENTS_IN_FIELD_RULE,
  FORBID_ELSE,
  FORBID_GETTERS,
  FORBID_SETTERS,
  FORBID_NON_FIRST_CLASS_COLLECTIONS,
  STRICT_GETTER_NAMES,
  FORBID_TRAVERSAL_CHAINS,
  ONE_TOP_LEVEL_TYPE_PER_FILE
}
