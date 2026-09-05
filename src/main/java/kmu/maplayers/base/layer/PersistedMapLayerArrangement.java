package kmu.maplayers.base.layer;

import com.fs.starfarer.api.Global;

import kmlib.logging.SessionWarning;
import kmlib.starsector.settings.CommonDataStore;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link MapLayerArrangement} kept in the game's common data, which is per user and per install
 * rather than per save - so it sits beside {@link PersistedActiveLayerSelection} and stores
 * somewhere else entirely, for the reason the arrangement itself gives.
 *
 * <p>The file name carries the mod id because common data is one folder shared by every installed
 * mod, so an unprefixed name is a collision waiting for the mod that picks the same word.
 *
 * <p><b>Every read fails open.</b> No file, an unreadable one, a field that is not a list, an entry
 * that is not a string: each answers {@link MapLayerArrangement#UNARRANGED}, which the row assembly
 * reads as "the roster as it registered". A preference the player cannot see the loss of is not
 * worth an exception on the path that draws their bar, and the alternatives - refusing to draw, or
 * rewriting the file - are both worse than showing them the unarranged row.
 */
public final class PersistedMapLayerArrangement implements MapLayerArrangementSelection {

    // Where this preference lives under the game's common data. Prefixed with the mod id for the
    // reason above, and frozen once shipped: a rename leaves every player's arrangement behind in a
    // file nothing reads, with no failure to tell them so.
    private static final String ARRANGEMENT_FILE_NAME = "kmu_map_layer_arrangement.json";

    // The two lists inside it, named as the record's own components are, so a player opening the
    // file reads the same words the code does.
    private static final String ORDERED_IDS_FIELD = "orderedLayerIds";
    private static final String HIDDEN_IDS_FIELD = "hiddenLayerIds";

    private static final Logger LOG = Global.getLogger(PersistedMapLayerArrangement.class);

    // Says once that the stored file is not shaped the way this reads it. One warning for the whole
    // read: a file wrong in two fields is one hand-edit, and the second line would say nothing the
    // first did not.
    private final SessionWarning malformedFileWarning = new SessionWarning(LOG);

    // Says once that an arrangement could not be assembled for storage. Its own warning rather than
    // the read's, since the two cost the player different things - the read loses a choice already
    // made, this loses the one just made - and a session hitting both would otherwise report only
    // whichever came first.
    private final SessionWarning contentRefusedWarning = new SessionWarning(LOG);

    // Where the file is read from and written to. Injected rather than reached, so this class holds
    // the file's shape and nothing about the disk under it.
    private final CommonDataStore commonDataStore;

    /**
     * @param commonDataStore the common-data folder this arrangement is kept in
     */
    public PersistedMapLayerArrangement(CommonDataStore commonDataStore) {
        this.commonDataStore = commonDataStore;
    }

    @Override
    public MapLayerArrangement readArrangement() {

        var storedFile = commonDataStore.readJsonFile(ARRANGEMENT_FILE_NAME);
        if (storedFile == null) {
            // No file yet, or one that would not open - the store has already said so if it broke.
            return MapLayerArrangement.UNARRANGED;
        }
        var orderedLayerIds = readIdList(storedFile, ORDERED_IDS_FIELD);
        var hiddenLayerIds = readIdList(storedFile, HIDDEN_IDS_FIELD);

        if (orderedLayerIds == null || hiddenLayerIds == null) {
            warnOfTheMalformedFile();
            return MapLayerArrangement.UNARRANGED;
        }
        return new MapLayerArrangement(orderedLayerIds, hiddenLayerIds);
    }

    @Override
    public void recordArrangement(MapLayerArrangement arrangement) {

        var content = new JSONObject();
        try {
            content.put(ORDERED_IDS_FIELD, writeIdList(arrangement.orderedLayerIds()));
            content.put(HIDDEN_IDS_FIELD, writeIdList(arrangement.hiddenLayerIds()));

        } catch (JSONException contentRefused) {
            // Handled because the library declares it, not because it can happen: the only refusal
            // is a null key, and both keys are constants here. Reported rather than swallowed so a
            // library that grows a second reason says so, and the write is given up whole rather
            // than made half-formed over what the player already had.
            contentRefusedWarning.warnOnce(
                "Could not assemble the map layer bar arrangement for storage; the arrangement "
                    + "just made will hold for this session and be gone at the next start.",
                contentRefused);
            return;
        }
        commonDataStore.writeJsonFile(ARRANGEMENT_FILE_NAME, content);
    }

    // The ids under one field, or null where the field holds something this cannot read. Null
    // rather than an empty list, so a field that is not a list is told from one that is empty: the
    // first is a file to warn about and fall back from whole, the second is an ordinary state - a
    // player who has ordered their bar and hidden nothing.
    //
    // An absent field is the empty list rather than null, since a store written by an older build
    // is missing a field rather than malformed.
    private static List<String> readIdList(JSONObject storedFile, String fieldName) {

        if (!storedFile.has(fieldName)) {
            return List.of();
        }
        var storedArray = storedFile.optJSONArray(fieldName);
        if (storedArray == null) {
            return null;
        }
        var layerIds = new ArrayList<String>(storedArray.length());

        for (var index = 0; index < storedArray.length(); index++) {

            // A layer id is a string; anything else in the list is a hand-edit this cannot make
            // sense of, and taking the rest of the list on trust would leave the player with a
            // partly-honoured order they never asked for.
            if (!(storedArray.opt(index) instanceof String layerId)) {
                return null;
            }
            layerIds.add(layerId);
        }
        return layerIds;
    }

    // One list of ids as the array the file holds. Built entry by entry rather than handed the
    // collection, so what lands in the file is a list of strings whatever the library would have
    // made of the collection itself.
    private static JSONArray writeIdList(List<String> layerIds) {

        var storedArray = new JSONArray();

        for (var layerId : layerIds) {
            storedArray.put(layerId);
        }
        return storedArray;
    }

    // Says once that the file is not shaped the way this reads it, naming what that costs: the
    // player's arrangement is ignored, and the next arrangement they make overwrites the file.
    private void warnOfTheMalformedFile() {
        malformedFileWarning.warnOnce(
            "The map layer bar arrangement in '" + ARRANGEMENT_FILE_NAME + "' is not shaped the "
                + "way it is read; the bar will show every registered layer in registration order, "
                + "and the next arrangement made will replace the file.");
    }
}
