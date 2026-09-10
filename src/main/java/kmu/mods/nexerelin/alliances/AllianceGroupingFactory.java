package kmu.mods.nexerelin.alliances;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds a flat list of {@link AllianceRecord}s into the {@link HolderGrouping} the
 * dominance pipeline reads: every member faction maps to its alliance's bloc id, each
 * bloc colours off its dominant member, and each bloc carries the alliance name. Pure
 * over plain data - no Nexerelin or Starsector type - so it is exercised directly on
 * hand-built records, and {@link NexerelinAlliances} is left as the only Nex-touching
 * seam.
 */
public final class AllianceGroupingFactory {

    private AllianceGroupingFactory() {
    }

    /**
     * Builds the grouping from the current alliances. A record with no members
     * contributes nothing (there is no dominant member to colour off and no faction to
     * fold), and an overlapping member - one named by two records - resolves to the
     * later record in list order, keeping the fold total and deterministic per input.
     * With no records the result is {@link HolderGrouping#identity()}, the faction
     * grouping.
     *
     * @param alliances the live alliance records, in any order
     * @return a grouping folding each alliance's members into its bloc
     */
    public static HolderGrouping buildFrom(List<AllianceRecord> alliances) {
        Map<String, String> blocIdByFactionId = new HashMap<>();
        Map<String, String> colourFactionIdByBlocId = new HashMap<>();
        Map<String, String> allianceNameByBlocId = new HashMap<>();
        for (AllianceRecord alliance : alliances) {
            List<String> members = alliance.membersSortedDescending();
            if (members.isEmpty()) {
                // No member means no bloc to fold into and no dominant member to colour
                // off, so a memberless alliance leaves no trace in the grouping.
                continue;
            }
            String blocId = alliance.allianceId();
            for (String member : members) {
                blocIdByFactionId.put(member, blocId);
            }
            colourFactionIdByBlocId.put(blocId, members.get(0));
            allianceNameByBlocId.put(blocId, alliance.name());
        }
        return new HolderGrouping(
            blocIdByFactionId,
            colourFactionIdByBlocId,
            allianceNameByBlocId);
    }
}
