package kmu.maplayers.politicalmap.base.render.style;

import kmu.maplayers.base.theme.MapCategory;
import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;

import java.util.Set;

/**
 * Resolves the two style decisions a piece of ownerless ground takes - which factionless category
 * it draws in, and how far it recedes - as pure rules over plain values. The factionless
 * counterpart to {@link BlocStyleResolver}: ground with no owner has no bloc to carry a decision,
 * so its rules are stated here rather than falling out of the per-bloc path.
 *
 * <p>Both rules are stated once because more than one pass classifies the same ground and the two
 * must agree - and because the answer for a cell drawn as no system at all is the detail a second
 * copy of the rule loses first.
 */
public final class FactionlessStyleResolver {

    // Resolves only; never instantiated.
    private FactionlessStyleResolver() {
    }

    /**
     * Which factionless category a piece of ground falls under: {@link MapCategory#DECIVILISED}
     * where a revealed dead colony sits, {@link MapCategory#UNINHABITED} everywhere else.
     *
     * @param decivilisedSystemIds the systems holding a revealed dead colony this pass
     * @param systemId             the system the ground draws as, or null for ground with no star
     *                             of its own - a shard of leftover space, which names nothing to
     *                             look up and so is never decivilised. The null id never reaches
     *                             the set, which may be immutable and null-hostile.
     * @return the category the ground draws in
     */
    public static MapCategory resolveCategoryOf(
            Set<String> decivilisedSystemIds,
            String systemId) {

        return systemId != null && decivilisedSystemIds.contains(systemId)
                ? MapCategory.DECIVILISED
                : MapCategory.UNINHABITED;
    }

    /**
     * How far factionless ground recedes this pass.
     *
     * <p>Decivilised ground is part of the "rest of the sector" a spotlight recedes: a dead colony
     * is a political feature drawn in a fill of its own, so leaving it at full strength lets it
     * out-read the bloc the spotlight is meant to isolate. Uninhabited ground is the empty backdrop
     * the whole map is drawn over rather than anything the spotlight competes with, and its faint
     * outline is what gives the sector its shape, so it never recedes.
     *
     * @param category   the category the ground draws in
     * @param passRecede the recede every non-spotlighted bloc takes this pass, which is
     *                   {@link BlocStyleAdjustment#NONE} off filter - so an unfiltered map draws
     *                   its dead worlds untouched
     * @return the adjustment this ground draws under
     */
    public static BlocStyleAdjustment resolveRecedeOf(
            MapCategory category,
            BlocStyleAdjustment passRecede) {

        return category == MapCategory.DECIVILISED
                ? passRecede
                : BlocStyleAdjustment.NONE;
    }
}
