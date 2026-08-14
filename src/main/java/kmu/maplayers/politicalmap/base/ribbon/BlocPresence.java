package kmu.maplayers.politicalmap.base.ribbon;

import kmlib.starsector.factions.FactionPalette;

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
 * <p>Which markets that count stands for is settled before this value is built. The rule on
 * {@link RibbonPlan} counts what it is handed and asks nothing about where the number came
 * from, which is what lets a held cell count the sample the dominance rule was decided on
 * and a claimed cell count the claim contest's own market list, through one rule rather than
 * two that could disagree about what a band means.
 *
 * <p>The palette is the bloc's own authored pair - bright into the market segments, dark
 * into the interjections between them - so a run draws in the same two shades the rest of
 * the map already gives that bloc.
 *
 * @param blocId      the bloc this stake belongs to: a faction id in the faction view, an
 *                    alliance bloc id in the alliances view
 * @param palette     the bloc's bright and dark shades, the segment and interjection
 *                    colours of its run
 * @param marketCount how many markets the bloc holds in the cell that affected the score
 *                    the cell was painted from; a bloc holding none is present in nothing
 *                    the ribbon reports, so it neither draws a run nor counts as presence
 */
public record BlocPresence(
    String blocId,
    FactionPalette palette,
    int marketCount) {

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
}
