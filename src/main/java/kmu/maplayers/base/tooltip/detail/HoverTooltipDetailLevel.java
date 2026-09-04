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
 * <p>The cycle is declaration order, wrapping back to the first - which is what lets one key be both
 * the way into detail and the way out of it. Where it wraps is not fixed at the last constant: a box
 * states the deepest level its own tree holds anything at, and the press collapses from there. The
 * levels below name tiers no box is obliged to have - a claim is settled over colonies without a
 * patrol entering it anywhere - and a cycle wrapping at the last constant regardless would offer such
 * a box a level that redraws exactly what is already on screen.
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

    // Where the cycle opens and wraps back to. Read off declaration order rather than named, so a
    // level inserted ahead of the present first one moves the wrap target with it.
    private static final int SHALLOWEST_ORDINAL = 0;

    // Stated per constant rather than derived from position, so what a level admits is read off the
    // constant that names it instead of counted from wherever it happens to sit in the cycle.
    private final int maximumSubordination;

    // What arriving at this level does, in the player's words - "expand market stats" for the level
    // that adds them. Held by the level being arrived at rather than by the one being left, so a
    // level added or reordered carries its own wording with it instead of leaving a phrase behind
    // describing a step that no longer lands here.
    //
    // The shallowest is worded as a collapse because that is the only way a press reaches it: it is
    // where the box opens, and the cycle arrives there only by wrapping from wherever the box being
    // read runs out of tiers.
    private final String arrivalPhraseKey;

    HoverTooltipDetailLevel(int maximumSubordination, String arrivalPhraseKey) {
        this.maximumSubordination = maximumSubordination;
        this.arrivalPhraseKey = arrivalPhraseKey;
    }

    /**
     * @return the deepest level the cycle declares - the bound a box holding every tier there is to
     *         hold would state, and the level past which no box can hold anything. What lets a
     *         caller settle where a press lands without asking any box how deep it goes
     */
    public static HoverTooltipDetailLevel resolveDeepestLevel() {

        var levels = values();

        return levels[levels.length - 1];
    }

    /**
     * The level one press moves to, given how deep the box being read actually goes: the next deeper
     * level, or the shallowest again once {@code deepestHeldLevel} has been reached - so a press
     * always acts and any box can be collapsed from wherever its own tree ends.
     *
     * <p>Bounded by the box rather than by the cycle, because the two are not the same depth. A box
     * whose tree ends above the last constant would otherwise be offered levels that redraw what is
     * already on screen, and the player would press through them to reach the collapse.
     *
     * <p>Wrapping is judged at or past the bound rather than exactly at it, since the level is one
     * shared fact held across layer switches: a box reached at a level deeper than it holds anything
     * at collapses on the next press rather than sitting at a depth it cannot act on.
     *
     * @param deepestHeldLevel the deepest level the box being read holds anything at
     * @return the level to move to, which is this level itself where there is nowhere else to go -
     *         a box holding nothing past the shallowest, read at the shallowest
     */
    public HoverTooltipDetailLevel resolveNextLevelWithin(HoverTooltipDetailLevel deepestHeldLevel) {

        var levels = values();

        if (isReadingAtLeast(deepestHeldLevel)) {
            return levels[SHALLOWEST_ORDINAL];
        }
        // Short of the bound there is always a deeper constant to step to, the bound being one of
        // these levels itself - so the step needs no wrap of its own.
        return levels[ordinal() + 1];
    }

    /**
     * What arriving at this level does, in the player's words - the whole of what the hint at the
     * foot of a box says beside the key.
     *
     * <p>Stated as the action rather than as the level's name, because a number or a name tells the
     * player nothing about what they would gain by pressing: the hint exists to say what the key is
     * for. Read off the level being arrived at rather than off the one being left, so the phrase and
     * the press it describes cannot come apart.
     *
     * @return the phrase for arriving here
     */
    public String resolveArrivalPhrase() {
        return KmuStrings.get(arrivalPhraseKey);
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
