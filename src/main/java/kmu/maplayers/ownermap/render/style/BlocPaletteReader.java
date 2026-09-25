package kmu.maplayers.ownermap.render.style;

import kmlib.starsector.factions.FactionPalette;

/**
 * Where the two shades a bloc paints in are read from.
 *
 * <p>A bloc is not something the sector can simply be asked for. A group - a bloc made of several
 * factions - carries a synthetic ID no {@code FactionAPI} answers to, and a bloc reaches a caller
 * from a mechanic that scores IDs rather than colouring them - a contest hands back faction IDs and
 * nothing else. Turning one into a pair of shades therefore means reading the live faction it
 * paints in, which a rule meant to hold over plain values must not do.
 *
 * <p>Inverted to a port for that reason: a caller settles what it is drawing and asks here for
 * colour, so its own rule stays pure over the values it is handed while the sector read stays where
 * the sector is.
 */
@FunctionalInterface
public interface BlocPaletteReader {

    /**
     * The shades the given bloc draws in.
     *
     * @param blocId the bloc to colour: a faction ID, or a group's synthetic bloc ID
     * @return the bloc's bright and dark shades, or null where the bloc has no colour to resolve
     *         at all - the degenerate case of a bloc whose colour faction has gone from the sector
     */
    FactionPalette readBlocPalette(String blocId);
}
