package kmu.maplayers.base.layer;

import kmlib.testfixtures.starsector.settings.CommonDataStoreFake;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the file this preference lives in and the shape it is read out of, and pins that every way
 * of failing to read it lands on the same answer: the roster as it registered.
 *
 * <p>The file name is asserted as a literal for the reason a save-serialised key is: renaming it
 * leaves every player's arrangement behind in a file nothing reads, with no failure anywhere to say
 * so.
 */
final class PersistedMapLayerArrangementTest {

    // The common-data file the arrangement is kept in, spelled out: it is frozen once shipped, so a
    // rename must break this rather than quietly orphan what players have arranged.
    private static final String FILE_NAME = "kmu_map_layer_arrangement.json";

    private static final String ORDERED_IDS_FIELD = "orderedLayerIds";
    private static final String HIDDEN_IDS_FIELD = "hiddenLayerIds";

    private final CommonDataStoreFake commonDataStoreFake = new CommonDataStoreFake();
    private final PersistedMapLayerArrangement arrangementSelection =
        new PersistedMapLayerArrangement(commonDataStoreFake);

    @Nested
    class ReadArrangement {

        @Test
        void readArrangementAnswersUnarrangedWithNoStoredFile() {
            // The fresh install, and every install whose player has never opened the dialog.
            assertThat(arrangementSelection.readArrangement())
                .isEqualTo(MapLayerArrangement.UNARRANGED);
        }

        @Test
        void readArrangementAnswersTheStoredOrderAndHiding() throws JSONException {

            storeArrangementFile(
                new JSONArray(List.of("gamma", "alpha")),
                new JSONArray(List.of("beta")));

            assertThat(arrangementSelection.readArrangement())
                .isEqualTo(new MapLayerArrangement(
                    List.of("gamma", "alpha"),
                    List.of("beta")));
        }

        @Test
        void readArrangementAnswersAnEmptyListForAFieldTheFileOmits() throws JSONException {
            // A file written by a build that stored only one of the two lists. Missing is not
            // malformed: what it does say is honoured, and the absent half reads as nothing stated.
            var storedFile = new JSONObject();
            storedFile.put(ORDERED_IDS_FIELD, new JSONArray(List.of("gamma")));

            commonDataStoreFake.storeFile(FILE_NAME, storedFile);

            assertThat(arrangementSelection.readArrangement())
                .isEqualTo(new MapLayerArrangement(List.of("gamma"), List.of()));
        }

        @Test
        void readArrangementAnswersUnarrangedForAFieldThatIsNotAList() throws JSONException {

            var storedFile = new JSONObject();
            storedFile.put(ORDERED_IDS_FIELD, "gamma");
            storedFile.put(HIDDEN_IDS_FIELD, new JSONArray());

            commonDataStoreFake.storeFile(FILE_NAME, storedFile);

            assertThat(arrangementSelection.readArrangement())
                .isEqualTo(MapLayerArrangement.UNARRANGED);
        }

        @Test
        void readArrangementAnswersUnarrangedForAnEntryThatIsNotAnId() throws JSONException {
            // The whole list is given up rather than the entry alone, so the player is shown the
            // unarranged row instead of a partly-honoured order they never asked for.
            storeArrangementFile(
                new JSONArray(List.of("gamma", 7)),
                new JSONArray());

            assertThat(arrangementSelection.readArrangement())
                .isEqualTo(MapLayerArrangement.UNARRANGED);
        }

        @Test
        void readArrangementAnswersUnarrangedWhereTheFileWillNotOpen() throws JSONException {

            storeArrangementFile(
                new JSONArray(List.of("gamma")),
                new JSONArray());

            commonDataStoreFake.refuseReads();

            assertThat(arrangementSelection.readArrangement())
                .isEqualTo(MapLayerArrangement.UNARRANGED);
        }
    }

    @Nested
    class RecordArrangement {

        @Test
        void recordArrangementWritesBothListsUnderTheArrangementsOwnFileName() {

            arrangementSelection.recordArrangement(new MapLayerArrangement(
                List.of("gamma", "alpha"),
                List.of("beta")));

            var storedFile = commonDataStoreFake.readStoredFile(FILE_NAME);

            assertThat(readStoredIds(storedFile, ORDERED_IDS_FIELD))
                .containsExactly("gamma", "alpha");
            assertThat(readStoredIds(storedFile, HIDDEN_IDS_FIELD))
                .containsExactly("beta");
        }

        @Test
        void recordArrangementStoresWhatTheNextReadAnswersWith() {
            // The round trip is the property that matters: an arrangement written under one shape
            // and read under another would pass two assertions that agree with each other and fail
            // the player across a restart.
            var arrangement = new MapLayerArrangement(
                List.of("gamma", "alpha", "beta"),
                List.of("alpha"));

            arrangementSelection.recordArrangement(arrangement);

            assertThat(arrangementSelection.readArrangement())
                .isEqualTo(arrangement);
        }
    }

    // Seeds the stored file as an earlier session would have left it.
    private void storeArrangementFile(JSONArray orderedIds, JSONArray hiddenIds) throws JSONException {

        var storedFile = new JSONObject();
        storedFile.put(ORDERED_IDS_FIELD, orderedIds);
        storedFile.put(HIDDEN_IDS_FIELD, hiddenIds);

        commonDataStoreFake.storeFile(FILE_NAME, storedFile);
    }

    // The IDs one field of the written file holds, read back the way anything else would read it.
    private static List<String> readStoredIds(JSONObject storedFile, String fieldName) {

        var storedArray = storedFile.optJSONArray(fieldName);
        var storedIds = new ArrayList<String>(storedArray.length());

        for (var index = 0; index < storedArray.length(); index++) {
            storedIds.add(storedArray.optString(index));
        }
        return storedIds;
    }
}
