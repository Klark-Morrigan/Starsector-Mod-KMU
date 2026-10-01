package kmu.maplayers.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for what a piece of void is called.
 *
 * <p>The key is written out whole in each case, because what is pinned is its exact shape: a
 * key travels into maps and reports, and a change in a joiner or in the order of the parts
 * renames every piece on the map without anything else failing.
 */
class VoidKeysTests {

    private static final String PREFIX = "void_test";

    // Two cells on the x axis, 1000 apart, and a third far off so the first two are the
    // closest pair. Named so that the order a pair is read in is visibly the raw IDs' and not
    // the reduced parts': "Beta Star" sorts before "alpha", capitals first, so the line runs
    // from Beta Star at x 1000 to alpha at the origin - west.
    private static final List<double[]> SITES = List.of(
        new double[] {1000, 0},
        new double[] {0, 0},
        new double[] {0, 5000});

    private static final List<String> SYSTEM_IDS = List.of("Beta Star", "alpha", "gamma");

    // A triangle above that line, which is its right walking west, and the same below it.
    private static final List<double[]> ABOVE = List.of(
        new double[] {400, 100},
        new double[] {600, 100},
        new double[] {500, 300});

    private static final List<double[]> BELOW = List.of(
        new double[] {400, -100},
        new double[] {500, -300},
        new double[] {600, -100});

    @Nested
    class BuildKey {

        @Test
        void thePartsAreTheCellsReducedAndSortedThenTheSide() {
            // alpha sorts before beta_star once reduced, whatever order the cells arrive in.
            assertThat(VoidKeys.buildKey(PREFIX, List.of(0, 1), ABOVE, SITES, SYSTEM_IDS))
                .isEqualTo("void_test--alpha--beta_star--r");
        }

        @Test
        void thePieceOnTheOtherSideOfTheSameCellsIsNamedApart() {
            // Two pieces on one pair of cells, one either side of the line between them.
            assertThat(VoidKeys.buildKey(PREFIX, List.of(1, 0), BELOW, SITES, SYSTEM_IDS))
                .isEqualTo("void_test--alpha--beta_star--l");
        }

        @Test
        void theSideIsReadAcrossTheClosestPairOnly() {
            // gamma is far off, so the line is still Beta Star to alpha.
            assertThat(VoidKeys.buildKey(PREFIX, List.of(2, 0, 1), ABOVE, SITES, SYSTEM_IDS))
                .isEqualTo("void_test--alpha--beta_star--gamma--r");
        }

        @Test
        void aPieceOnOneCellIsMarkedAsHavingNoSide() {

            assertThat(VoidKeys.buildKey(PREFIX, List.of(2), ABOVE, SITES, SYSTEM_IDS))
                .isEqualTo("void_test--gamma--x");
        }
    }

    @Nested
    class ReduceToKeyCharacters {

        @Test
        void aDisplayNameComesOutShapedLikeAnId() {

            assertThat(VoidKeys.reduceToKeyCharacters("New  Maxios-7"))
                .isEqualTo("new_maxios_7");
        }

        @Test
        void anIdIsLeftAsItIs() {

            assertThat(VoidKeys.reduceToKeyCharacters("system_a1b2"))
                .isEqualTo("system_a1b2");
        }
    }
}
