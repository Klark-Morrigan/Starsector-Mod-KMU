package kmu.util;

import kmlib.testfixtures.starsector.strings.ShippedStrings;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shipped strings file against the key classes that name its keys and the fixture that
 * stands in for it. Nothing else holds the three together: a key a constant names but the file never declares
 * resolves to {@code [REDACTED]} on screen, and no suite sees it, because every suite reads its
 * wording from the fixture instead.
 *
 * <p>The fixture is the reason the second walk exists. It restates the wording of the keys the
 * suites assert on rather than reading the file, which is what keeps a unit test off the disk - and
 * also what lets the two drift apart silently. A caption reworded in the file alone leaves every
 * assertion green while the player reads something else; reworded in the fixture alone, the suites
 * agree on words the game never ships. Held here, in the one place both artefacts may be read.
 *
 * <p>Reading both the file and the holder is {@link ShippedStrings}' job, KMLib holding the walk
 * that every KM mod's guard would otherwise keep a copy of.
 */
final class KmuStringKeysIntegrationTests {

    // Where the key classes live, read so a key class added beside them cannot escape the walk
    // below by being left off its list.
    private static final Path KEY_CLASS_DIRECTORY = Path.of("src/main/java/kmu/util");

    private static final String KEY_CLASS_SUFFIX = "StringKeys.java";

    // Every class holding string IDs, one per reader.
    private static final List<Class<?>> KEY_CLASSES = List.of(
        KmuStringKeys.class,
        KmuMapStringKeys.class,
        KmuOwnerMapStringKeys.class,
        KmuPoliticalMapStringKeys.class,
        KmuMarketConditionStringKeys.class);

    @Nested
    class ShippedStringIds {

        @Test
        void everyKeyClassInThePackageIsWalked() throws IOException {
            // A key class this suite does not list would have its constants checked by nothing.
            try (var files = Files.list(KEY_CLASS_DIRECTORY)) {

                var keyClassFileNames = files
                    .map(file -> file.getFileName().toString())
                    .filter(fileName -> fileName.endsWith(KEY_CLASS_SUFFIX))
                    .toList();

                assertThat(keyClassFileNames)
                    .containsExactlyInAnyOrderElementsOf(KEY_CLASSES
                        .stream()
                        .map(keyClass -> keyClass.getSimpleName() + ".java")
                        .toList());
            }
        }

        @Test
        void everyStringIdTheKeyClassesNameIsDeclaredInTheFile() {
            // A constant naming a key the file never declares reads as [REDACTED] wherever it is
            // drawn - visible to the player, invisible to every suite, since each takes its wording
            // from the fixture instead.
            assertThat(ShippedStrings.readStringsByKey())
                .containsKeys(readNamedStringIds().toArray(String[]::new));
        }

        @Test
        void everyStringIdDeclaredInTheFileIsNamedByAKeyClass() {
            // The opposite drift, and the quieter one: a key nothing names is wording that ships,
            // is translated, and is never drawn - a rename that left the old row behind.
            assertThat(readNamedStringIds())
                .containsExactlyInAnyOrderElementsOf(ShippedStrings.readStringsByKey().keySet());
        }

        @Test
        void noStringIdIsNamedByTwoKeyClasses() {
            // One home per ID: two constants for one row would let a rename move one and strand
            // the other on wording the reader no longer means.
            assertThat(readNamedStringIds())
                .doesNotHaveDuplicates();
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
            var shippedStrings = ShippedStrings.readStringsByKey();

            assertThat(StarsectorSettingsFake.readStringsByKey())
                .allSatisfy((key, wording) -> assertThat(wording)
                    .as("wording of %s", key)
                    .isEqualTo(shippedStrings.get(key)));
        }
    }

    // Every string ID every key class names, in class then declaration order, duplicates kept so a
    // second home for one ID shows.
    private static List<String> readNamedStringIds() {

        return KEY_CLASSES
            .stream()
            .flatMap(keyClass -> ShippedStrings.readStringIdsByConstantName(keyClass).values().stream())
            .toList();
    }
}
