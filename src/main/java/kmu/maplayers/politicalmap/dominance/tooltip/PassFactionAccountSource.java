package kmu.maplayers.politicalmap.dominance.tooltip;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.weighting.MarketWeightBreakdown;
import kmu.maplayers.politicalmap.tooltip.SystemColonyReading;

import java.util.List;
import java.util.Map;

/**
 * The account a running game's domination box hangs beneath each faction it lists: the colonies its
 * score was summed from, and the factors behind each colony's weight, read off the pass the box
 * ranked under.
 *
 * <p>Apart from the box because the two answer different questions. The box decides how the contest
 * is laid out; this decides what explains one faction's score, which is the most expensive read a
 * hover makes and the one part of the box that walks the system's colonies a second time.
 *
 * <p>Every account is read once for the whole box rather than per faction: read per faction, two of
 * them could be explained from different selections over the system. The pass remembers each system
 * it walks, so the reads here cost one traversal between them.
 *
 * <p>Taken off the pass rather than assembled here: the weighting rule and the visibility rule are the
 * pass's, and an account that named them itself could explain a system under a rule the map did not
 * paint it under. The weights themselves are the ranking's, handed over rather than read again, so
 * each market is weighed once per box.
 */
final class PassFactionAccountSource
        implements SystemDominationTooltip.FactionAccountSource {

    @Override
    public FactionAccountResolver createFactionAccountResolver(
            StarSystemAPI system,
            DominancePass pass,
            Map<String, List<MarketWeightBreakdown>> breakdownsByFactionId,
            HoverTooltipDetailLevel detailLevel) {

        // The colonies the pass could not weigh, selected beside the ones it did. They stay a
        // separate read rather than becoming a second kind of breakdown because the pass must see
        // only the markets it can weigh.
        var unweighedColoniesByFactionId = pass.readUnweighedColoniesByFaction(system);

        // What the box may say about each colony beyond its weight, settled once for the whole box
        // off the same walk the weights and the read above came from: what sort of place it is,
        // whether the player has found it, and - unless somebody is looking at it as the box is
        // drawn - when it was last seen.
        var colonyReading = SystemColonyReading.readColonyReadingOf(
            pass.sector(),
            system,
            pass.readColoniesIn(system),
            pass.colonyKnowledge());

        // A faction the reads found nothing for is listed as its line alone rather than as a heading
        // over an empty account, which is what an empty answer means to the box's shape.
        var reading = new WeightAccountReading(pass.rules(), colonyReading, detailLevel);

        return standing -> MarketWeightRowResolver.resolveMarketRows(
            breakdownsByFactionId.getOrDefault(standing.factionId(), List.of()),
            unweighedColoniesByFactionId.getOrDefault(standing.factionId(), List.of()),
            reading);
    }
}
