package kmu.maplayers.ownermap.owners.holders.alliances;

import kmlib.starsector.factions.alliances.AllianceRecord;

import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds a flat list of {@link AllianceRecord}s into the {@link HolderGrouping} a holder pass
 * reads: every member faction maps to its alliance's bloc ID, each bloc colours off its lead
 * member, and each bloc carries the alliance name. Pure over plain data - no mod's or Starsector
 * type - so the fold holds whatever mod the records were read out of, which is why it sits with
 * the alliance holders rather than with the adapter that reads them.
 */
public final class AllianceGroupingFactory {

    private AllianceGroupingFactory() {
    }

    /**
     * Builds the grouping from the current alliances. A record with no members
     * contributes nothing (there is no lead member to colour off and no faction to
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

        for (var alliance : alliances) {

            var leadFactionId = alliance.findLeadFactionId();
            if (leadFactionId.isEmpty()) {
                // No member means no bloc to fold into and no lead member to colour
                // off, so a memberless alliance leaves no trace in the grouping.
                continue;
            }

            var blocId = alliance.allianceId();

            for (var member : alliance.membersSortedDescending()) {
                blocIdByFactionId.put(member, blocId);
            }

            colourFactionIdByBlocId.put(blocId, leadFactionId.get());
            allianceNameByBlocId.put(blocId, alliance.name());
        }

        return new HolderGrouping(
            blocIdByFactionId,
            colourFactionIdByBlocId,
            allianceNameByBlocId);
    }
}
