package kmu.settings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shipped LunaLib settings file, read as rows and columns.
 *
 * <p>One of the two things a settings check reads, and apart from the checks because it is a
 * reading rather than a judgement: which rows the file declares, what each cell of one holds, and
 * the few shapes the file is authored in - a tab's rows in one run, a value row under its own
 * section's caption - are answers about the file alone, and what any of them ought to be is the
 * suite's to say. {@link SettingsSourceText} is the other reading, over the Java that names these
 * rows.
 *
 * <p>The column vocabulary stays inside: a caller asks for a row's default, its bounds or its
 * options, never for cell fourteen. Nothing outside is phrased in column numbers, and a reading that
 * hands them out would put the file's shape back into every check that reads one cell of it.
 */
final class LunaSettingsTable {

    static final Path SETTINGS_CSV = Path.of("data", "config", "LunaSettings.csv");

    static final String RADIO_FIELD_TYPE = "Radio";
    static final String DOUBLE_FIELD_TYPE = "Double";
    static final String INT_FIELD_TYPE = "Int";
    static final String DOUBLE_FIELD_TYPE = "Double";
    static final String BOOLEAN_FIELD_TYPE = "Boolean";
    static final String KEYCODE_FIELD_TYPE = "Keycode";

    // The CSV's own column order, as its header row declares it.
    private static final int FIELD_ID_COLUMN = 0;
    private static final int FIELD_NAME_COLUMN = 4;
    private static final int FIELD_TYPE_COLUMN = 6;
    private static final int DEFAULT_VALUE_COLUMN = 7;
    private static final int OPTIONS_COLUMN = 8;
    private static final int MIN_VALUE_COLUMN = 14;
    private static final int MAX_VALUE_COLUMN = 15;
    private static final int TAB_COLUMN = 16;

    // The two row types that carry an ID so LunaLib can place them but store nothing, so no source
    // reads either: a section caption, and a run of prose standing among the knobs. Every other row
    // holds a value. They are told apart as well as together - a caption owns the rows under it,
    // while prose owns nothing and is free to stand ahead of every caption on its tab.
    private static final String HEADER_FIELD_TYPE = "Header";
    private static final String TEXT_FIELD_TYPE = "Text";

    // KMU's field IDs all carry the mod's prefix, which is also what tells a field row from the
    // file's own column-header line.
    private static final String FIELD_ID_PREFIX = "kmu_";

    // LunaLib splits a Radio's options on commas; the authored rows space them out for readability.
    private static final String OPTION_SEPARATOR = ",";

    private LunaSettingsTable() {
    }

    // The section captions whose two caption cells hold different text. A Header is drawn through
    // addSectionHeading(defaultValue), so the name column beside it is inert for this row type
    // alone - every other row type shows its name column and stores its default. Both are authored
    // to the same text so that the row reads the same however it is skimmed.
    static List<String> findHeaderRowsWhoseCaptionColumnsDisagree() {
        return readFieldRows()
            .stream()
            .filter(row -> HEADER_FIELD_TYPE.equals(row.get(FIELD_TYPE_COLUMN)))
            .filter(row -> !row.get(FIELD_NAME_COLUMN).equals(row.get(DEFAULT_VALUE_COLUMN)))
            .map(row -> row.get(FIELD_ID_COLUMN))
            .toList();
    }

    // The value rows placed on a different tab from the caption that heads their section. LunaLib
    // draws a section as the caption plus the rows following it, so the file's order is what binds
    // the two - a row moved between tabs on its own leaves its heading behind, and a row added under
    // the wrong caption inherits a tab nobody chose for it.
    static List<String> findRowsStrandedFromTheirSection() {
        var strandedFieldIds = new ArrayList<String>();

        // Empty until the first caption, so a value row ahead of every caption reads as stranded -
        // it has no section to belong to.
        var sectionTab = "";

        for (var row : readFieldRows()) {
            if (HEADER_FIELD_TYPE.equals(row.get(FIELD_TYPE_COLUMN))) {
                sectionTab = row.get(TAB_COLUMN);
            } else if (isStoredValueRow(row) && !sectionTab.equals(row.get(TAB_COLUMN))) {
                strandedFieldIds.add(row.get(FIELD_ID_COLUMN));
            }
        }
        return strandedFieldIds;
    }

    // The tabs whose rows are split into more than one run by another tab's. LunaLib places a row
    // by its tab column alone, so an interleaved file still draws the same screen - what breaks is
    // reading it. The file is authored one unbroken block per tab, separated by a spacer row, and
    // that is what makes a misplaced section show up as a stray run of its own rather than as a
    // handful of cells among a hundred-odd identical-looking ones.
    static List<String> findTabsDeclaredInMoreThanOneRun() {
        var runsPerTab = new LinkedHashMap<String, Integer>();

        // Empty rather than any tab name, so the file's first row opens a run instead of joining
        // one. No tab is named by the empty string, the spacer rows carrying no prefixed id.
        var previousTab = "";

        for (var tab : readDeclaredTabs()) {
            if (!tab.equals(previousTab)) {
                runsPerTab.merge(tab, 1, Integer::sum);
                previousTab = tab;
            }
        }
        return runsPerTab
            .entrySet()
            .stream()
            .filter(tabRuns -> tabRuns.getValue() > 1)
            .map(Map.Entry::getKey)
            .toList();
    }

    // The prose rows whose words would not reach the screen. LunaLib draws a Text row through
    // addPara(defaultValue) and shows no name column for it, so words authored beside it are
    // invisible and an empty drawn column is a blank note taking up space. Neither shows as an
    // error anywhere - the row loads, it simply says nothing.
    static List<String> findTextRowsWhoseWordsAreNotDrawn() {
        return readFieldRows()
            .stream()
            .filter(row -> TEXT_FIELD_TYPE.equals(row.get(FIELD_TYPE_COLUMN)))
            .filter(row -> !row.get(FIELD_NAME_COLUMN).isEmpty()
                    || row.get(DEFAULT_VALUE_COLUMN).isEmpty())
            .map(row -> row.get(FIELD_ID_COLUMN))
            .toList();
    }

    // The value a fresh player is given for the named row.
    static String readDefaultValue(String fieldId, String expectedFieldType) {
        return readColumn(fieldId, DEFAULT_VALUE_COLUMN, expectedFieldType);
    }

    // The high end of the named row's slider.
    static String readMaxValue(String fieldId, String expectedFieldType) {
        return readColumn(fieldId, MAX_VALUE_COLUMN, expectedFieldType);
    }

    // The low end of the named row's slider.
    static String readMinValue(String fieldId, String expectedFieldType) {
        return readColumn(fieldId, MIN_VALUE_COLUMN, expectedFieldType);
    }

    // Every prefixed ID the file declares a row for, section captions included: a caption stores
    // nothing, but it is still a row the file declares, so a source naming one is not naming a key
    // that does not exist.
    static List<String> readDeclaredFieldIds() {
        return readFieldRows()
            .stream()
            .map(row -> row.get(FIELD_ID_COLUMN))
            .toList();
    }

    // The tab every row asks to be placed on, section captions included: a caption is what carries a
    // section onto a tab, so it is placed the same way a value row is.
    static List<String> readDeclaredTabs() {
        return readFieldRows()
            .stream()
            .map(row -> row.get(TAB_COLUMN))
            .toList();
    }

    // Every row the file declares for a KMU field, as the pair a caller walking the file by type
    // asks about, in file order. Two cells rather than the row, so a check that wants the Boolean
    // rows or the numeric ones says so without also being handed the file's shape.
    static List<FieldRow> readFieldIdsAndTypes() {
        return readFieldRows()
            .stream()
            .map(row -> new FieldRow(row.get(FIELD_ID_COLUMN), row.get(FIELD_TYPE_COLUMN)))
            .toList();
    }

    // The row's offered option labels, trimmed of the spacing the authored rows use.
    static List<String> readOptions(String fieldId) {
        return Arrays
            .stream(readColumn(fieldId, OPTIONS_COLUMN, RADIO_FIELD_TYPE).split(OPTION_SEPARATOR))
            .map(String::trim)
            .filter(option -> !option.isEmpty())
            .toList();
    }

    // Every Radio field the shipped file declares, in file order.
    static List<String> readRadioFieldIds() {
        return readSettingsRows().stream()
            .filter(row -> row.size() > FIELD_TYPE_COLUMN)
            .filter(row -> RADIO_FIELD_TYPE.equals(row.get(FIELD_TYPE_COLUMN)))
            .map(row -> row.get(FIELD_ID_COLUMN))
            .toList();
    }

    // Every field the screen stores a value for, in file order.
    static List<String> readValueFieldIds() {
        return readFieldRows()
            .stream()
            .filter(LunaSettingsTable::isStoredValueRow)
            .map(row -> row.get(FIELD_ID_COLUMN))
            .toList();
    }

    // One cell of the named field's row. Fails outright when the row is missing or is not of the
    // type the caller reads it as, since either means the caller's tables no longer describe the
    // shipped file - and a cell read off a row of the wrong type would otherwise be held against a
    // column that means something else there.
    private static String readColumn(String fieldId, int column, String expectedFieldType) {
        var row = findRow(fieldId);
        assertThat(row.get(FIELD_TYPE_COLUMN))
            .as("field type of %s", fieldId)
            .isEqualTo(expectedFieldType);
        return row.get(column);
    }

    private static List<String> findRow(String fieldId) {

        var rows = readSettingsRows().stream()
            .filter(row -> row.size() > OPTIONS_COLUMN)
            .filter(row -> fieldId.equals(row.get(FIELD_ID_COLUMN)))
            .toList();

        assertThat(rows)
            .as("rows for field %s in %s", fieldId, SETTINGS_CSV)
            .hasSize(1);

        return rows.get(0);
    }

    // Whether a row is one the screen stores a value for, as against the caption and prose rows that
    // only draw. Both of those carry an ID and neither belongs to any section, so the readings that
    // ask what a field is worth, and the one that asks which caption owns it, have to leave them out.
    private static boolean isStoredValueRow(List<String> row) {

        var fieldType = row.get(FIELD_TYPE_COLUMN);

        return !HEADER_FIELD_TYPE.equals(fieldType) && !TEXT_FIELD_TYPE.equals(fieldType);
    }

    private static List<List<String>> readSettingsRows() {
        try {
            return Files
                .readAllLines(SETTINGS_CSV, StandardCharsets.UTF_8)
                .stream()
                .map(LunaSettingsTable::splitCsvLine)
                .toList();
        } catch (IOException failure) {
            // Surfaced rather than swallowed: the file is shipped data, so a read failure means the
            // reading is looking in the wrong place, not that the settings are fine.
            throw new UncheckedIOException(
                "Could not read " + SETTINGS_CSV.toAbsolutePath(),
                failure);
        }
    }

    // Every row the file declares for a KMU field, section captions included, in file order. The
    // spacing rows between sections and the file's own column-header line carry no prefixed ID, so
    // the prefix is also what tells a row from the file's furniture.
    private static List<List<String>> readFieldRows() {
        return readSettingsRows()
            .stream()
            .filter(row -> row.size() > TAB_COLUMN)
            .filter(row -> row.get(FIELD_ID_COLUMN).startsWith(FIELD_ID_PREFIX))
            .toList();
    }

    // Splits one CSV line into its cells, honouring double quotes - the description and option
    // columns both contain commas, so a plain split would shift every later column.
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

    /**
     * One declared row, as a check walking the file by type needs it.
     *
     * @param fieldId   the LunaLib field ID the row is stored under
     * @param fieldType the row's declared type, which is what says how its cells are read
     */
    record FieldRow(String fieldId, String fieldType) {
    }
}
