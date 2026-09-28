package com.github.larseckart.objectcalisthenics.analyzer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PrimitiveObsessionCheckerTest {

  @TempDir
  Path tempDir;

  @Test
  void reportsRepeatedStringConceptWithPrivateBehaviour() throws IOException {
    Path source = writeSource("Accounts.java", """
        class Accounts {
          void register(String email) {
            validateEmail(email);
            normalizeEmail(email);
          }

          private void validateEmail(String email) {
            if (email.isBlank()) {
              throw new IllegalArgumentException();
            }
          }

          private String normalizeEmail(String email) {
            return email.trim().toLowerCase();
          }
        }
        """);

    List<Violation> findings = primitiveFindings(analyzer(5).analyze(List.of(source)));

    assertThat(findings)
        .singleElement()
        .satisfies(finding -> {
          assertThat(finding.subject()).isEqualTo("email");
          assertThat(finding.message())
              .contains("uses String in 3 declarations")
              .contains("score 9")
              .contains("private helper(s) validateEmail, normalizeEmail")
              .contains("validation in validateEmail")
              .contains("normalization, parsing, or formatting");
          assertThat(finding.advice().principle()).contains("domain value");
        });
  }

  @Test
  void fieldsConnectSameNamedParametersWithoutArgumentFlow() throws IOException {
    Violation finding = singleFinding("Attempts.java", """
        class Attempts {
          private int maximum;

          void configure(int maximum) {
            if (maximum < 1) {
              throw new IllegalArgumentException();
            }
            this.maximum = maximum;
          }
        }
        """);

    assertThat(finding.subject()).isEqualTo("maximum");
  }

  @Test
  void declarationIdentityDoesNotDependOnSourceLines() throws IOException {
    Violation finding = singleFinding("Limits.java", """
        class Limits { int maximum; void configure(int maximum) {
          if (maximum < 1) throw new IllegalArgumentException();
          this.maximum = maximum;
        } }
        """);

    assertThat(finding.subject()).isEqualTo("maximum");
  }

  @Test
  void flowingParameterIdentityDoesNotDependOnSourceLines() throws IOException {
    Path source = writeSource("Accounts.java", """
        class Accounts { void register(String email) { validate(email); } private void validate(String email) {
          if (email.isBlank()) throw new IllegalArgumentException();
        } }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source))))
        .singleElement()
        .satisfies(finding -> assertThat(finding.subject()).isEqualTo("email"));
  }

  @Test
  void recordComponentsParticipateUnlessTheRecordIsAPrimitiveWrapper() throws IOException {
    Path source = writeSource("Money.java", """
        record Money(int amount, String currency) {
          Money withAmount(int amount) {
            if (amount < 0) {
              throw new IllegalArgumentException();
            }
            return new Money(amount, currency);
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source))))
        .singleElement()
        .satisfies(finding -> assertThat(finding.subject()).isEqualTo("amount"));
  }

  @Test
  void staticFieldsDoNotTurnParametersIntoARepeatedConcept() throws IOException {
    Path source = writeSource("Limits.java", """
        class Limits {
          private static int maximum;

          void configure(int maximum) {
            if (maximum < 1) {
              throw new IllegalArgumentException();
            }
            Limits.maximum = maximum;
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source)))).isEmpty();
  }

  @Test
  void boxedPrimitivesAreConceptCandidates() throws IOException {
    Violation finding = singleFinding("Retries.java", """
        class Retries {
          void configure(Integer retries) {
            requireRetries(retries);
          }

          private void requireRetries(Integer retries) {
            if (retries < 0) {
              throw new IllegalArgumentException();
            }
          }
        }
        """);

    assertThat(finding.message()).contains("uses Integer");
  }

  @Test
  void doesNotReportRepeatedTechnicalPrimitiveWithoutBehaviour() throws IOException {
    Path source = writeSource("Pager.java", """
        class Pager {
          void move(int offset) {
            copy(offset);
          }

          private void copy(int offset) {
            send(offset);
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source)))).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unrelatedBehaviourCases")
  void doesNotBorrowUnrelatedBehaviour(String description, String source) throws IOException {
    assertThat(findings("UnrelatedBehaviour.java", source)).isEmpty();
  }

  private static Stream<Arguments> unrelatedBehaviourCases() {
    return Stream.of(
        Arguments.of("same name with a different type", """
        class Codes {
          void accept(String code) {
            copy(code);
          }

          private void copy(String code) {
            send(code);
          }

          void count(int code) {
            if (code < 0) {
              throw new IllegalArgumentException();
            }
          }
        }
        """),
        Arguments.of("field shadowed by a different parameter type", """
        class Codes {
          String code;

          void store(String code) {
            this.code = code;
          }

          void count(int code) {
            if (code < 0) {
              throw new IllegalArgumentException();
            }
          }
        }
        """),
        Arguments.of("call on a collaborator", """
        class Accounts {
          void register(String email, Sink sink) {
            sink.validate(email);
          }

          private void validate(String email) {
            if (email.isBlank()) {
              throw new IllegalArgumentException();
            }
          }
        }

        interface Sink {
          void validate(String email);
        }
        """),
        Arguments.of("same-named foreign field argument", """
        class Accounts {
          void register(String email, Contact contact) {
            validate(contact.email);
          }

          private void validate(String email) {
            if (email.isBlank()) {
              throw new IllegalArgumentException();
            }
          }
        }

        class Contact {
          String email;
        }
        """));
  }

  @Test
  void ambiguousOverloadsDoNotCreateSpeculativeParameterFlow() throws IOException {
    Path source = writeSource("Accounts.java", """
        class Accounts {
          void register(String email) {
            validate(email, true);
          }

          private void validate(String email, boolean active) {
            send(email, active);
          }

          private void validate(String email, int attempts) {
            if (email.isBlank()) {
              throw new IllegalArgumentException();
            }
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source)))).isEmpty();
  }

  @Test
  void doesNotMergeSameNamedParametersWithoutDataFlow() throws IOException {
    Path source = writeSource("Payments.java", """
        class Payments {
          void charge(int amount, String currency) {
            if (amount < 0) {
              throw new IllegalArgumentException();
            }
          }

          void refund(int amount, String currency) {
            if (amount < 0) {
              throw new IllegalArgumentException();
            }
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source)))).isEmpty();
  }

  @Test
  void travellingEvidenceRequiresThreeRelevantCallables() throws IOException {
    Path source = writeSource("Payments.java", """
        class Payments {
          int amount;
          String currency;

          void charge(int amount, String currency) {
            if (amount < 0) {
              throw new IllegalArgumentException();
            }
          }

          void refund(int amount, String currency) {
            if (amount < 0) {
              throw new IllegalArgumentException();
            }
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source))))
        .singleElement()
        .satisfies(finding -> assertThat(finding.message()).doesNotContain("travels with"));
  }

  @Test
  void travellingEvidenceIsAddedAtThreeRelevantCallables() throws IOException {
    Path source = writeSource("Payments.java", """
        class Payments {
          int amount;
          String currency;

          void charge(int amount, String currency) {
            if (amount < 0) throw new IllegalArgumentException();
          }

          void refund(int amount, String currency) {
            if (amount < 0) throw new IllegalArgumentException();
          }

          void reserve(int amount, String currency) {
            if (amount < 0) throw new IllegalArgumentException();
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source))))
        .anySatisfy(finding -> assertThat(finding.message()).contains("travels with currency"));
  }

  @Test
  void doesNotCombineUnrelatedGenericParameters() throws IOException {
    Path source = writeSource("Parameters.java", """
        class Parameters {
          private int integer(String value) {
            if (value.isBlank()) {
              throw new IllegalArgumentException();
            }
            return Integer.parseInt(value);
          }

          private String filename(String value) {
            if (value.contains("/")) {
              throw new IllegalArgumentException();
            }
            return value.trim();
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source)))).isEmpty();
  }

  @Test
  void genericValueDoesNotBecomeAConceptWhenHelpersForwardIt() throws IOException {
    Path source = writeSource("Parameters.java", """
        class Parameters {
          private String required(String value) {
            if (value.isBlank()) {
              throw new IllegalArgumentException();
            }
            return value.trim();
          }

          private String email(String value) {
            return required(value).toLowerCase();
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source)))).isEmpty();
  }

  @Test
  void primitiveArraysAreLeftToCollectionAndParsingRules() throws IOException {
    Path source = writeSource("Images.java", """
        class Images {
          void validate(byte[] image) {
            if (image.length == 0) {
              throw new IllegalArgumentException();
            }
            store(image);
          }

          private void store(byte[] image) {
            if (image == null) {
              throw new IllegalArgumentException();
            }
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source)))).isEmpty();
  }

  @Test
  void enumStorageValuesAreAlreadyEncapsulatedByTheEnum() throws IOException {
    Path source = writeSource("DeliveryMethod.java", """
        enum DeliveryMethod {
          EMAIL("email"), SMS("sms");

          private final String persistedValue;

          DeliveryMethod(String persistedValue) {
            this.persistedValue = persistedValue;
          }

          static DeliveryMethod fromPersistedValue(String persistedValue) {
            for (DeliveryMethod method : values()) {
              if (method.persistedValue.equals(persistedValue)) {
                return method;
              }
            }
            throw new IllegalArgumentException();
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source)))).isEmpty();
  }

  @Test
  void offsetsAndLengthsTravellingWithArraysAreParsingCoordinates() throws IOException {
    Path source = writeSource("PacketReader.java", """
        class PacketReader {
          void read(byte[] bytes, int offset, int length) {
            requireAvailable(bytes, offset, length);
          }

          private void requireAvailable(byte[] bytes, int offset, int length) {
            if (offset < 0 || length > bytes.length - offset) {
              throw new IllegalArgumentException();
            }
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source)))).isEmpty();
  }

  @Test
  void domainOffsetIsNotIgnoredWithoutAnArrayOrBuffer() throws IOException {
    Violation finding = singleFinding("BillingSchedule.java", """
        class BillingSchedule {
          void schedule(int offset) {
            requireOffset(offset);
          }

          private void requireOffset(int offset) {
            if (offset < 1) {
              throw new IllegalArgumentException();
            }
          }
        }
        """);

    assertThat(finding.subject()).isEqualTo("offset");
  }

  @Test
  void doesNotTreatStringConcatenationAsArithmetic() throws IOException {
    Path source = writeSource("Errors.java", """
        class Errors {
          void missing(String name) {
            fail("Missing " + name);
            invalid(name);
          }

          private void invalid(String name) {
            fail("Invalid " + name);
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source)))).isEmpty();
  }

  @Test
  void doesNotTreatFormattingANumericPrimitiveAsArithmetic() throws IOException {
    Path source = writeSource("Messages.java", """
        class Messages {
          String show(int amount) {
            return message(amount);
          }

          private String message(int amount) {
            return "Amount: " + amount;
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source)))).isEmpty();
  }

  @Test
  void doesNotTreatOrdinaryBooleanBranchesAsMissingBehaviour() throws IOException {
    Path source = writeSource("Cookies.java", """
        class Cookies {
          void set(boolean secure) {
            if (secure) {
              addSecureAttribute();
            }
            clear(secure);
          }

          private void clear(boolean secure) {
            if (secure) {
              addSecureAttribute();
            }
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source)))).isEmpty();
  }

  @Test
  void doesNotTreatResultSelectionAsARejectingGuard() throws IOException {
    Path source = writeSource("Protocols.java", """
        class Protocols {
          String select(boolean secure) {
            return scheme(secure);
          }

          private String scheme(boolean secure) {
            if (secure) {
              return "https";
            }
            return "http";
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source)))).isEmpty();
  }

  @Test
  void assertionsAndRejectingGuardsCountAsConditionalBehaviour() throws IOException {
    Path source = writeSource("Page.java", """
        class Page {
          void show(int number) {
            assert number > 0;
            render(number);
          }

          private void render(int number) {
            if (number > 100) {
              return;
            }
            print(number);
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source))))
        .singleElement()
        .satisfies(finding -> assertThat(finding.message())
            .contains("conditional use in show, render")
            .contains("repeated across methods"));
  }

  @Test
  void numericArithmeticCountsAsBehaviour() throws IOException {
    Path source = writeSource("Pricing.java", """
        class Pricing {
          int total(int amount) {
            return doubled(amount);
          }

          private int doubled(int amount) {
            return amount * 2;
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source))))
        .singleElement()
        .satisfies(finding -> assertThat(finding.message()).contains("numeric arithmetic"));
  }

  @Test
  void doesNotReportPrimitiveInputBehaviourInsideItsWrapper() throws IOException {
    Path source = writeSource("EmailAddress.java", """
        record EmailAddress(String value) {
          static EmailAddress parse(String text) {
            return new EmailAddress(requireText(text));
          }

          private static String requireText(String text) {
            if (text.isBlank()) {
              throw new IllegalArgumentException();
            }
            return text.trim().toLowerCase();
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source)))).isEmpty();
  }

  @Test
  void thresholdCanDisableOrRequireStrongerEvidence() throws IOException {
    Path source = writeSource("Accounts.java", """
        class Accounts {
          void register(String email) {
            validateEmail(email);
          }

          private void validateEmail(String email) {
            if (email.isBlank()) {
              throw new IllegalArgumentException();
            }
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(8).analyze(List.of(source)))).isEmpty();
    assertThat(primitiveFindings(analyzer(-1).analyze(List.of(source)))).isEmpty();
  }

  @Test
  void canSuppressTheHeuristicPerType() throws IOException {
    Path source = writeSource("Accounts.java", """
        @SuppressWarnings("calisthenics:primitive-obsession")
        class Accounts {
          void register(String email) {
            validateEmail(email);
          }

          private void validateEmail(String email) {
            if (email.isBlank()) {
              throw new IllegalArgumentException();
            }
          }
        }
        """);

    assertThat(primitiveFindings(analyzer(5).analyze(List.of(source)))).isEmpty();
  }

  private ObjectCalisthenicsAnalyzer analyzer(int threshold) {
    RuleSet rules = new RuleSet(
        50, 2, true, true, 1, true, true, true,
        false, true, Set.of(), Set.of("System.out", "System.err"));
    return new ObjectCalisthenicsAnalyzer(
        rules, new PrimitiveObsessionConfig(threshold), List.of());
  }

  private List<Violation> primitiveFindings(AnalysisResult result) {
    return result.violations().stream()
        .filter(violation -> violation.rule().equals("primitive-obsession"))
        .toList();
  }

  private Violation singleFinding(String fileName, String source) throws IOException {
    List<Violation> findings = findings(fileName, source);
    assertThat(findings).hasSize(1);
    return findings.get(0);
  }

  private List<Violation> findings(String fileName, String source) throws IOException {
    Path sourceFile = writeSource(fileName, source);
    return primitiveFindings(analyzer(5).analyze(List.of(sourceFile)));
  }

  private Path writeSource(String fileName, String source) throws IOException {
    Path file = tempDir.resolve(fileName);
    Files.writeString(file, source);
    return file;
  }
}
