package kmu.maplayers.politicalmap.base.render.style;

import kmu.maplayers.base.theme.ElementStyleAdjustment;

import java.util.Set;

/**
 * Resolves the two style decisions an ownerless cell takes - which factionless category
 * it draws in, and how far it recedes - as pure rules over plain values. The factionless
 * counterpart to {@link BlocStyleResolver}: a cell with no holder has no bloc to carry a decision,
 * so its rules are stated here rather than falling out of the per-bloc path.
 *
 * <p>Both rules are stated once because more than one pass classifies the same cells and the two
 * must agree - and because the answer for a cell drawn as no system at all is the detail a second
 * copy of the rule loses first.
 */
public final class FactionlessStyleResolver {

    // Resolves only; never instantiated.
    private FactionlessStyleResolver() {
    }

    /**
     * Which factionless category a cell falls under:
     * {@link PoliticalMapCategory#DECIVILISED} where a revealed dead colony sits,
     * {@link PoliticalMapCategory#UNINHABITED} everywhere else.
     *
     * @param decivilisedSystemIds the systems holding a revealed dead colony this pass
     * @param systemId             the system the cell draws as, or null for a cell with no star
     *                             of its own - a shard of leftover space, which names nothing to
     *                             look up and so is never decivilised. The null id never reaches
     *                             the set, which may be immutable and null-hostile.
     * @return the category the cell draws in
     */
    public static PoliticalMapCategory resolveCategoryOf(
            Set<String> decivilisedSystemIds,
            String systemId) {

        return systemId != null && decivilisedSystemIds.contains(systemId)
            ? PoliticalMapCategory.DECIVILISED
            : PoliticalMapCategory.UNINHABITED;
    }

    /**
     * How far a factionless cell recedes this pass.
     *
     * <p>A decivilised cell is part of the "rest of the sector" a spotlight recedes: a dead colony
     * is a political feature drawn in a fill of its own, so leaving it at full strength lets it
     * out-read the bloc the spotlight is meant to isolate. An uninhabited cell is the empty backdrop
     * the whole map is drawn over rather than anything the spotlight competes with, and its faint
     * outline is what gives the sector its shape, so it never recedes.
     *
     * @param category   the category the cell draws in
     * @param passRecede the recede every non-spotlighted bloc takes this pass, which is
     *                   {@link ElementStyleAdjustment#NONE} off filter - so an unfiltered map draws
     *                   its dead worlds untouched
     * @return the adjustment the cell draws under
     */
    public static ElementStyleAdjustment resolveRecedeOf(
            PoliticalMapCategory category,
            ElementStyleAdjustment passRecede) {

        return category == PoliticalMapCategory.DECIVILISED
            ? passRecede
            : ElementStyleAdjustment.NONE;
    }
}
