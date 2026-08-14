package kmu.maplayers.politicalmap.base.ribbon;

import kmu.maplayers.politicalmap.base.dominance.MarketFootprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Plans the ribbon of a cell painted by the dominance mechanic, where the count each bloc's run
 * is made of is already in hand: the map sampled the system's colonies to decide who holds it,
 * and {@link MarketFootprint#marketCount()} is how many that sample banked for each bloc.
 *
 * <p>So there is no second walk and no second rule here, only an ordering. Every market the
 * count reports is one the dominance score was summed over, which is what makes a band on a
 * held cell a readout of the very sample the fill beneath it was decided from - the claim side
 * reaches the same guarantee the long way round, from the contest's own market list.
 *
 * <p>The order is the fill's order. The bloc the cell was painted for leads, whatever settled
 * that - a plain weight lead, or one of the tie-breaks below the weights - so a reader never
 * finds the leading colour second in a band on a cell painted in it. Behind the leader the
 * blocs run by descending weight and then by id, the same total order the standings box ranks
 * them in, so the band and the box agree about who is second.
 *
 * <p>Pure over the footprints a pass already read, with the one live read - a bloc's shades -
 * inverted to {@link BlocPaletteReader}, exactly as on the claim side.
 */
public final class HeldCellRibbons {

    private HeldCellRibbons() {
    }

    /**
     * Plans one held cell's ribbon from the footprints its system was ranked by.
     *
     * @param paintingBlocId the bloc the cell's fill was painted for - the dominant bloc, which
     *                       leads the band whatever settled its lead
     * @param footprintByBlocId each bloc's footprint in the cell's system, as the dominance pass
     *                       folded them under the view's grouping
     * @param palettes       where each present bloc's two shades are read from
     * @param lengths        how far a market's segment and an interjection run
     * @return the cell's runs in draw order, or {@link RibbonPlan#NONE} where no bloc but the
     *         painter is present
     */
    public static RibbonPlan planHeldCellRibbon(
            String paintingBlocId,
            Map<String, MarketFootprint> footprintByBlocId,
            BlocPaletteReader palettes,
            RibbonSegmentLengths lengths) {

        return RibbonPlan.planCellRibbon(
            paintingBlocId,
            resolveRankedPresences(paintingBlocId, footprintByBlocId, palettes),
            lengths);
    }

    // The blocs present in the system in the band's order: the painter, then the rest by
    // descending weight and by id.
    private static List<BlocPresence> resolveRankedPresences(
            String paintingBlocId,
            Map<String, MarketFootprint> footprintByBlocId,
            BlocPaletteReader palettes) {

        var ranked = new ArrayList<>(footprintByBlocId.entrySet());
        ranked.sort(orderByPaintedThenWeight(paintingBlocId));

        var presences = new ArrayList<BlocPresence>(ranked.size());

        for (var blocFootprint : ranked) {
            var palette = palettes.readBlocPalette(blocFootprint.getKey());

            // A bloc with no shades to draw in is dropped rather than painted colourless, exactly
            // as the holder resolve drops a system whose colour faction has gone.
            if (palette != null) {
                presences.add(new BlocPresence(
                    blocFootprint.getKey(),
                    palette,
                    blocFootprint.getValue().marketCount()));
            }
        }
        return presences;
    }

    // The band's order. The painter sorts ahead of everything because the dominance rule can
    // hand a system to a bloc that leads on none of the weights - a tie settled by the market
    // nearest the system centre, or by id - and a band that re-ranked from the weights alone
    // would then open on a bloc the cell is not painted for.
    //
    // Behind it, descending combined weight and then ascending id: the same two keys, in the
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
