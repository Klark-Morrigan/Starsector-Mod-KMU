package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints;

import java.util.List;

/**
 * The domination breakdown stated in full: the same ranked groups the ordinary box shows, each opened
 * up into the colonies it holds the system with and the factors each colony's weight was summed from.
 *
 * <p>The counterpart {@link SystemDominationTooltip} offers for the expanded detail mode. The ordinary
 * box answers who holds the system; this one answers why, which is a different question and a far
 * longer answer - so it is a box the player asks for rather than one they are always given.
 *
 * <p>It is the same contest either way. The ranking, the status line, the decree, the two headings, the
 * lines naming the blocs and the member factions inside them are all the shared shape's
 * ({@link SystemStandingsTooltip}), read from the one pass, so the two boxes cannot differ on anything
 * but how far into a group they go. What differs is only that: every faction listed is opened up here
 * into the colonies it holds the system with. The colonies hang under the faction flying them rather
 * than under the bloc, because a colony belongs to a faction and a bloc's score is the sum over its
 * members' - so a reader following the arithmetic upward reads each sum beneath the thing it is the sum
 * of, and the grouping the alliances view exists to show survives being explained.
 *
 * <p>The parts are read from the very arithmetic the scores above them were summed over
 * ({@link KnownMarketFootprints#readBreakdownByFaction}), so the lines always add up to the number the
 * ordinary box and the map's own fills show. A breakdown computed beside the weight rather than under
 * it could drift from it, and a box explaining a number it disagrees with is worse than no box.
 *
 * <p>One kind of colony is listed that no score above it accounts for: one the economy does not list,
 * which the weight read has nothing to weigh and so passes over entirely. It is named at nought
 * rather than left off, because the player can see the station on the map in a faction's colours -
 * but it is read separately and carried separately all the way to its line, so nothing it says can
 * reach the pass that painted the system.
 *
 * <p>Stateless past the reader it is built around, like the box it stands in for.
 */
public final class ExpandedSystemDominationTooltip extends SystemStandingsTooltip {

    ExpandedSystemDominationTooltip(ClaimBreakdownReader claimBreakdownReader) {
        super(claimBreakdownReader);
    }

    @Override
    protected FactionAccountResolver createFactionAccountResolver(
            SectorAPI sector,
            StarSystemAPI system,
            DominancePass pass) {

        // Read once for the whole box rather than per faction: every colony in the system comes out of
        // the one walk of its economy, which is what guarantees no two factions are explained from
        // different reads of it - and the walk is the most expensive thing a hover does.
        var breakdownsByFactionId = KnownMarketFootprints.readBreakdownByFaction(
            sector,
            system,
            pass.rules(),
            pass.shouldIncludeUndiscoveredMarkets());

        // The colonies the pass could not weigh, read beside the ones it did. They are a second read
        // rather than a second kind of breakdown because the pass must go on seeing exactly the
        // markets it sees today: a colony the economy does not list has nothing to weigh, and one
        // admitted here would hand its owner weight nobody worked out.
        var unweighedColoniesByFactionId = KnownMarketFootprints.readUnweighedColoniesByFaction(
            sector,
            system,
            pass.shouldIncludeUndiscoveredMarkets());

        // A faction the reads found nothing for is listed as its line alone rather than as a heading
        // over an empty account, which is what an empty answer means to the shape above.
        return standing -> MarketWeightRowResolver.resolveMarketRows(
            breakdownsByFactionId.getOrDefault(standing.factionId(), List.of()),
            unweighedColoniesByFactionId.getOrDefault(standing.factionId(), List.of()),
            pass.rules());
    }
}
