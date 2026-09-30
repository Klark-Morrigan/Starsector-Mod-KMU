package kmu.maplayers.ownermap.render.style;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.theme.ElementStyleAdjustment;

import java.util.Set;

/**
 * Resolves the two style decisions an unowned cell takes - whether it is settled, which picks its
 * category off the layer's declaration, and how far it recedes - as pure rules over plain values.
 * The unowned counterpart to {@link OwnerStyleResolver}: a cell with no owner has no owner to carry
 * a decision, so its rules are stated here rather than falling out of the per-owner path.
 *
 * <p>Both rules are stated once because more than one pass classifies the same cells and the two
 * must agree - and because the answer for a cell drawn as no system at all is the detail a second
 * copy of the rule loses first.
 *
 * <p>Neither rule reads the owner map. A cell reaches here precisely because this pass found no
 * owner for it, and that says nothing about whether the system is empty - only that whatever
 * stands there falls outside what this layer's holding admits.
 */
public final class FactionlessStyleResolver {

    // Resolves only; never instantiated.
    private FactionlessStyleResolver() {
    }

    /**
     * Whether an unowned cell is settled - something stands in its system that this pass's holding
     * does not account for - rather than empty. The layer's declaration turns the answer into a
     * category ({@link OwnerCategories#resolveUnownedCategory}).
     *
     * <p>The question is what is <em>in</em> the system, not whether this pass found an owner
     * for it. The two answers part company as soon as a layer resolves holding under a rule
     * that admits only some markets, for reasons the layer's own holding rule sets, leaving
     * settled systems with no owner at all. Reading emptiness off the owner map would call such
     * a system empty space and hide it behind the uninhabited-systems checkbox - a populated
     * system erased from the map by a toggle that names the opposite of what it holds.
     *
     * <p>That is a rule about where the answer is read from, not a promise that every populated
     * system counts as settled. The set handed in is the pass's habitation, and a player may have
     * said a decivilised world is not somebody living in its system; a system holding nothing else
     * is then absent from the set and counts as empty on the player's own word. What is ruled out
     * above is the map deciding that for itself off an owner map the player never saw.
     *
     * @param inhabitedSystemKeys the systems something stands in this pass, live colony or - where
     *                            the player has left such a world counting as habitation - a
     *                            revealed decivilised one
     * @param systemKey           the system the cell draws as, or null for a cell with no star
     *                            of its own - a shard of leftover space, which names nothing to
     *                            look up and so is never settled. The null key never reaches
     *                            the set, which may be immutable and null-hostile.
     * @return whether the cell is settled
     */
    public static boolean isSettledSystem(
            Set<SystemKey> inhabitedSystemKeys,
            SystemKey systemKey) {

        return systemKey != null && inhabitedSystemKeys.contains(systemKey);
    }

    /**
     * How far an unowned cell recedes this pass.
     *
     * <p>A settled cell is part of the "rest of the sector" a spotlight recedes: an inhabited
     * system nobody here holds is a feature of the map drawn in a fill of its own, so leaving it at
     * full strength lets it out-read the owner the spotlight is meant to isolate. An empty cell is
     * the backdrop the whole map is drawn over rather than anything the spotlight competes with,
     * and its faint outline is what gives the sector its shape, so it never recedes.
     *
     * <p>A settled cell the spotlighted owner <em>lives in</em> is the exception to that. Sinking it
     * would hide the pick's own colonies for the sole reason that this layer's holding rule cannot
     * account for them. The recede clears away what the pick is not, and a system the pick is
     * living in is not that.
     *
     * <p>Such a cell stays at full strength <em>as it already draws</em> - neutral, in the settled
     * bundle, identical to how an unfiltered map paints it - rather than taking the spotlight's
     * colours. Nobody holds the system on this layer, so painting it in the owner's own shades
     * would state exactly the holding the view is drawn to report it does not have.
     *
     * @param isSettled            whether the cell is settled rather than empty
     * @param passRecede           the recede every non-spotlighted owner takes this pass, which is
     *                             {@link ElementStyleAdjustment#NONE} off filter - so an unfiltered
     *                             map draws its settled cells untouched
     * @param isSpotlitBlocPresent whether the spotlighted owner has a counted colony in this cell's
     *                             system. False for every cell off filter, so the exception cannot
     *                             fire on an unfiltered map
     * @return the adjustment the cell draws under
     */
    public static ElementStyleAdjustment resolveRecedeOf(
            boolean isSettled,
            ElementStyleAdjustment passRecede,
            boolean isSpotlitBlocPresent) {

        if (!isSettled || isSpotlitBlocPresent) {
            return ElementStyleAdjustment.NONE;
        }
        return passRecede;
    }
}
