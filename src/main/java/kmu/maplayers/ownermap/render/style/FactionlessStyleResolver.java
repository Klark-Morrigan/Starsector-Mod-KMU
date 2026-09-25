package kmu.maplayers.ownermap.render.style;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.theme.ElementStyleAdjustment;

import java.util.Set;

/**
 * Resolves the two style decisions an ownerless cell takes - which factionless category
 * it draws in, and how far it recedes - as pure rules over plain values. The factionless
 * counterpart to {@link OwnerStyleResolver}: a cell with no holder has no bloc to carry a decision,
 * so its rules are stated here rather than falling out of the per-bloc path.
 *
 * <p>Both rules are stated once because more than one pass classifies the same cells and the two
 * must agree - and because the answer for a cell drawn as no system at all is the detail a second
 * copy of the rule loses first.
 *
 * <p>Neither rule reads the holder map. A cell reaches here precisely because this pass found no
 * holder for it, and that says nothing about whether the system is empty - only that whatever
 * stands there falls outside what this layer's holding admits.
 */
public final class FactionlessStyleResolver {

    // Resolves only; never instantiated.
    private FactionlessStyleResolver() {
    }

    /**
     * Which factionless category a cell falls under:
     * {@link OwnerMapCategory#DECIVILISED} where something stands that this pass's holding
     * does not account for, {@link OwnerMapCategory#UNINHABITED} where nothing stands at
     * all.
     *
     * <p>The question is what is <em>in</em> the system, not whether this pass found a holder
     * for it. The two answers part company as soon as a layer resolves holding under a rule
     * that admits only some markets, for reasons the layer's own holding rule sets, leaving
     * settled systems with no holder at all. Reading emptiness off the holder map would call such
     * a system empty
     * space and hide it behind the uninhabited-systems checkbox - a populated system erased
     * from the map by a toggle that names the opposite of what it holds.
     *
     * <p>That is a rule about where the answer is read from, not a promise that every populated
     * system lands in {@link OwnerMapCategory#DECIVILISED}. The set handed in is the pass's
     * habitation, and a player may have said a decivilised world is not somebody living in its
     * system; a system holding nothing else is then absent from the set and falls to
     * {@link OwnerMapCategory#UNINHABITED} on the player's own word. What is ruled out above
     * is the map deciding that for itself off a holder map the player never saw.
     *
     * <p>A live colony and a decivilised one therefore share the one bundle. The distinction the
     * two factionless styles draw is presence against absence - is anything here, or is this the
     * backdrop - and on that question an unheld colony and a revealed decivilised world answer
     * alike. Which kind of settlement it is would be a third bundle's worth of theme and settings
     * to say, and the map says it in the hover box instead.
     *
     * @param inhabitedSystemKeys the systems something stands in this pass, live colony or - where
     *                            the player has left such a world counting as habitation - a
     *                            revealed decivilised one
     * @param systemKey           the system the cell draws as, or null for a cell with no star
     *                            of its own - a shard of leftover space, which names nothing to
     *                            look up and so is never inhabited. The null key never reaches
     *                            the set, which may be immutable and null-hostile.
     * @return the category the cell draws in
     */
    public static OwnerMapCategory resolveCategoryOf(
            Set<SystemKey> inhabitedSystemKeys,
            SystemKey systemKey) {

        return systemKey != null && inhabitedSystemKeys.contains(systemKey)
            ? OwnerMapCategory.DECIVILISED
            : OwnerMapCategory.UNINHABITED;
    }

    /**
     * How far a factionless cell recedes this pass.
     *
     * <p>A settled cell is part of the "rest of the sector" a spotlight recedes: an inhabited
     * system nobody here holds is a feature of the map drawn in a fill of its own, so leaving it at
     * full strength lets it out-read the bloc the spotlight is meant to isolate. An uninhabited
     * cell is the empty backdrop
     * the whole map is drawn over rather than anything the spotlight competes with, and its faint
     * outline is what gives the sector its shape, so it never recedes.
     *
     * <p>A settled cell the spotlighted bloc <em>lives in</em> is the exception to that. Sinking it
     * would hide the pick's own colonies for the sole reason that this layer's holding rule cannot
     * account for them. The recede clears away what the pick is not, and a system the pick is
     * living in is not that.
     *
     * <p>Such a cell stays at full strength <em>as it already draws</em> - neutral, in the settled
     * bundle, identical to how an unfiltered map paints it - rather than taking the spotlight's
     * colours. Nobody holds the system on this layer, so painting it in the bloc's own shades
     * would state exactly the holding the view is drawn to report it does not have.
     *
     * @param category             the category the cell draws in
     * @param passRecede           the recede every non-spotlighted bloc takes this pass, which is
     *                             {@link ElementStyleAdjustment#NONE} off filter - so an unfiltered
     *                             map draws its settled cells untouched
     * @param isSpotlitBlocPresent whether the spotlighted bloc owns a counted colony in this cell's
     *                             system. False for every cell off filter, so the exception cannot
     *                             fire on an unfiltered map
     * @return the adjustment the cell draws under
     */
    public static ElementStyleAdjustment resolveRecedeOf(
            OwnerMapCategory category,
            ElementStyleAdjustment passRecede,
            boolean isSpotlitBlocPresent) {

        if (category != OwnerMapCategory.DECIVILISED || isSpotlitBlocPresent) {
            return ElementStyleAdjustment.NONE;
        }
        return passRecede;
    }
}
