package kmu.maplayers.ownermap.owners.holders.base;

import kmlib.colour.ColourPair;

/**
 * Where the two shades one colony's segment of a band draws in are read from.
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
 *
 * <p>Asked per colony, by the bloc it is counted under and the faction owning it, because the
 * grouping decides what a run is and this read decides what a segment is. A layer colouring by the
 * bloc answers alike for every member, which draws a run in one pair. A layer colouring by
 * something a bloc does not decide answers per member, so two members' colonies differ inside one
 * run.
 */
@FunctionalInterface
public interface RibbonPaletteReader {

    /**
     * The shades one colony's segment draws in.
     *
     * @param blocId         the bloc the colony is counted under: a faction ID, or a group's
     *                       synthetic bloc ID
     * @param ownerFactionId the faction owning the colony, which is the bloc itself outside a group
     * @return the segment's bright and dark shades, or null where there is no colour to resolve at
     *         all - the degenerate case of a faction gone from the sector - which drops that
     *         colony's segment
     */
    ColourPair readSegmentPalette(String blocId, String ownerFactionId);
}
