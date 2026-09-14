package kmu.maplayers.politicalmap.base.render.style;

import kmlib.starsector.factions.FactionPalette;

/**
 * Where the two shades a bloc paints in are read from.
 *
 * <p>A bloc is not something the sector can simply be asked for. An alliance bloc carries a
 * synthetic ID no {@code FactionAPI} answers to, and a bloc reaches a caller from a mechanic that
 * scores IDs rather than colouring them - a claim contest hands back faction IDs and nothing else.
 * Turning one into a pair of shades therefore means reading the live faction it paints in, which is
 * the one thing a rule stated over hand-built values must not do.
 *
 * <p>Inverted to a port for that reason: a caller settles what it is drawing and asks here for
 * colour, so its own rule stays exercisable on literals while the sector read stays where the
 * sector is.
 */
@FunctionalInterface
public interface BlocPaletteReader {

    /**
     * The shades the given bloc draws in.
     *
     * @param blocId the bloc to colour: a faction ID on the faction view, an alliance bloc ID on
     *               the alliances view
     * @return the bloc's bright and dark shades, or null where the bloc has no colour to resolve
     *         at all - the degenerate case of a bloc whose colour faction has gone from the sector
     */
    FactionPalette readBlocPalette(String blocId);
}
