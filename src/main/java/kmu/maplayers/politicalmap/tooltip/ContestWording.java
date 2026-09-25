package kmu.maplayers.politicalmap.tooltip;

import kmu.util.KmuStringKeys;

/**
 * How a political box words the block holding everyone present who stands neither in the holder's
 * alliance nor on good terms with it.
 *
 * <p>Which groups land in that block does not move - the wording is the only thing that does -
 * because what differs between installs is not who is present but what being present amounts to.
 * Where a mod transfers systems between factions, the groups around a holder are genuinely competing
 * for the one the player is hovering, and naming that a contest reports something they can watch play
 * out. Where nothing transfers anything, the sector's holdings are fixed for the whole game: the same
 * groups sit beside each other from the first cycle to the last, and a heading calling that a contest
 * describes a war that will never come.
 *
 * <p>A closed pair rather than a heading string chosen at each call site, so the two shapes that draw
 * the block cannot word one hover as a contest and the tab beside it as mere presence.
 *
 * <p>The block that turns on this is the only one that does. The holder's own, the allied and the
 * friendly blocks each name a relation that is just as true of a sector nothing ever moves, and the
 * non-political one is about candidacy rather than about conflict.
 */
public enum ContestWording {

    /**
     * Systems change hands, so presence beside a holder is a claim on the system - which is what the
     * player is being told when the block names it.
     */
    CONTESTED(KmuStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_CONTESTED),

    /**
     * Systems never change hands, so presence beside a holder is presence and nothing further. The
     * block still earns its place - it is where the player reads who else is in the system and what
     * they are worth there - and it simply stops asserting a struggle over it.
     */
    PRESENT(KmuStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_PRESENT);

    private final String headingKey;

    ContestWording(String headingKey) {
        this.headingKey = headingKey;
    }

    /**
     * The strings key the block draws its heading from under this wording.
     *
     * @return the key, resolved against the player's own language where the block is drawn
     */
    public String resolveHeadingKey() {
        return headingKey;
    }
}
