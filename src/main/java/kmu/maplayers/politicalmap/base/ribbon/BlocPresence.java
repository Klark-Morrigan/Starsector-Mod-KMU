package kmu.maplayers.politicalmap.base.ribbon;

import kmlib.starsector.factions.FactionPalette;

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
}
