package kmu.util;

import kmlib.testfixtures.starsector.strings.ShippedStrings;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shipped strings file against the code that names its keys and the fixture that stands in
 * for it. Nothing else holds the three together: a key a constant names but the file never declares
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
final class KmuStringKeysIntegrationTest {

    @Nested
    class ShippedStringIds {

        @Test
        void everyStringIdKmuStringKeysNamesIsDeclaredInTheFile() {
            // A constant naming a key the file never declares reads as [REDACTED] wherever it is
            // drawn - visible to the player, invisible to every suite, since each takes its wording
            // from the fixture instead.
            assertThat(ShippedStrings.readStringsByKey())
                .containsKeys(ShippedStrings.readStringIdsByConstantName(KmuStringKeys.class)
                    .values()
                    .toArray(String[]::new));
        }

        @Test
        void everyStringIdDeclaredInTheFileIsNamedByKmuStringKeys() {
            // The opposite drift, and the quieter one: a key nothing names is wording that ships,
            // is translated, and is never drawn - a rename that left the old row behind.
            assertThat(ShippedStrings.readStringIdsByConstantName(KmuStringKeys.class).values())
                .containsExactlyInAnyOrderElementsOf(ShippedStrings.readStringsByKey().keySet());
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
}
