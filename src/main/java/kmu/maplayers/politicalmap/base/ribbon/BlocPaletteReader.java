package kmu.maplayers.politicalmap.base.ribbon;

import kmlib.starsector.factions.FactionPalette;

/**
 * Where the two shades a bloc's ribbon runs draw in are read from.
 *
 * <p>A cell's fill carries the palette of the one bloc it was painted for, and a ribbon reports
 * every bloc present in the system instead - so the rest of them arrive named by a mechanic that
 * colours none of them. A claim contest hands back faction ids and scores and nothing else, and
 * turning one of those into a pair of shades means reading the live faction a bloc paints in,
 * which is the one thing a rule stated over hand-built standings must not do.
 *
 * <p>Inverted to a port for that reason: the rule folds counts and asks here for colour, so the
 * fold stays exercisable on literals while the sector read stays where the sector is.
 */
@FunctionalInterface
public interface BlocPaletteReader {

    /**
     * The shades the given bloc draws its runs in.
     *
     * @param blocId the bloc to colour: a faction id on the faction view, an alliance bloc id on
     *               the alliances view
     * @return the bloc's bright and dark shades, or null where the bloc has no colour to resolve
     *         at all - the degenerate case of a bloc whose colour faction has gone from the sector
     */
    FactionPalette readBlocPalette(String blocId);
}
