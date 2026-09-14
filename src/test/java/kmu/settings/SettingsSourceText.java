package kmu.settings;

import kmlib.settings.LabeledChoice;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shipped Java sources, read as text: which field IDs they name, and what each row's fallback
 * constant is declared as.
 *
 * <p>Text rather than classes because every one of these constants is private to the class that
 * reads its row, which is what a settings class is for - so there is no accessor to call and no
 * reflection that would be any more honest than a regex. What it costs is that the sources have to
 * be written the way the walk expects; that is the point rather than the price, since a getter
 * written some other way fails the walk loudly instead of dropping out of it unnoticed.
 *
 * <p>A row's two ends are reached by following links, each anchored on the name the previous one
 * yielded: the field ID to the constant declaring it, that constant to the fallback passed beside it
 * at the read, and that fallback to the value it is declared as. Only the middle link varies by
 * type, so it is what parts the numeric walk from the boolean one - a row fetched through some other
 * kind of read then fails as an unfollowed link rather than being matched and held against the wrong
 * kind of default.
 *
 * <p>{@link LunaSettingsTable} is the other reading a settings check is made of, over the file these
 * sources name rows in.
 */
final class SettingsSourceText {

    static final Path MAIN_SOURCE_ROOT = Path.of("src", "main", "java");

    private static final String JAVA_SOURCE_SUFFIX = ".java";

    // A field ID as the sources spell it: quoted, so a mention in prose or a comment does not count
    // as reading the field.
    private static final Pattern FIELD_ID_LITERAL = Pattern.compile("\"(kmu_[A-Za-z0-9_]+)\"");

    // The first link, the same whatever the row holds - a field ID is a field ID.
    private static final String FIELD_CONSTANT_PATTERN = "(\\w+)\\s*=\\s*\"%s\"";

    // Every typed read a numeric row can be fetched through. Named one by one rather than as a
    // wildcard so that a read this walk has no answer for - a choice or a boolean read against a
    // numeric row - fails as an unfollowed link instead of being matched wrongly.
    private static final String NUMERIC_FALLBACK_READ_PATTERN =
        "read(?:Double|Float|Int)\\(\\s*%s\\s*,\\s*(\\w+)\\s*\\)";

    private static final String NUMERIC_DEFAULT_PATTERN = "\\b%s\\s*=\\s*(-?[\\d.]+[fFdD]?)\\s*;";

    // The same middle link for a Boolean row. Only one read can fetch one, so unlike the numeric
    // alternation this names a single method - which is what makes a switch read through anything
    // else fail the walk rather than pass it.
    private static final String BOOLEAN_FALLBACK_READ_PATTERN =
        "readBoolean\\(\\s*%s\\s*,\\s*(\\w+)\\s*\\)";

    private static final String BOOLEAN_DEFAULT_PATTERN = "\\b%s\\s*=\\s*(true|false)\\s*;";

    // A named Java fallback as the settings classes declare it: the constant, then the enum constant
    // it is assigned. Anchored on the constant's own name so two rows backed by the same enum with
    // different defaults are still told apart, and the enum is left unnamed so a choice moved to
    // another type still resolves.
    private static final String CHOICE_DEFAULT_PATTERN = "\\b%s\\s*=\\s*\\w+\\.([A-Z][A-Z0-9_]*)\\s*;";

    private SettingsSourceText() {
    }

    // The constant a Boolean row's getter passes as its fallback.
    static String findBooleanFallbackConstant(String fieldId) {
        return findFallbackConstant(fieldId, BOOLEAN_FALLBACK_READ_PATTERN);
    }

    // The constant a numeric row's getter passes as its fallback, followed through any of the typed
    // reads a number may be fetched by.
    static String findNumericFallbackConstant(String fieldId) {
        return findFallbackConstant(fieldId, NUMERIC_FALLBACK_READ_PATTERN);
    }

    // The state a switch's fallback constant is declared as.
    static boolean readDeclaredFlag(String defaultConstant) {

        return Boolean.parseBoolean(findSoleMatch(
            BOOLEAN_DEFAULT_PATTERN.formatted(Pattern.quote(defaultConstant)),
            "a declaration of " + defaultConstant));
    }

    // The number a fallback constant is declared as. A float literal's trailing suffix is not part
    // of the number and is dropped.
    static double readDeclaredNumber(String defaultConstant) {

        var declared = findSoleMatch(
            NUMERIC_DEFAULT_PATTERN.formatted(Pattern.quote(defaultConstant)),
            "a declaration of " + defaultConstant);

        return Double.parseDouble(declared.replaceAll("[fFdD]$", ""));
    }

    // Every field ID the shipped sources name, wherever they hold it.
    static Set<String> readFieldIdLiteralsInMainSources() {
        try (var sources = Files.walk(MAIN_SOURCE_ROOT)) {
            return sources
                .filter(source -> source.toString().endsWith(JAVA_SOURCE_SUFFIX))
                .flatMap(SettingsSourceText::findFieldIdLiterals)
                .collect(Collectors.toSet());
        } catch (IOException failure) {
            // Surfaced for the reason the CSV read is: an unreadable source tree means the walk is
            // looking in the wrong place, not that every field is read.
            throw new UncheckedIOException(
                "Could not walk " + MAIN_SOURCE_ROOT.toAbsolutePath(),
                failure);
        }
    }

    // The option label a named choice fallback constant resolves to, so a caller holds the shipped
    // constant rather than a copy of it.
    static String readFallbackLabel(String defaultConstant, LabeledChoice[] choices) {

        var declaredChoice = findDeclaredChoiceName(defaultConstant);

        return Arrays
            .stream(choices)
            .filter(choice -> ((Enum<?>) choice).name().equals(declaredChoice))
            .map(LabeledChoice::getLabel)
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                defaultConstant + " is declared as " + declaredChoice
                    + ", which is no option of the enum this row's table names"));
    }

    // The enum constant a fallback is declared as. Exactly one declaration is expected: none means
    // the caller's table names a constant the sources no longer hold, and two would leave the walk
    // holding whichever the file listed first.
    private static String findDeclaredChoiceName(String defaultConstant) {

        var pattern = Pattern.compile(CHOICE_DEFAULT_PATTERN.formatted(defaultConstant));

        try (var sources = Files.walk(MAIN_SOURCE_ROOT)) {

            var declarations = sources
                .filter(source -> source.toString().endsWith(JAVA_SOURCE_SUFFIX))
                .flatMap(source -> pattern.matcher(readSource(source)).results())
                .map(match -> match.group(1))
                .toList();

            assertThat(declarations)
                .as("declarations of %s under %s", defaultConstant, MAIN_SOURCE_ROOT)
                .hasSize(1);

            return declarations.get(0);

        } catch (IOException failure) {
            throw new UncheckedIOException(
                "Could not walk " + MAIN_SOURCE_ROOT.toAbsolutePath(),
                failure);
        }
    }

    // The constant a row's getter passes as its fallback, found by following the first two links.
    private static String findFallbackConstant(String fieldId, String fallbackReadPattern) {

        var fieldConstant = findSoleMatch(
            FIELD_CONSTANT_PATTERN.formatted(Pattern.quote(fieldId)),
            "the constant holding field id " + fieldId);

        return findSoleMatch(
            fallbackReadPattern.formatted(Pattern.quote(fieldConstant)),
            "the fallback passed beside " + fieldConstant);
    }

    private static Stream<String> findFieldIdLiterals(Path source) {
        return FIELD_ID_LITERAL
            .matcher(readSource(source))
            .results()
            .map(match -> match.group(1));
    }

    // The one capture the pattern finds across every shipped source. Exactly one is expected: none
    // means the sources no longer spell the thing this walk follows, and two would leave it holding
    // whichever file happened to be read first.
    private static String findSoleMatch(String pattern, String soughtDescription) {

        var matches = Pattern
            .compile(pattern)
            .matcher(readMainSourceText())
            .results()
            .map(match -> match.group(1))
            .distinct()
            .toList();

        assertThat(matches)
            .as("%s in %s", soughtDescription, MAIN_SOURCE_ROOT)
            .hasSize(1);

        return matches.get(0);
    }

    // Every shipped source as one text, so a walk that follows a link across classes - a field ID
    // declared in one and read in another - sees both ends of it.
    private static String readMainSourceText() {
        try (var sources = Files.walk(MAIN_SOURCE_ROOT)) {
            return sources
                .filter(source -> source.toString().endsWith(JAVA_SOURCE_SUFFIX))
                .map(SettingsSourceText::readSource)
                .collect(Collectors.joining("\n"));
        } catch (IOException failure) {
            // Surfaced for the reason the CSV read is: an unreadable source tree means the walk is
            // looking in the wrong place, not that every fallback agrees.
            throw new UncheckedIOException(
                "Could not walk " + MAIN_SOURCE_ROOT.toAbsolutePath(),
                failure);
        }
    }

    private static String readSource(Path source) {
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not read " + source.toAbsolutePath(), failure);
        }
    }
}
