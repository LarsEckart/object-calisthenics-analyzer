package com.github.larseckart.objectcalisthenics.gradle;

import com.github.larseckart.objectcalisthenics.analyzer.Violation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Baseline {

  private static final String HEADER = "# Object Calisthenics baseline v1";

  private final Set<Entry> entries;
  private final Path projectDirectory;

  private Baseline(Set<Entry> entries, Path projectDirectory) {
    this.entries = entries;
    this.projectDirectory = projectDirectory.toAbsolutePath().normalize();
  }

  static Baseline load(Path file, Path projectDirectory) {
    if (!Files.exists(file)) {
      return new Baseline(Set.of(), projectDirectory);
    }

    try {
      Set<Entry> entries = new HashSet<>();
      for (String line : Files.readAllLines(file)) {
        if (!line.isBlank() && !line.startsWith("#")) {
          entries.add(Entry.parse(line));
        }
      }
      return new Baseline(entries, projectDirectory);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read baseline from " + file, e);
    }
  }

  static void write(Path file, Path projectDirectory, List<Violation> violations) {
    Baseline baseline = new Baseline(Set.of(), projectDirectory);
    List<String> lines = baseline.entriesFor(violations).stream()
        .distinct()
        .sorted(Comparator.comparing(Entry::file)
            .thenComparing(Entry::rule)
            .thenComparing(Entry::subject))
        .map(Entry::serialize)
        .toList();

    try {
      Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.write(file, java.util.stream.Stream.concat(
          java.util.stream.Stream.of(HEADER),
          lines.stream()
      ).toList());
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write baseline to " + file, e);
    }
  }

  List<Entry> entriesFor(List<Violation> violations) {
    return violations.stream()
        .map(violation -> new Entry(
            relativePath(violation.file()),
            violation.rule(),
            violation.subject()
        ))
        .toList();
  }

  List<Entry> newEntries(List<Entry> current) {
    return current.stream().filter(entry -> !entries.contains(entry)).toList();
  }

  List<Entry> staleEntries(List<Entry> current) {
    Set<Entry> currentEntries = new HashSet<>(current);
    return entries.stream().filter(entry -> !currentEntries.contains(entry)).toList();
  }

  private String relativePath(Path file) {
    Path absoluteFile = file.toAbsolutePath().normalize();
    Path stablePath = absoluteFile.startsWith(projectDirectory)
        ? projectDirectory.relativize(absoluteFile)
        : absoluteFile;
    return stablePath.toString().replace(file.getFileSystem().getSeparator(), "/");
  }

  record Entry(String file, String rule, String subject) {

    private static Entry parse(String line) {
      String[] fields = line.split("\\t", -1);
      if (fields.length != 3) {
        throw new IllegalArgumentException("Invalid Object Calisthenics baseline entry: " + line);
      }
      return new Entry(unescape(fields[0]), unescape(fields[1]), unescape(fields[2]));
    }

    private String serialize() {
      return escape(file) + "\t" + escape(rule) + "\t" + escape(subject);
    }

    private static String escape(String value) {
      return value
          .replace("\\", "\\\\")
          .replace("\t", "\\t")
          .replace("\n", "\\n")
          .replace("\r", "\\r");
    }

    private static String unescape(String value) {
      StringBuilder result = new StringBuilder();
      boolean escaped = false;
      for (int index = 0; index < value.length(); index++) {
        char character = value.charAt(index);
        if (escaped) {
          result.append(switch (character) {
            case 't' -> '\t';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case '\\' -> '\\';
            default -> throw new IllegalArgumentException(
                "Invalid escape sequence in Object Calisthenics baseline: \\" + character
            );
          });
          escaped = false;
        } else if (character == '\\') {
          escaped = true;
        } else {
          result.append(character);
        }
      }
      if (escaped) {
        throw new IllegalArgumentException("Invalid trailing escape in Object Calisthenics baseline");
      }
      return result.toString();
    }
  }
}
