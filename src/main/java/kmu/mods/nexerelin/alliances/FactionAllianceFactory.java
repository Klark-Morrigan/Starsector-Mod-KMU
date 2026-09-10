package kmu.mods.nexerelin.alliances;

import kmu.maplayers.base.visibility.colonies.FactionAlliances;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inverts a flat list of {@link AllianceRecord}s into the {@link FactionAlliances} the visibility
 * rule reads: every member faction maps to its alliance's id, so two factions are on the same side
 * exactly when they land on the same value.
 *
 * <p>The same list {@link AllianceGroupingFactory} folds, turned the other way round for a reader
 * asking a different question - who would keep a partner's secret, rather than what a group of
 * factions paints as. Pure over plain data, so it is exercised directly on hand-built records and
 * {@link NexerelinAlliances} is left as the only Nex-touching seam.
 */
public final class FactionAllianceFactory {

    private FactionAllianceFactory() {
    }

    /**
     * Builds the memberships from the current alliances. A record naming no members contributes
     * nothing, and a faction named by two records resolves to the later one in list order, keeping
     * the fold total and deterministic per input.
     *
     * @param alliances the live alliance records, in any order; null yields
     *                  {@link FactionAlliances#NONE}
     * @return the alliance each member faction belongs to
     */
    public static FactionAlliances buildFrom(List<AllianceRecord> alliances) {
        if (alliances == null) {
            return FactionAlliances.NONE;
        }
        Map<String, String> allianceIdByFactionId = new HashMap<>();

        for (AllianceRecord alliance : alliances) {
            String allianceId = alliance.allianceId();
            if (allianceId == null) {
                // An alliance the game never named cannot be compared against another, and the
                // immutable copy the result takes refuses a null outright.
                continue;
            }
            for (String member : alliance.membersSortedDescending()) {
                allianceIdByFactionId.put(member, allianceId);
            }
        }
        return new FactionAlliances(allianceIdByFactionId);
    }
}
