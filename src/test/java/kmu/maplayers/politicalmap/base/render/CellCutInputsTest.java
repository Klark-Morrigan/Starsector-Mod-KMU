package kmu.maplayers.politicalmap.base.render;

import kmlib.starsector.colonies.ColonyVisibility;

import kmu.maplayers.base.geometry.CellSeedInputs;
import kmu.maplayers.base.visibility.MapVisibilityOverrides;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what counts as a different cut of the cells. Everything this value is for rests on its
 * equality: a reading that compares equal to the one the cells were last cut from is a reading
 * the map is entitled to skip the recut for - and, downstream, to keep reusing work fitted
 * inside those cell shapes.
 *
 * <p>The two settings halves are the cases worth guarding hardest, because the reachable-set
 * revision does not move with them. A change to either recuts every cell while that signal stands
 * still, so a staleness test taken on the signal alone reads them as no change at all - the map
 * keeps cells it has recut and offers label boxes clipped to shapes that are gone.
 */
final class CellCutInputsTest {

    // The reading two cuts share where the case is about one of the other inputs.
    // The "show all factions" reveal on: the fog lifted outright, and no gate held, which
    // is the widest rule any surface reads under.
    private static final ColonyVisibility UNDER_THE_REVEAL =
        new ColonyVisibility(true, Set.of());

    private static final int GEOMETRY_REVISION = 7;
    private static final CellSeedInputs SEED_INPUTS = new CellSeedInputs(48, 4000.0);
    private static final MapVisibilityOverrides DEV_TOGGLES =
        new MapVisibilityOverrides(ColonyVisibility.BASE_FOG, false);

    @Nested
    class Equality {

        @Test
        void equalityHoldsForTwoReadingsOfAnUnmovedMap() {
            // The per-frame case: nothing the cells are cut from has moved, so the cells in hand
            // are still the cells these inputs describe and no recut is owed.
            assertThat(new CellCutInputs(GEOMETRY_REVISION, SEED_INPUTS, DEV_TOGGLES))
                .isEqualTo(new CellCutInputs(GEOMETRY_REVISION, SEED_INPUTS, DEV_TOGGLES));
        }

        @Test
        void equalitySeparatesReadingsAcrossAReachableSetChange() {
            // A system gained or lost access, or one started or stopped moving: the framework
            // raises its signal and the partition is cut again around the changed site.
            assertThat(new CellCutInputs(GEOMETRY_REVISION, SEED_INPUTS, DEV_TOGGLES))
                .isNotEqualTo(new CellCutInputs(GEOMETRY_REVISION + 1, SEED_INPUTS, DEV_TOGGLES));
        }

        @Test
        void equalitySeparatesReadingsAcrossACellReachChange() {
            // Dragging the cell radius reseeds every cell at an unmoved reachable-set revision.
            // Read on that revision alone this is invisible, which is the whole reason the reach
            // travels in this value rather than being compared beside it.
            assertThat(new CellCutInputs(GEOMETRY_REVISION, SEED_INPUTS, DEV_TOGGLES))
                .isNotEqualTo(new CellCutInputs(
                    GEOMETRY_REVISION,
                    new CellSeedInputs(48, 5000.0),
                    DEV_TOGGLES));
        }

        @Test
        void equalitySeparatesReadingsAcrossAFrontierResolutionChange() {
            // The other seed input, at the same unmoved revision: how many segments bound a cell
            // changes every cell's outline, so a box clipped inside the old one no longer fits.
            assertThat(new CellCutInputs(GEOMETRY_REVISION, SEED_INPUTS, DEV_TOGGLES))
                .isNotEqualTo(new CellCutInputs(
                    GEOMETRY_REVISION,
                    new CellSeedInputs(64, 4000.0),
                    DEV_TOGGLES));
        }

        @Test
        void equalitySeparatesReadingsAcrossAHiddenSystemsFlip() {
            // The toggle admits every star system to the partition, so cells appear where there
            // were none and every neighbour cedes area to them - at an unmoved revision again.
            assertThat(new CellCutInputs(GEOMETRY_REVISION, SEED_INPUTS, DEV_TOGGLES))
                .isNotEqualTo(new CellCutInputs(
                    GEOMETRY_REVISION,
                    SEED_INPUTS,
                    new MapVisibilityOverrides(ColonyVisibility.BASE_FOG, true)));
        }

        @Test
        void equalitySeparatesReadingsAcrossAnUndiscoveredMarketsFlip() {
            // The second reveal reaches the cells through inhabitation: an undiscovered colony
            // counted makes its system drawn, which seeds a cell that was not there before.
            assertThat(new CellCutInputs(GEOMETRY_REVISION, SEED_INPUTS, DEV_TOGGLES))
                .isNotEqualTo(new CellCutInputs(
                    GEOMETRY_REVISION,
                    SEED_INPUTS,
                    new MapVisibilityOverrides(UNDER_THE_REVEAL, false)));
        }
    }
}
