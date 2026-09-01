package kmu.maplayers.base.tooltip.detail;

import kmu.util.KmuStrings;

/**
 * How deep the hover box reads the one tree a layer composes for a system: four ordered depths,
 * cycled on one key, each admitting one more tier of the same account.
 *
 * <p>A level is a cut, not a variant: it states the deepest subordination the box may show, so every
 * level draws the same tree to a different depth and two systems can be compared at any of them. A
 * depth chosen by the player rather than inferred from what fits, because a box whose depth cannot
 * be predicted cannot be read comparatively.
 *
 * <p>What is drawn is the cut's alone, applied where a body is laid out
 * ({@code kmu.maplayers.base.tooltip.layout}), which is what leaves a layer free to
 * stop composing a tier the level would drop - worth doing wherever a tier is expensive to work out,
 * since one composed and then cut has already cost whatever it took to read and word, leaving the
 * level that shows the least costing the most. The two questions below are what a layer asks before
 * working a tier out at all.
 *
 * <p>The cycle is declaration order, wrapping from the deepest back to the first - which is what
 * lets one key be both the way into detail and the way out of it.
 */
public enum HoverTooltipDetailLevel {

    /** Who holds the system: the groups holding it, and the factions gathered inside one. */
    FACTIONS(0, KmuStrings.MAP_LAYER_TOOLTIP_FOOTER_COLLAPSE_FACTIONS),

    /** Adds the markets each faction holds the system with. */
    SYSTEM_COMPOSITION(1, KmuStrings.MAP_LAYER_TOOLTIP_FOOTER_EXPAND_SYSTEM_COMPOSITION),

    /** Adds stability, size and the patrol total per market. */
    MARKET_STATS(2, KmuStrings.MAP_LAYER_TOOLTIP_FOOTER_EXPAND_MARKET_STATS),

    /** Adds the small/medium/large split behind each patrol total. */
    PATROL_DETAILS(3, KmuStrings.MAP_LAYER_TOOLTIP_FOOTER_EXPAND_PATROL_DETAILS);

    // How far under the box's own voice a line breaking down a listed one stands - what an account
    // *is*, rather than which level first admits one. The same one step the laying-out takes when it
    // subordinates a line beneath the one it explains, stated here as a depth so a level asks about
    // accounts without reaching into how a box is laid out.
    private static final int ACCOUNT_SUBORDINATION = 1;

    // Stated per constant rather than derived from position, so what a level admits is read off the
    // constant that names it instead of counted from wherever it happens to sit in the cycle.
    private final int maximumSubordination;

    // What arriving at this level does, in the player's words - "expand market stats" for the level
    // that adds them. Held by the level being arrived at rather than by the one being left, so a
    // level added or reordered carries its own wording with it instead of leaving a phrase behind
    // describing a step that no longer lands here.
    //
    // The shallowest is worded as a collapse because that is the only way the cycle reaches it: it
    // is where the box opens, and a press arrives there only by wrapping from the deepest.
    private final String arrivalPhraseKey;

    HoverTooltipDetailLevel(int maximumSubordination, String arrivalPhraseKey) {
        this.maximumSubordination = maximumSubordination;
        this.arrivalPhraseKey = arrivalPhraseKey;
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
     * What the next press does to a box drawn at this level, in the player's words - the whole of
     * what the hint at its foot says beside the key.
     *
     * <p>Stated as the action rather than as the level the box is at, because a number or a name
     * tells the player nothing about what they would gain by pressing: the hint exists to say what
     * the key is for. Read off the level being moved to, so the phrase and the press it describes
     * cannot come apart.
     *
     * @return the phrase for the step one press takes from here
     */
    public String resolveNextActionPhrase() {
        return KmuStrings.get(getNextLevel().arrivalPhraseKey);
    }

    /**
     * Whether the next press takes the box back to the shallowest level rather than one tier deeper.
     * The cycle wraps, so this holds at the deepest level alone - the one place the key collapses the
     * box instead of opening it further.
     *
     * @return true where one press collapses the box
     */
    public boolean isCollapsingOnNextPress() {
        return getNextLevel() == FACTIONS;
    }

    /**
     * Whether this level shows a line standing as the account of one the box lists - what any layer
     * asks before working an account out, whatever its subject matter.
     *
     * <p>Read off what an account <em>is</em> - one step under the box's voice from a listed line -
     * rather than off the level that happens to be the first to admit one. Named instead, a level
     * inserted between two of these would leave this answering about the wrong one.
     *
     * @return true where an account beneath a listed line is shown
     */
    public boolean isAdmittingAccounts() {
        return maximumSubordination >= ACCOUNT_SUBORDINATION;
    }

    /**
     * Whether the box has been asked to read at least as deep as {@code level}.
     *
     * <p>Asked of a level rather than of a depth, because what a composer knows is which tier of its
     * own subject matter it is about to build and each constant here names one. Counted against a
     * number instead, every layer would be counting its own tiers, and two of them would eventually
     * count differently over one box.
     *
     * @param level the level whose content is in question
     * @return true where this level admits everything that one admits
     */
    public boolean isReadingAtLeast(HoverTooltipDetailLevel level) {
        return maximumSubordination >= level.maximumSubordination;
    }

    /**
     * Read where a listing is laid out and nowhere else: a level is otherwise passed around whole,
     * by whoever draws a box, and asked what it admits through the questions above.
     *
     * @return the deepest {@code subordinationLevel} a line may carry and still be shown at this
     *         level. The cut is on subordination rather than indent, so a line set in without being
     *         demoted - an alliance's member factions under the line naming the alliance - survives
     *         the shallowest level, being the content that level exists to show.
     */
    public int getMaximumSubordination() {
        return maximumSubordination;
    }
}
