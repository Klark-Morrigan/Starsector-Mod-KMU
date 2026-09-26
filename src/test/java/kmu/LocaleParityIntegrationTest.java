package kmu;

import kmlib.testfixtures.localisation.LocaleParity;
import kmlib.testfixtures.localisation.LocalisationDirectory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import static kmlib.testfixtures.localisation.LocalisationDirectory.LOCALISATION_DIRECTORY;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holds every locale KMU ships to its default, over the committed bundles under {@code localisation/}
 * rather than the {@code data/} copies a build writes from one of them.
 *
 * <p>Each gap checked here has nothing true behind it in play: a strings key one locale lacks draws as
 * {@code [REDACTED]}, a dropped format slot turns the whole line into it, and a settings row varying its
 * type, bounds or options per locale is a setting a player loses by switching builds. Nothing else
 * reads more than one bundle at a time, so these drift unseen until someone plays the other language.
 *
 * <p>The comparisons are KMLib's {@link LocaleParity}; this suite only points them at KMU's directory.
 */
final class LocaleParityIntegrationTest {

    // What every KMU settings field ID starts with, which tells the mod's rows from the spacing rows
    // and the column-header line in each bundle's settings table.
    private static final String FIELD_ID_PREFIX = KmuMod.MOD_ID + "_";

    private static final LocaleParity LOCALE_PARITY =
        new LocaleParity(new LocalisationDirectory(LOCALISATION_DIRECTORY), FIELD_ID_PREFIX);

    @Nested
    class FindAllMismatches {

        @Test
        void everyLocaleMatchesTheDefault() {

            // Each finding names its locale, its file and the edit that fixes it.
            assertThat(LOCALE_PARITY.findAllMismatches())
                .isEmpty();
        }
    }

    @Nested
    class ListModInfoFallbackFieldNames {

        @Test
        void launcherFieldsLeftToTheBaseArePublishedPerLocale(TestReporter testReporter) {
            // A launcher field a locale leaves out shows the base wording, which is degraded rather
            // than broken and often the intended choice - so it is published with the results for
            // whoever reviews them, and fails nothing.
            LOCALE_PARITY
                .listModInfoFallbackFieldNames()
                .forEach((localeTag, fallbackFieldNames) ->
                    testReporter.publishEntry(
                        localeTag + " launcher fields left to the base",
                        String.join(", ", fallbackFieldNames)));
        }
    }
}
