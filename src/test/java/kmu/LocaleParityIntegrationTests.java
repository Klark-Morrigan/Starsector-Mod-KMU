package kmu;

import kmlib.testfixtures.localisation.LocaleParity;
import kmlib.testfixtures.localisation.LocalisationDirectory;
import kmlib.testfixtures.mods.console.commands.ConsoleCommandsTable;

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
final class LocaleParityIntegrationTests {

    // What every KMU settings field ID starts with, which tells the mod's rows from the spacing rows
    // and the column-header line in each bundle's settings table.
    private static final String FIELD_ID_PREFIX = KmuMod.MOD_ID + "_";

    private static final LocalisationDirectory DIRECTORY = new LocalisationDirectory(LOCALISATION_DIRECTORY);

    private static final LocaleParity LOCALE_PARITY = new LocaleParity(DIRECTORY, FIELD_ID_PREFIX);

    // What parts a translated help from the default's English copy beneath it, as the table spells it: Console
    // Commands reads each backslash-n as a line break. Cutting the English copy once Console Commands draws the
    // translation is deleting everything from here to the end of the cell.
    private static final String ENGLISH_HELP_MARKER = "\\n\\n-- English --\\n";

    @Nested
    class ConsoleCommandHelp {

        @Test
        void everyTranslatedHelpEndsInTheDefaultsWordsVerbatim() {

            // Console Commands draws in fonts of its own, which hold no CJK glyphs, so a Chinese help reads as a
            // row of question marks. Each translation carries the English beneath it until that changes, and this
            // is what keeps the copy saying what the English table says.
            var manifest = DIRECTORY.readManifest();
            var defaultLocale = manifest.getDefaultLocale();
            var defaultHelps = DIRECTORY.openBundle(defaultLocale).readConsoleCommands();

            manifest
                .declaredLocalesByTag()
                .values()
                .stream()
                .filter(locale -> !locale.equals(defaultLocale))
                .forEach(locale -> DIRECTORY.openBundle(locale).readConsoleCommands().forEach((command, cells) ->

                    assertThat(cells.get(ConsoleCommandsTable.HELP_COLUMN))
                        .as("%s help of %s", locale.localeTag(), command)
                        .endsWith(ENGLISH_HELP_MARKER
                            + defaultHelps.get(command).get(ConsoleCommandsTable.HELP_COLUMN))));
        }
    }

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
