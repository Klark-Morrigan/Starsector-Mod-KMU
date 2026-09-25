package kmu.maplayers.politicalmap.tooltip;

/**
 * A block a political-map box lists the factions around a system's holder under, as far as its heading
 * goes: the strings key it draws under, given how the install has the contest worded.
 *
 * <p>Shared by the claim and the domination block sets so one walk lays either of them down
 * ({@link PoliticalMapCellTooltip#appendContestBlockSections}). The sets themselves stay apart, their
 * blocks placing different things by different rules; what they have in common is that every block is
 * headed, and that one heading in each turns on the install ({@link ContestWording}).
 */
public interface ContestBlockHeading {

    /**
     * The strings key of the heading this block draws under.
     *
     * @param contestWording how the install has the contest worded, which every block is offered and
     *                       only the one naming the holder's rivals reads
     * @return the key, resolved against the player's own language where the block is drawn
     */
    String resolveHeadingKey(ContestWording contestWording);
}
