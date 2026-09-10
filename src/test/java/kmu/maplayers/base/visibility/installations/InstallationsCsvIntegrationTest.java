package kmu.maplayers.base.visibility.installations;

import com.fs.starfarer.api.SettingsAPI;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Holds the shipped table against the reader that opens it, by running the real file through the
 * real reader. Nothing else does: the columns are named in one place as strings and in the other as
 * a header line, so a rename on either side leaves every row silently stating nothing - which in
 * play looks exactly like a file nobody has edited yet.
 *
 * <p>The rows themselves are asserted because they are decisions rather than data. Not one of the
 * types below carries the station tag, so nothing but its row puts it on the map - and each is
 * something a player navigates by: the stations that are the whole point of a remnant system, and
 * the Domain-era landmarks a sector is read by, down to the survey probes.
 *
 * <p>The file states no kinds, and that is asserted too. Every kind is resolved from what the
 * sector says, and a shipped row stating one would be this mod deciding that a place is held by
 * somebody before anybody has looked.
 */
final class InstallationsCsvIntegrationTest {

    // The path the game is handed, spelled out here as the header line is: this suite exists to
    // catch the two spellings drifting apart.
    private static final String TABLE_PATH = "data/config/kmu/installations.csv";

    private static final String SHIPPED_HEADER = "entityTypeId,isAdmitted,kind";

    private static final String COLUMN_SEPARATOR = ",";

    // Vanilla's remnant station entities, which the theme generators add under these type ids.
    private static final List<String> REMNANT_STATION_TYPES = List.of(
        "station_research_remnant",
        "station_mining_remnant",
        "orbital_habitat_remnant");

    // The Domain-era hulks and infrastructure a sector is navigated by. The hypershunt is
    // "coronal_tap" to the game and a coronal hypershunt to the player, and a gate keeps the
    // inactive type id whether or not it is running.
    private static final List<String> DOMAIN_LANDMARK_TYPES = List.of(
        "derelict_cryosleeper",
        "derelict_mothership",
        "derelict_survey_ship",
        "derelict_probe",
        "derelict_gatehauler",
        "coronal_tap",
        "inactive_gate");

    private final SettingsAPI settingsMock = mock(SettingsAPI.class);

    @Nested
    class ShippedTable {

        @Test
        void ships_the_columns_the_reader_opens_it_by() throws IOException {

            assertThat(readShippedLines().get(0))
                .isEqualTo(SHIPPED_HEADER);
        }

        @Test
        void lists_vanillas_remnant_stations_which_no_tag_of_theirs_admits() throws Exception {

            var table = readShippedTable();

            assertThat(REMNANT_STATION_TYPES)
                .allSatisfy(entityTypeId ->
                    assertThat(table.readOverrideOf(entityTypeId).isAdmitted())
                        .contains(Boolean.TRUE));
        }

        @Test
        void lists_the_domain_era_landmarks_a_sector_is_navigated_by() throws Exception {

            var table = readShippedTable();

            assertThat(DOMAIN_LANDMARK_TYPES)
                .allSatisfy(entityTypeId ->
                    assertThat(table.readOverrideOf(entityTypeId).isAdmitted())
                        .contains(Boolean.TRUE));
        }

        @Test
        void states_no_kind_for_any_type_it_ships() throws Exception {

            assertThat(readShippedTable().overridesByEntityTypeId().values())
                .allSatisfy(shipped -> assertThat(shipped.kind()).isEmpty());
        }
    }

    // The shipped file as the reader sees it: parsed off disk into the rows the game would hand
    // over, then read by the production reader so the column names on both sides have to agree.
    private InstallationOverrideTable readShippedTable() throws IOException, JSONException {

        when(settingsMock.getMergedSpreadsheetDataForMod(anyString(), anyString(), anyString()))
            .thenReturn(parseShippedRows());

        return new InstallationOverrideTableReader(settingsMock).readTable();
    }

    // A plain split rather than a CSV parser, which the shipped file is written to stay within: no
    // quoting, no embedded separators, one cell per column. A row that outgrew that would fail here
    // rather than being read one way by this suite and another by the game.
    private JSONArray parseShippedRows() throws IOException, JSONException {

        var lines = readShippedLines();
        var columnNames = lines.get(0).split(COLUMN_SEPARATOR, -1);
        var rows = new JSONArray();

        for (var line : lines.subList(1, lines.size())) {

            var cells = line.split(COLUMN_SEPARATOR, -1);
            var row = new JSONObject();

            for (var columnIndex = 0; columnIndex < columnNames.length; columnIndex++) {
                row.put(
                    columnNames[columnIndex],
                    columnIndex < cells.length ? cells[columnIndex] : "");
            }
            rows.put(row);
        }
        return rows;
    }

    // The file's own lines, blank ones dropped, so a trailing newline is not read as a row.
    private List<String> readShippedLines() throws IOException {

        var lines = new ArrayList<String>();

        for (var line : Files.readAllLines(Path.of(TABLE_PATH), StandardCharsets.UTF_8)) {

            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }
}
