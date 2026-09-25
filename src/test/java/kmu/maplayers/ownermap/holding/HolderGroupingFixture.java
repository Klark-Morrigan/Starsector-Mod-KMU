package kmu.maplayers.ownermap.holding;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The grouping a suite poses when it needs factions standing together rather than apart.
 *
 * <p>Built as plain data rather than through the mod that supplies groups in play: a grouping is
 * three maps and every reader of one asks it which bloc a faction is in, so a hand-built one poses a
 * case exactly and reaches no live group source to do it.
 *
 * <p>Shared because the shape is the thing that repeats and the factions are not - a bloc ID, its
 * colour faction, and its display name have to line up across all three maps or the grouping answers
 * one question consistently and the next one not at all. Stated once here, a suite names only the
 * factions its own case is about.
 */
public final class HolderGroupingFixture {

    /**
     * The bloc the factions below fold into, and the ID a suite names that bloc by - the painter of
     * a cell, the key of a count, the bloc a palette answers for. Exported because a suite reading
     * a fold back has to name what it produced, and one spelling the ID for itself would pose a
     * bloc this fixture never built.
     */
    public static final String GROUP_BLOC_ID = "group-1";

    // What the group is called on screen. Spelled here and asserted as a literal wherever a case
    // turns on it, so an assertion states the words a player would read rather than agreeing with
    // this fixture whatever it says.
    private static final String GROUP_NAME = "Allied Powers";

    private HolderGroupingFixture() {
    }

    /**
     * Builds a grouping in which the named factions stand in one group and everyone else is their
     * own bloc.
     *
     * @param leadFactionId    the group's first member, which is also the faction whose palette
     *                         and crest the bloc paints in
     * @param otherFactionIds  the rest of its members
     * @return the grouping, in which no faction outside those named is grouped away from itself
     */
    public static HolderGrouping buildGroupOf(String leadFactionId, String... otherFactionIds) {

        var blocIdByFactionId = new LinkedHashMap<String, String>();

        blocIdByFactionId.put(leadFactionId, GROUP_BLOC_ID);

        for (var factionId : otherFactionIds) {
            blocIdByFactionId.put(factionId, GROUP_BLOC_ID);
        }
        return new HolderGrouping(
            blocIdByFactionId,
            Map.of(GROUP_BLOC_ID, leadFactionId),
            Map.of(GROUP_BLOC_ID, GROUP_NAME));
    }
}
