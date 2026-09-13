package kmu.maplayers.politicalmap.base.ribbon;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.politicalmap.base.render.style.BlocPaletteReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One bloc's stake in one cell's presence ribbon: who it is, the two shades its runs draw
 * in, and how many markets it holds in the cell.
 *
 * <p>The ribbon counts markets rather than scaling weights, so a bloc arrives here as a
 * plain count. A readout of "how many colonies, and whose" is something a reader can check
 * against the map; a summed weight is not, and no weight can be divided back into the count
 * that would be.
 *
 * <p>Which colonies that count stands for is settled before this value is built, by
 * {@link ColonyCellRibbons} for every layer alike: the rule on {@link RibbonPlan} counts what
 * it is handed and asks nothing about where the number came from. What reaches it per mechanic
 * is the order, so a band cannot mean one thing on the claims layer and another on the
 * dominance ones.
 *
 * <p>The palette is the bloc's own authored pair - bright into the market segments, dark
 * into every parting, both the ones between its markets and the divider closing its run
 * where another bloc's follows - so a run draws in the same two shades the rest of the map
 * already gives that bloc.
 *
 * @param blocId      the bloc this stake belongs to: a faction ID in the faction view, an
 *                    alliance bloc ID in the alliances view
 * @param palette     the bloc's bright and dark shades, the segment and parting colours of
 *                    its run
 * @param marketCount how many colonies the bloc holds in the cell that the player may be
 *                    shown; a bloc holding none is present in nothing the ribbon reports, so
 *                    it neither draws a run nor counts as presence
 */
public record BlocPresence(
    String blocId,
    FactionPalette palette,
    int marketCount) {

    // A bloc holding nothing the player may be shown, which is the count presence starts above.
    private static final int NO_MARKETS = 0;

    /**
     * Colours an already-ordered set of bloc counts into the presences a plan is stated over.
     *
     * <p>The step both mechanics finish on, held here rather than at each of them, because it is
     * the one part of the walk that is not about counting: whatever a mechanic counted and however
     * it ranked what it counted, the colours are looked up the same way and a bloc with none is
     * dropped the same way. Two copies of that would be two places for a bloc to survive
     * colourless.
     *
     * <p>Dropped rather than painted in a stand-in shade, since a bloc whose colour faction has
     * gone from the sector is one the map cannot name - the same answer the holder and claim
     * resolves give a system whose colour faction has gone.
     *
     * @param marketCountByBlocId how many markets each bloc holds, in the order its runs are to be
     *                            drawn - the caller's ranking is kept exactly
     * @param palettes            where each bloc's two shades are read from
     * @return the blocs that have shades to draw in, in the order given
     */
    public static List<BlocPresence> collectColouredPresences(
            Map<String, Integer> marketCountByBlocId,
            BlocPaletteReader palettes) {

        var presences = new ArrayList<BlocPresence>(marketCountByBlocId.size());

        for (var blocMarketCount : marketCountByBlocId.entrySet()) {
            var palette = palettes.readBlocPalette(blocMarketCount.getKey());

            if (palette != null) {
                presences.add(new BlocPresence(
                    blocMarketCount.getKey(),
                    palette,
                    blocMarketCount.getValue()));
            }
        }
        return presences;
    }

    /**
     * Whether the bloc holds anything a band can report.
     *
     * <p>The one place the rule above is spelled: a bloc counted at nothing draws no run, does not
     * contest a cell, and does not keep one from being bare. Held here rather than as a comparison
     * at each reader, since three readers asking it three times is three chances for one of them to
     * come to mean something slightly different by presence.
     *
     * @return true where the bloc holds at least one colony the player may be shown
     */
    public boolean hasMarkets() {
        return marketCount > NO_MARKETS;
    }
}
