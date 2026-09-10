package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;

import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGroupingSource;
import kmu.starsector.nexerelin.NexerelinAlliances;

import java.util.List;

/**
 * The domination breakdown a political-map layer shows for the hovered star system: the groups holding
 * markets there strongest first, each a crest, a name, and a score matching the weights the map paints
 * its fills by - and, under every faction named, the colonies it holds the system with and the factors
 * each colony's weight was summed from. The one {@link MapHoverTooltip} the faction and alliance views
 * both inject - it adapts flat vs nested off the active view's grouping, so those two layers share one
 * tooltip that varies its content rather than each carrying its own.
 *
 * <p>The two-tier shape at the top is settled where the group's kind is known
 * ({@link StandingRowResolver}) rather than here: a lone-faction group (the faction view) resolves to an
 * entry made up of nothing and reads as one flat line, while an alliance bloc resolves to one carrying
 * its member factions and reads as a line over them, however few it holds.
 *
 * <p>What this box decides is the account beneath those lines - how far into a group the box goes -
 * and it composes one account, read to whatever depth was asked for: who holds the system at the
 * shallowest level, the colonies behind that a tier down, their factors a tier below again. One tree
 * read to four depths rather than four bodies, so no two depths can describe one system differently.
 *
 * <p>What is drawn is settled by the cut the listing is laid out under
 * ({@link kmu.maplayers.base.tooltip.layout.CellTooltipBody}); what is composed stops at the same place, the
 * tiers here being the expensive ones. So the shallowest level pays for no colony read at all, and the
 * level that ranks the groups costs no more than the ranking it draws.
 *
 * <p>The colonies hang under the faction flying them rather than under the bloc, because a colony
 * belongs to a faction and a bloc's score is the sum over its members' - so a reader following the
 * arithmetic upward reads each sum beneath the thing it is the sum of, and the grouping the alliances
 * view exists to show survives being explained.
 *
 * <p>The parts are read from the very arithmetic the scores above them were summed over, and through
 * the very pass that ranked them ({@link DominancePass#readWeightBreakdownsByFaction}), so the lines
 * always add up to the number the top line and the map's own fills show. A breakdown computed beside
 * the weight rather than under it could drift from it, and a box explaining a number it disagrees with
 * is worse than no box.
 *
 * <p>Every colony line says what the box has found out about the place beyond its weight
 * ({@link SystemColonyReading}): what sort of place it is, how it is out of plain view, and - where
 * nobody is looking at it as the box is drawn - how old the news of it is. That matters most for the
 * colonies a revelation gate admitted on the strength of an observation - a derelict, a concealed base -
 * which would otherwise be listed exactly as a colony the player is standing over.
 *
 * <p>One kind of colony is listed that no score above it accounts for: one the economy does not list,
 * which the weight read has nothing to weigh and so passes over entirely. It is named at nought rather
 * than left off, because the player can see the station on the map in a faction's colours - but it is
 * read separately and carried separately all the way to its line, so nothing it says can reach the pass
 * that painted the system.
 *
 * <p>Stateless past the seams it is built around - the view, the live economy, the alliance set and
 * the settings are read afresh each paint - so one shared instance serves both views.
 */
public final class SystemDominationTooltip extends SystemStandingsTooltip {

    /**
     * The one shared instance; stateless, so both views inject it.
     *
     * <p>This is where the alliance set behind the allied block is bound, and the only place the
     * domination box names where alliances come from. The gate answers with the identity grouping
     * wherever the mod supplying them is absent, so the box needs no branch of its own and reads as
     * its dominating and contested blocks alone on such an install.
     *
     * <p>Deliberately not the active view's own grouping, which is what the map paints under: the
     * faction view pins that to identity so fills, runs and rows stay per faction, and reusing it
     * would leave the block permanently empty on the one layer that draws it.
     */
    public static final SystemDominationTooltip INSTANCE = new SystemDominationTooltip(
        VANILLA_CLAIM_BREAKDOWN_READER,
        NexerelinAlliances::resolveGrouping);

    SystemDominationTooltip(
            ClaimBreakdownReader claimBreakdownReader,
            HolderGroupingSource holderGroupingSource) {

        super(claimBreakdownReader, holderGroupingSource);
    }

    @Override
    protected HoverTooltipDetailLevel resolveDeepestAccountLevel() {
        // The colonies behind a faction, the factors behind a colony's weight, and the small, medium
        // and large split behind the patrol factor - which is the deepest tier the levels declare, so
        // this box fills the cycle out.
        return HoverTooltipDetailLevel.PATROL_DETAILS;
    }

    @Override
    protected FactionAccountResolver createFactionAccountResolver(
            StarSystemAPI system,
            DominancePass pass,
            HoverTooltipDetailLevel detailLevel) {

        // Read once for the whole box rather than per faction: read per faction, two of them could
        // be explained from different selections over the system, and the walk behind them is the
        // most expensive thing a hover does. The pass remembers each system it walks, so the reads
        // below cost one traversal between them.
        //
        // Taken off the pass rather than assembled here: the weighting rule and the visibility
        // rule are the pass's, and a box that named them itself could explain a system under a
        // rule the map did not paint it under.
        var breakdownsByFactionId = pass.readWeightBreakdownsByFaction(system);

        // The colonies the pass could not weigh, selected beside the ones it did. They stay a
        // separate read rather than becoming a second kind of breakdown because the pass must go on
        // seeing exactly the markets it sees today: a colony the economy does not list has nothing
        // to weigh, and one admitted there would hand its owner weight nobody worked out.
        var unweighedColoniesByFactionId = pass.readUnweighedColoniesByFaction(system);

        // What the box may say about each colony beyond its weight, settled once for the whole box
        // off the same walk the two reads above came from: what sort of place it is, whether the
        // player has found it, and - unless somebody is looking at it as the box is drawn - when it
        // was last seen.
        var colonyReading = SystemColonyReading.readColoniesIn(
            pass.sector(),
            system,
            pass.readColoniesIn(system),
            pass.colonyKnowledge());

        // A faction the reads found nothing for is listed as its line alone rather than as a heading
        // over an empty account, which is what an empty answer means to the shape above.
        var reading = new WeightAccountReading(pass.rules(), colonyReading, detailLevel);

        return standing -> MarketWeightRowResolver.resolveMarketRows(
            breakdownsByFactionId.getOrDefault(standing.factionId(), List.of()),
            unweighedColoniesByFactionId.getOrDefault(standing.factionId(), List.of()),
            reading);
    }
}
