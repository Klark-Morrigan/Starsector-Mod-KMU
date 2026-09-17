package kmu.util;

import kmlib.testfixtures.reflection.DeclaredConstants;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shipped strings file against the code that names its keys and the fixture that stands in
 * for it. Nothing else holds the three together: a key a constant names but the file never declares
 * resolves to {@code [REDACTED]} on screen, and no test sees it, because every suite reads its
 * wording from the fixture instead.
 *
 * <p>The fixture is the reason the second walk exists. It restates the wording of the keys the
 * suites assert on rather than reading the file, which is what keeps a unit test off the disk - and
 * also what lets the two drift apart silently. A caption reworded in the file alone leaves every
 * assertion green while the player reads something else; reworded in the fixture alone, the suites
 * agree on words the game never ships. Held here, in the one place both artefacts may be read.
 *
 * <p>The file is walked by hand rather than parsed, no JSON reader being on the test classpath. It
 * is flat - one key and its wording per line, inside a single category object, no escapes - so the
 * line shape below reads it exactly, and a file that stopped being flat would fail the walk rather
 * than quietly matching less of it.
 */
final class KmuStringsIntegrationTest {

    private static final Path STRINGS_JSON = Path.of("data", "strings", "strings.json");

    // One entry as the file writes it: a key, its wording, and the comma every line but the last
    // carries. The wording is captured whole, including any trailing punctuation of its own.
    private static final Pattern ENTRY_LINE =
        Pattern.compile("^\\s*\"([a-z_]+)\"\\s*:\\s*\"(.*)\"\\s*,?\\s*$");

    // The one constant on KmuStrings that names the category rather than a string inside it.
    private static final String CATEGORY_CONSTANT = "CATEGORY";

    @Nested
    class ShippedStringIds {

        @Test
        void everyStringIdKmuStringsNamesIsDeclaredInTheFile() {
            // A constant naming a key the file never declares reads as [REDACTED] wherever it is
            // drawn - visible to the player, invisible to every suite, since each takes its wording
            // from the fixture instead.
            assertThat(readShippedStrings())
                .containsKeys(readStringIdsByConstantName()
                    .values()
                    .toArray(String[]::new));
        }

        @Test
        void everyStringIdDeclaredInTheFileIsNamedByKmuStrings() {
            // The opposite drift, and the quieter one: a key nothing names is wording that ships,
            // is translated, and is never drawn - a rename that left the old row behind.
            assertThat(readStringIdsByConstantName().values())
                .containsExactlyInAnyOrderElementsOf(readShippedStrings().keySet());
        }
    }

    @Nested
    class FixtureWording {

        @Test
        void everyStringTheFixtureAnswersWithMatchesTheShippedWording() {
            // What makes an assertion on wording worth anything: the fixture states the words the
            // game ships rather than words of its own. It stands in for only the keys the suites
            // reach, so the walk is over what it does answer - a key it leaves out is answered by
            // no assertion either.
            var shippedStrings = readShippedStrings();

            assertThat(StarsectorSettingsFake.readStringsByKey())
                .allSatisfy((key, wording) -> assertThat(wording)
                    .as("wording of %s", key)
                    .isEqualTo(shippedStrings.get(key)));
        }
    }

    // The file's entries in the order it declares them.
    private static Map<String, String> readShippedStrings() {

        var stringsByKey = new LinkedHashMap<String, String>();

        for (var line : readFileLines()) {
            var entry = ENTRY_LINE.matcher(line);

            if (entry.matches()) {
                stringsByKey.put(entry.group(1), entry.group(2));
            }
        }
        return stringsByKey;
    }

    private static Iterable<String> readFileLines() {
        try {
            return Files.readAllLines(STRINGS_JSON, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read " + STRINGS_JSON, exception);
        }
    }

    // Every string ID KmuStrings names, kept against the constant naming it so a failure says which
    // constant is at fault rather than only which key is missing. CATEGORY names the category the
    // keys sit in rather than one of them, so it is dropped before the file is asked about it.
    private static Map<String, String> readStringIdsByConstantName() {

        var idsByConstantName =
            new LinkedHashMap<>(DeclaredConstants.readConstantsByName(KmuStrings.class, String.class));

        idsByConstantName.remove(CATEGORY_CONSTANT);

        return idsByConstantName;
    }
}
