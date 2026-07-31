package kmu.settings;

import kmlib.settings.LabeledChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shipped LunaLib settings table against the code that reads it. A Radio field's stored value
 * is its selected option's label, so a label that drifts between the CSV and its {@link LabeledChoice}
 * enum fails silently in play: the read finds no match, falls back to the default, and the player's
 * pick simply does nothing with no error to trace. Nothing else checks the two agree - the enums say so
 * only in a doc comment - so this reads the real data file rather than a fixture.
 *
 * <p>An option label is therefore a stored key wearing the costume of a caption, and is frozen for the
 * same reason a field id is: tidying the wording of one resets that setting for every player who had
 * picked it. The label check is only as wide as the table below, so the coverage walk holds every Radio
 * row in the file against that table - a row added or reworded by a pass over the settings screen has to
 * be classified here before the suite goes green, rather than slipping past the guard unnoticed.
 */
final class LunaSettingsCsvIntegrationTest {
    private static final Path SETTINGS_CSV = Path.of("data", "config", "LunaSettings.csv");
    
    // The CSV's own column order, as its header row declares it.
    private static final int FIELD_ID_COLUMN = 0;
    private static final int FIELD_TYPE_COLUMN = 6;
    private static final int DEFAULT_VALUE_COLUMN = 7;
    private static final int OPTIONS_COLUMN = 8;
    private static final String RADIO_FIELD_TYPE = "Radio";
    
    // LunaLib splits a Radio's options on commas; the authored rows space them out for readability.
    private static final String OPTION_SEPARATOR = ",";
    
    // The Radio fields whose options are not a LabeledChoice enum's labels, and so cannot be held
    // against one. The log level's options are log4j's own level names, which KmLogging hands
    // straight to the logger; naming them here is what keeps the coverage walk exhaustive without
    // pretending the row is choice-backed.
    private static final List<String> NON_CHOICE_BACKED_RADIO_FIELDS = List.of("kmu_logLevel");

    @Nested
    class RadioOptionLabels {

        @ParameterizedTest(name = "{0}")
        @MethodSource("kmu.settings.LunaSettingsCsvIntegrationTest#provideChoiceBackedRadioFields")
        void radioOptionLabelsAllResolveToTheirChoiceEnum(String fieldId, LabeledChoice[] choices) {
            var expectedLabels = Arrays.stream(choices).map(LabeledChoice::getLabel).toList();
            // A subset is legitimate - a field may offer only some of its enum's options - but an
            // option the enum cannot name is dead: picking it reads back as the fallback.
            assertThat(readOptions(fieldId)).isSubsetOf(expectedLabels);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("kmu.settings.LunaSettingsCsvIntegrationTest#provideChoiceBackedRadioFields")
        void radioOptionLabelsIncludeTheRowsOwnDefault(String fieldId, LabeledChoice[] choices) {
            assertThat(readOptions(fieldId)).contains(readColumn(fieldId, DEFAULT_VALUE_COLUMN));
        }
    }

    @Nested
    class RadioFieldCoverage {

        @Test
        void everyRadioFieldInTheFileIsClassifiedBySuite() {
            assertThat(readRadioFieldIds())
                .as(
                    "Radio rows in %s not listed as choice-backed or as non-choice-backed,"
                        + " so nothing holds their option labels frozen",
                    SETTINGS_CSV)
                .isSubsetOf(listClassifiedRadioFieldIds());
        }
    }

    // The Radio fields whose stored label a LabeledChoice enum maps back to a choice. Listed here
    // rather than read from KmuLunaSettings because the field ids are private there - a typo in this
    // table fails loudly (no such row) rather than quietly skipping a field.
    private static Stream<Arguments> provideChoiceBackedRadioFields() {
        return Stream.of(
            Arguments.of("kmu_politicalMapSidebarChevronColor", NotchChevronColorChoice.values()),
            Arguments.of("kmu_politicalMapHiddenMarketScaling", HiddenMarketScalingChoice.values()),
            Arguments.of("kmu_politicalMapFactionOuterBorderColor", FactionPaletteChoice.values()),
            Arguments.of("kmu_politicalMapFactionInnerBorderColor", FactionPaletteChoice.values()),
            Arguments.of("kmu_politicalMapFactionFillColor", FactionPaletteChoice.values()),
            Arguments.of("kmu_politicalMapIndependentOuterBorderColor", FactionPaletteChoice.values()),
            Arguments.of("kmu_politicalMapIndependentInnerBorderColor", FactionPaletteChoice.values()),
            Arguments.of("kmu_politicalMapIndependentFillColor", FactionPaletteChoice.values()),
            Arguments.of("kmu_politicalMapHoverHighlightColor", FactionPaletteChoice.values()));
    }

    // Every Radio field the shipped file declares, in file order.
    private static List<String> readRadioFieldIds() {
        return readSettingsRows().stream()
            .filter(row -> row.size() > FIELD_TYPE_COLUMN)
            .filter(row -> RADIO_FIELD_TYPE.equals(row.get(FIELD_TYPE_COLUMN)))
            .map(row -> row.get(FIELD_ID_COLUMN))
            .toList();
    }

    // The Radio fields this suite has an answer for: those held against a choice enum above, plus
    // those declared to have no enum behind them.
    private static List<String> listClassifiedRadioFieldIds() {
        return Stream.concat(
                provideChoiceBackedRadioFields().map(field -> (String) field.get()[0]),
                NON_CHOICE_BACKED_RADIO_FIELDS.stream())
            .toList();
    }

    // The row's offered option labels, trimmed of the spacing the authored rows use.
    private static List<String> readOptions(String fieldId) {
        return Arrays.stream(readColumn(fieldId, OPTIONS_COLUMN).split(OPTION_SEPARATOR))
            .map(String::trim)
            .filter(option -> !option.isEmpty())
            .toList();
    }

    // One cell of the named field's row. Fails the test outright when the row is missing or is not a
    // Radio, since either means the table below no longer describes the shipped file.
    private static String readColumn(String fieldId, int column) {
        var row = findRow(fieldId);
        assertThat(row.get(FIELD_TYPE_COLUMN))
            .as("field type of %s", fieldId)
            .isEqualTo(RADIO_FIELD_TYPE);
        return row.get(column);
    }

    private static List<String> findRow(String fieldId) {
        var rows = readSettingsRows().stream()
            .filter(row -> row.size() > OPTIONS_COLUMN)
            .filter(row -> fieldId.equals(row.get(FIELD_ID_COLUMN)))
            .toList();
        assertThat(rows).as("rows for field %s in %s", fieldId, SETTINGS_CSV).hasSize(1);
        return rows.get(0);
    }

    private static List<List<String>> readSettingsRows() {
        try {
            return Files
                .readAllLines(SETTINGS_CSV, StandardCharsets.UTF_8)
                .stream()
                .map(LunaSettingsCsvIntegrationTest::splitCsvLine)
                .toList();
        } catch (IOException failure) {
            // Surfaced rather than swallowed: the file is shipped data, so a read failure means the
            // test is looking in the wrong place, not that the settings are fine.
            throw new UncheckedIOException(
                "Could not read " + SETTINGS_CSV.toAbsolutePath(),
                failure);
        }
    }

    // Splits one CSV line into its cells, honouring double quotes - the description and option columns
    // both contain commas, so a plain split would shift every later column.
    private static List<String> splitCsvLine(String line) {
        var cells = new ArrayList<String>();
        var cell = new StringBuilder();
        var isQuoted = false;
        for (var character : line.toCharArray()) {
            if (character == '"') {
                isQuoted = !isQuoted;
            } else if (character == ',' && !isQuoted) {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(character);
            }
        }
        cells.add(cell.toString().trim());
        return cells;
    }
}
