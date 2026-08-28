package kmu.maplayers.politicalmap.base.dominance;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The grouping a suite poses when it needs factions standing together rather than apart.
 *
 * <p>Built as plain data rather than through the mod that supplies alliances in play: a grouping is
 * three maps and every reader of one asks it which bloc a faction is in, so a hand-built one poses a
 * case exactly and reaches no live alliance set to do it.
 *
 * <p>Shared because the shape is the thing that repeats and the factions are not - a bloc id, its
 * colour faction, and its display name have to line up across all three maps or the grouping answers
 * one question consistently and the next one not at all. Stated once here, a suite names only the
 * factions its own case is about.
 */
public final class HolderGroupingFixture {

    // The one alliance every posed grouping is built around. Any id serves - nothing reads it but the
    // grouping's own three maps, which is exactly why it is written once rather than per suite.
    private static final String ALLIANCE_BLOC_ID = "alliance-1";

    // What the alliance is called on screen. Spelled here and asserted as a literal wherever a case
    // turns on it, so an assertion states the words a player would read rather than agreeing with
    // this fixture whatever it says.
    private static final String ALLIANCE_NAME = "Allied Powers";

    private HolderGroupingFixture() {
    }

    /**
     * Builds a grouping in which the named factions stand in one alliance and everyone else is their
     * own bloc.
     *
     * @param leadFactionId    the alliance's first member, which is also the faction whose palette
     *                         and crest the bloc paints in
     * @param otherFactionIds  the rest of its members
     * @return the grouping, in which no faction outside those named is grouped away from itself
     */
    public static HolderGrouping buildAllianceOf(String leadFactionId, String... otherFactionIds) {

        var blocIdByFactionId = new LinkedHashMap<String, String>();

        blocIdByFactionId.put(leadFactionId, ALLIANCE_BLOC_ID);

        for (var factionId : otherFactionIds) {
            blocIdByFactionId.put(factionId, ALLIANCE_BLOC_ID);
        }
        return new HolderGrouping(
            blocIdByFactionId,
            Map.of(ALLIANCE_BLOC_ID, leadFactionId),
            Map.of(ALLIANCE_BLOC_ID, ALLIANCE_NAME));
    }
}
