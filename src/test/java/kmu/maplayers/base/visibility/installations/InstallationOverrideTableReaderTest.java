package kmu.maplayers.base.visibility.installations;

import com.fs.starfarer.api.SettingsAPI;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how the shipped table is asked for and what each way of being wrong costs.
 *
 * <p>The path, the ID column and the owning mod are asserted as literals, because all three are a
 * contract with somebody else: the game finds the file by that path, folds every other mod's copy
 * of it onto ours by that column, and does the folding for that mod id. A rename here leaves the
 * mod reading nothing, and every mod that shipped rows for us writing into a file nobody opens.
 *
 * <p>The rest of the suite is about failing small. A file a mod update half-broke must still leave
 * the map drawing, so each scale of failure is pinned to the narrowest answer it can have - an
 * unreadable file to an empty table, a bad row to that row alone, a bad cell to that column's own
 * default. The direction of those defaults is the point: absent always means the facts decide, and
 * never that somebody holds a place.
 */
final class InstallationOverrideTableReaderTest {

    // The file, the columns and the mod, spelled out: the three halves of the contract above.
    private static final String TABLE_PATH = "data/config/kmu/installations.csv";
    private static final String OWNING_MOD_ID = "kmu";
    private static final String ENTITY_TYPE_COLUMN = "entityTypeId";
    private static final String ADMISSION_COLUMN = "isAdmitted";
    private static final String KIND_COLUMN = "kind";

    // A modded station that carries the station tag, so the facts already admit it - the row that
    // suppresses one has to be able to overrule that.
    private static final String ARTILLERY_STATION_TYPE = "IndEvo_ArtilleryStation";

    // A vanilla salvage derelict that carries no station tag, so nothing admits it but a row.
    private static final String CRYOSLEEPER_TYPE = "derelict_cryosleeper";

    private static final String NEIGHBOUR_TYPE = "station_research_remnant";

    private final SettingsAPI settingsMock = mock(SettingsAPI.class);

    private final InstallationOverrideTableReader tableReader =
        new InstallationOverrideTableReader(settingsMock);

    @Nested
    class ReadTable {

        @Test
        void asksForTheShippedTableAsAMergeOverEveryMod() throws Exception {

            shipRows();

            tableReader.readTable();

            verify(settingsMock)
                .getMergedSpreadsheetDataForMod(ENTITY_TYPE_COLUMN, TABLE_PATH, OWNING_MOD_ID);
        }

        @Test
        void listsATypeTheSectorSaysNothingAboutWhereARowAdmitsIt() throws Exception {

            shipRows(buildRow(CRYOSLEEPER_TYPE, "true", ""));

            assertThat(tableReader.readTable().readOverrideOf(CRYOSLEEPER_TYPE).isAdmitted())
                .contains(Boolean.TRUE);
        }

        @Test
        void dropsATypeTheSectorWouldAdmitWhereARowSuppressesIt() throws Exception {

            shipRows(buildRow(ARTILLERY_STATION_TYPE, "false", ""));

            assertThat(tableReader.readTable().readOverrideOf(ARTILLERY_STATION_TYPE).isAdmitted())
                .contains(Boolean.FALSE);
        }

        @Test
        void statesTheKindARowNames() throws Exception {

            shipRows(buildRow(ARTILLERY_STATION_TYPE, "", "HELD"));

            assertThat(tableReader.readTable().readOverrideOf(ARTILLERY_STATION_TYPE).kind())
                .contains(InstallationKind.HELD);
        }

        @Test
        void leavesEveryQuestionToTheSectorWhereARowStatesOnlyItsType()
                throws Exception {

            shipRows(buildRow(ARTILLERY_STATION_TYPE, "", ""));

            assertThat(tableReader.readTable().readOverrideOf(ARTILLERY_STATION_TYPE))
                .isEqualTo(InstallationOverride.NONE);
        }

        @Test
        void leavesEveryQuestionToTheSectorWhereAMergedRowCarriesNoneOfTheColumns()
                throws Exception {
            // Another mod's copy of the table need not be the same shape as ours: the merge folds
            // whatever columns that file has, so a row can arrive carrying only the type it names.
            var row = new JSONObject();
            row.put(ENTITY_TYPE_COLUMN, ARTILLERY_STATION_TYPE);

            shipRows(row);

            assertThat(tableReader.readTable().readOverrideOf(ARTILLERY_STATION_TYPE))
                .isEqualTo(InstallationOverride.NONE);
        }

        @Test
        void leavesAdmissionToTheSectorWhereARowStatesAWordItDoesNotUnderstand()
                throws Exception {
            // Not "false": a mistyped word is a row whose author meant something, and reading every
            // word but one as a suppression would hide the entity type they meant to list.
            shipRows(buildRow(CRYOSLEEPER_TYPE, "yes", ""));

            assertThat(tableReader.readTable().readOverrideOf(CRYOSLEEPER_TYPE).isAdmitted())
                .isEmpty();
        }

        @Test
        void leavesTheKindToTheSectorWhereARowNamesOneThatDoesNotExist()
                throws Exception {

            shipRows(buildRow(ARTILLERY_STATION_TYPE, "", "ABANDONED"));

            assertThat(tableReader.readTable().readOverrideOf(ARTILLERY_STATION_TYPE).kind())
                .isEmpty();
        }

        @Test
        void skipsARowNamingNoTypeWhileItsNeighboursLoad() throws Exception {
            // A statement about no type is a statement about nothing, and filing one under a blank
            // key would hand its answers to every other unnamed row.
            shipRows(
                buildRow(" ", "true", ""),
                buildRow(NEIGHBOUR_TYPE, "true", ""));

            var table = tableReader.readTable();

            assertThat(table.overridesByEntityTypeId())
                .containsOnlyKeys(NEIGHBOUR_TYPE);
        }

        @Test
        void skipsACommentRowWhileItsNeighboursLoad() throws Exception {
            // Filed under its own text, a comment would read as a type nobody ships.
            shipRows(
                buildRow("#" + CRYOSLEEPER_TYPE, "true", ""),
                buildRow(NEIGHBOUR_TYPE, "true", ""));

            assertThat(tableReader.readTable().overridesByEntityTypeId())
                .containsOnlyKeys(NEIGHBOUR_TYPE);
        }

        @Test
        void skipsAnEntryThatIsNotARowWhileItsNeighboursLoad() throws Exception {

            var rows = new JSONArray();
            rows.put("not a row");
            rows.put(buildRow(NEIGHBOUR_TYPE, "true", ""));

            when(settingsMock.getMergedSpreadsheetDataForMod(anyString(), anyString(), anyString()))
                .thenReturn(rows);

            assertThat(tableReader.readTable().overridesByEntityTypeId())
                .containsOnlyKeys(NEIGHBOUR_TYPE);
        }

        @Test
        void readsAFileThatWillNotOpenAsATableStatingNothing() throws Exception {
            // The file a mod update half-broke, and the file nobody shipped. Neither is worth
            // taking down the pass that was only consulting it.
            when(settingsMock.getMergedSpreadsheetDataForMod(anyString(), anyString(), anyString()))
                .thenThrow(new IOException("no such file"));

            assertThat(tableReader.readTable())
                .isEqualTo(InstallationOverrideTable.NONE);
        }

        @Test
        void readsAGameThatAnsweredNothingAsATableStatingNothing() throws Exception {

            when(settingsMock.getMergedSpreadsheetDataForMod(anyString(), anyString(), anyString()))
                .thenReturn(null);

            assertThat(tableReader.readTable())
                .isEqualTo(InstallationOverrideTable.NONE);
        }
    }

    @Nested
    class Construct {

        @Test
        void rejectsAReaderWithNoSettingsToReadTheFileThrough() {

            assertThatThrownBy(() -> new InstallationOverrideTableReader(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // One row of the merged table as the game hands it over: every cell a string, an empty one
    // standing for a column the author left blank.
    private static JSONObject buildRow(String entityTypeId, String isAdmitted, String kind)
            throws JSONException {

        var row = new JSONObject();
        row.put(ENTITY_TYPE_COLUMN, entityTypeId);
        row.put(ADMISSION_COLUMN, isAdmitted);
        row.put(KIND_COLUMN, kind);
        return row;
    }

    // What the game answers the merge with, in the order the rows are folded.
    private void shipRows(JSONObject... rows) throws Exception {

        var shipped = new JSONArray();

        for (var row : rows) {
            shipped.put(row);
        }
        when(settingsMock.getMergedSpreadsheetDataForMod(anyString(), anyString(), anyString()))
            .thenReturn(shipped);
    }
}
