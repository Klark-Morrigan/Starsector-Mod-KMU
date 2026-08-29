package kmu.maplayers.politicalmap.base.tooltip;

import kmu.util.KmuStrings;

/**
 * The blocks a hovered system's standings are listed under, in the order a box lays them down: who
 * holds the system, who stands with them, who contests it, and who was never in the running at all.
 *
 * <p>A closed set rather than a heading string handed around, so which block a group falls in is an
 * answer the compiler checks: a block stated here is a block the box lays down, and a routing that
 * stopped placing one fails to compile where a misspelled heading key would simply have drawn a
 * block that never filled.
 *
 * <p>Each block carries the heading it draws under, because the two are one decision - a block is
 * the groups it takes under the wording naming them - and the order they read in is this
 * declaration order rather than a sequence of calls somewhere else that could drift from it.
 */
public enum StandingBlock {

    /**
     * The bloc the map fills the system in the colour of. The strongest of those in the running,
     * which is what keeps the box an explanation of the cell beneath it.
     */
    HOLDER(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_DOMINATED),

    /** The blocs the alliance set folds into the holder's own, which are not contesting it. */
    ALLIED(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_ALLIED_WITH_SYSTEM_HOLDER),

    /** The blocs standing against the holder. */
    CONTESTED(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_CONTESTED),

    /**
     * The blocs that take no part in the contest for the system. Listed with whatever they scored,
     * and never named as holding it. Which blocs those are is
     * {@link kmu.maplayers.politicalmap.base.dominance.BlocCandidacy}'s answer.
     */
    NON_POLITICAL(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_NON_POLITICAL);

    private final String headingKey;

    StandingBlock(String headingKey) {
        this.headingKey = headingKey;
    }

    /**
     * The strings key of the heading this block draws under.
     *
     * @return the key, resolved against the player's own language where the block is drawn
     */
    public String resolveHeadingKey() {
        return headingKey;
    }
}
