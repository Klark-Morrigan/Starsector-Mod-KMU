package kmu.maplayers.politicalmap.dominance.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.politicalmap.base.dominance.MarketFootprint;
import kmu.maplayers.politicalmap.base.ribbon.ColonyCellRibbons;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The order a held cell's band comes out in: the fill's own order, read off the footprints the
 * system was ranked by.
 *
 * <p>The counting is {@link ColonyCellRibbons}'s and is the same on every layer, so what the held
 * side supplies is the ranking alone - which is all it ever added, the weights being an ordering
 * key rather than anything a band reports.
 *
 * <p>The bloc the cell was painted for leads, whatever settled that - a plain weight lead, or one
 * of the tie-breaks below the weights - so a reader never finds the leading colour second in a band
 * on a cell painted in it. Behind the leader the blocs run by descending weight and then by ID, the
 * same total order the standings box ranks them in, so the band and the box agree about who is
 * second.
 *
 * <p>A bloc the weights never reached - one holding nothing but colonies the economy does not list,
 * which have no industries, conditions or stability for the arithmetic to read - takes no place in
 * this ranking and draws behind it in ID order. It is present in the system and counted like any
 * other bloc; what it has no claim to is a rank among the blocs that were weighed.
 *
 * <p>Pure over the footprints a pass already read, every live read reached through the inputs.
 */
public final class HeldCellRibbons {

    private HeldCellRibbons() {
    }

    /**
     * Plans one held cell's band, ranked by the footprints its system was decided from.
     *
     * @param paintingBlocId    the bloc the cell's fill was painted for - the dominant bloc, which
     *                          leads the band whatever settled its lead
     * @param system            the system the cell draws as, whose colonies the band counts
     * @param footprintByBlocId each bloc's footprint in the cell's system, as the dominance pass
     *                          folded them under the view's grouping
     * @param inputs            everything one bake's bands are settled from, sampled once by the
     *                          bake
     * @return the cell's runs in draw order, or {@link RibbonPlan#NONE} where nothing the rule
     *         counts is present in the cell
     */
    public static RibbonPlan planHeldCellRibbon(
            String paintingBlocId,
            StarSystemAPI system,
            Map<String, MarketFootprint> footprintByBlocId,
            RibbonPlanInputs inputs) {

        // A painter always: the held mechanic paints a cell for the bloc its weights led, and a
        // system no bloc holds anything in raises no footprints for one to be resolved from.
        return ColonyCellRibbons.planCellRibbon(
            Optional.of(paintingBlocId),
            system,
            rankBlocsByWeight(paintingBlocId, footprintByBlocId),
            inputs);
    }

    // The blocs the weights reached, in the band's order. The weights are read no further than
    // this - what the band reports is the count - so the ordering is the last thing a footprint is
    // consulted for.
    private static List<String> rankBlocsByWeight(
            String paintingBlocId,
            Map<String, MarketFootprint> footprintByBlocId) {

        var ranked = new ArrayList<>(footprintByBlocId.entrySet());
        ranked.sort(orderByPaintedThenWeight(paintingBlocId));

        var rankedBlocIds = new ArrayList<String>(ranked.size());

        for (var blocFootprint : ranked) {
            rankedBlocIds.add(blocFootprint.getKey());
        }
        return rankedBlocIds;
    }

    // The band's order. The painter sorts ahead of everything because the dominance rule can
    // hand a system to a bloc that leads on none of the weights - a tie settled by the market
    // nearest the system centre, or by ID - and a band that re-ranked from the weights alone
    // would then open on a bloc the cell is not painted for.
    //
    // Behind it, descending combined weight and then ascending ID: the same two keys, in the
    // same order, that the standings ranking uses, so the band cannot disagree with the box
    // about who stands second in a system.
    private static Comparator<Map.Entry<String, MarketFootprint>> orderByPaintedThenWeight(
            String paintingBlocId) {

        return Comparator
            .comparing((Map.Entry<String, MarketFootprint> bloc) ->
                !bloc.getKey().equals(paintingBlocId))
            .thenComparing(
                bloc -> bloc.getValue().totalWeight(),
                Comparator.reverseOrder())
            .thenComparing(Map.Entry::getKey);
    }
}
