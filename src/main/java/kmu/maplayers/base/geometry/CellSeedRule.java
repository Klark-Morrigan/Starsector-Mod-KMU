package kmu.maplayers.base.geometry;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.base.visibility.systems.MapVisibilityPass;

/**
 * Which star systems seed a cell: a layer's statement of what its partition is cut around,
 * answered over the pass a cut is taken from.
 *
 * <p>The substrate's own answer is {@link #SEED_DRAWN_SYSTEMS} - a system seeds a cell exactly
 * where the map draws it - and it is what a layer with no rule of its own names. It is named
 * rather than defaulted into the cut, so a layer seeding a different set of systems says so where
 * it composes itself instead of inheriting another layer's set without a line saying which.
 *
 * <p>A rule reads only the pass and the system, so it is a pure function of the cut's inputs:
 * whatever recuts the cells - the reachable set moving, the visibility rules changing - is what
 * moves a rule's answer, and a rule reading anything live beside them would answer differently
 * between two cuts nothing told apart.
 */
@FunctionalInterface
public interface CellSeedRule {

    /**
     * The substrate's rule: a system seeds a cell where the pass's drawn-set rule draws it -
     * reachable or forced onto the map, and inhabited where reachability alone does not admit it.
     */
    CellSeedRule SEED_DRAWN_SYSTEMS = (pass, system) -> pass.isDrawn(system);

    /**
     * Whether one system seeds a cell under this rule.
     *
     * @param pass   the cut's reading of the sector, whose drawn-set answer the substrate's rule
     *               takes and any other rule may read beside its own
     * @param system the system to test
     * @return true when the system seeds a cell
     */
    boolean isSeededBy(MapVisibilityPass pass, StarSystemAPI system);
}
