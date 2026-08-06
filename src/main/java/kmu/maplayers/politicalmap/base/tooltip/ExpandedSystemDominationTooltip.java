package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints;
import kmu.maplayers.politicalmap.base.dominance.MarketWeightBreakdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The domination breakdown stated in full: the same ranked groups the ordinary box shows, each opened
 * up into the colonies it holds the system with and the factors each colony's weight was summed from.
 *
 * <p>The counterpart {@link SystemDominationTooltip} offers for the expanded detail mode. The ordinary
 * box answers who holds the system; this one answers why, which is a different question and a far
 * longer answer - so it is a box the player asks for rather than one they are always given.
 *
 * <p>It is the same contest either way. The ranking, the status line, the decree, the two headings and
 * the lines naming the blocs are all the shared shape's ({@link SystemStandingsTooltip}), read from the
 * one pass, so the two boxes cannot differ on anything but how far into a group they go. What differs
 * is only that: a group is listed here over its colonies rather than over its member factions, because
 * the account being made is of where the score came from, and a score comes from colonies whichever
 * bloc they are flying for.
 *
 * <p>The parts are read from the very arithmetic the scores above them were summed over
 * ({@link KnownMarketFootprints#readBreakdownByFaction}), so the lines always add up to the number the
 * ordinary box and the map's own fills show. A breakdown computed beside the weight rather than under
 * it could drift from it, and a box explaining a number it disagrees with is worse than no box.
 *
 * <p>Stateless past the reader it is built around, like the box it stands in for.
 */
public final class ExpandedSystemDominationTooltip extends SystemStandingsTooltip {

    ExpandedSystemDominationTooltip(ClaimBreakdownReader claimBreakdownReader) {
        super(claimBreakdownReader);
    }

    @Override
    protected List<CellTooltipEntry> resolveGroupEntries(
            SectorAPI sector,
            StarSystemAPI system,
            List<ListedGroup> listedGroups,
            DominancePass pass) {

        // Read once for the whole box rather than per group: every bloc's colonies come out of the one
        // walk of the system's economy, which is also what guarantees no two blocs are explained from
        // different reads of it.
        var breakdownsByFactionId = KnownMarketFootprints.readBreakdownByFaction(
            sector,
            system,
            pass.rules(),
            pass.shouldIncludeUndiscoveredMarkets());

        // The line naming the bloc is left exactly as it arrived - it is the ordinary box's line - and
        // only what hangs beneath it is this box's own.
        return listedGroups
            .stream()
            .map(listedGroup -> listedGroup
                .entry()
                .nesting(MarketWeightRowResolver.resolveMarketRows(
                    collectGroupBreakdowns(listedGroup.standing(), breakdownsByFactionId),
                    pass.rules())))
            .toList();
    }

    // Every colony behind one group's score, its member factions' gathered together. A bloc's score is
    // the sum over its members' colonies, so the account of it is those colonies ranked against each
    // other rather than partitioned back out by owner - which would answer "which faction" a second
    // time and bury the colonies a level deeper for it.
    private static List<MarketWeightBreakdown> collectGroupBreakdowns(
            GroupStanding standing,
            Map<String, List<MarketWeightBreakdown>> breakdownsByFactionId) {

        var breakdowns = new ArrayList<MarketWeightBreakdown>();
        for (var member : standing.members()) {
            breakdowns.addAll(breakdownsByFactionId.getOrDefault(member.factionId(), List.of()));
        }
        return breakdowns;
    }
}
