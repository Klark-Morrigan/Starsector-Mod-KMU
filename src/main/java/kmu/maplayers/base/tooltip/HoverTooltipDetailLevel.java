package kmu.maplayers.base.tooltip;

/**
 * How deep the hover box reads the one tree a layer composes for a system: four ordered depths,
 * cycled on one key, each admitting one more tier of the same account.
 *
 * <p>A level is a cut, not a variant: it states the deepest subordination the box may show, so every
 * level draws the same tree to a different depth and two systems can be compared at any of them. A
 * depth chosen by the player rather than inferred from what fits, because a box whose depth cannot
 * be predicted cannot be read comparatively.
 *
 * <p>The cycle is declaration order, wrapping from the deepest back to the first - which is what
 * lets one key be both the way into detail and the way out of it.
 */
public enum HoverTooltipDetailLevel {

    /** Who holds the system: the groups holding it, and the factions gathered inside one. */
    FACTIONS(0),

    /** Adds the markets each faction holds the system with. */
    SYSTEM_COMPOSITION(1),

    /** Adds stability, size and the patrol total per market. */
    MARKET_STATS(2),

    /** Adds the small/medium/large split behind each patrol total. */
    PATROL_DETAILS(3);

    // Stated per constant rather than derived from position, so what a level admits is read off the
    // constant that names it instead of counted from wherever it happens to sit in the cycle.
    private final int maximumSubordination;

    HoverTooltipDetailLevel(int maximumSubordination) {
        this.maximumSubordination = maximumSubordination;
    }

    /**
     * @return the level one press moves to: the next deeper one, or the first again from the
     *         deepest - wrapping rather than stopping, so a press always acts and the deepest box
     *         can always be left
     */
    public HoverTooltipDetailLevel getNextLevel() {

        var levels = values();

        return levels[(ordinal() + 1) % levels.length];
    }

    /**
     * Kept to this package: a level is passed around by whoever draws a box, and read for what it
     * admits only where a listing is laid out.
     *
     * @return the deepest {@code subordinationLevel} a line may carry and still be shown at this
     *         level. The cut is on subordination rather than indent, so a line set in without being
     *         demoted - an alliance's member factions under the line naming the alliance - survives
     *         the shallowest level, being the content that level exists to show.
     */
    int getMaximumSubordination() {
        return maximumSubordination;
    }
}
