package kmu.maplayers.base.geometry.v4;

import kmu.maplayers.base.geometry.SectorGeometryParameters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit coverage for how a reach becomes walls: one line, and a stub off each end towards its
 * own cell.
 *
 * <p>The stubs are the subject. The reach itself is copied to the unit, so what can go wrong
 * is which way a stub points and how far it runs - both of which decide whether the walk finds
 * a junction at the shore or a wall floating beside it.
 */
class LakeCoastTest {

    // Cells drawn with three sides round the circle, so the sagitta is exactly half the cell
    // radius: 100 * (1 - cos 60 degrees) = 50. A stub is two of those, which is 100.
    private static final SectorGeometryParameters PARAMETERS =
        new SectorGeometryParameters(100, 3, 10, 1, 8);

    private static final double[] LEFT_SITE = {0, 0};

    private static final double[] RIGHT_SITE = {1000, 0};

    private static final List<double[]> SITES = List.of(LEFT_SITE, RIGHT_SITE);

    // A reach 300 above the sites, running from directly above the left one to directly above
    // the right one, so each stub runs straight down and its far end is a round number.
    private static final ReachLine REACH =
        new ReachLine(new double[] {0, 300}, new double[] {1000, 300}, 0, 1);

    private static final double SAME_POINT = 1e-9;

    @Nested
    class LayCoastWalls {

        @Test
        void aReachIsLaidAsItselfBetweenTwoStubs() {

            var laid = LakeCoast.layCoastWalls(List.of(REACH), SITES, PARAMETERS);

            assertThat(laid.walls())
                .hasSize(3);

            assertThat(laid.walls().get(1).segment().readStart())
                .containsExactly(0, 300);
            assertThat(laid.walls().get(1).segment().readEnd())
                .containsExactly(1000, 300);
        }

        @Test
        void eachStubRunsTwoSagittasFromTheEndTowardsItsOwnSite() {
            // From (0, 300) towards (0, 0) by 100 is (0, 200); from (1000, 300) towards
            // (1000, 0) by 100 is (1000, 200). A stub pointing at the wrong site would run
            // sideways, and one of the wrong length would stop short of the shore or reach
            // past a neighbour.
            var laid = LakeCoast.layCoastWalls(List.of(REACH), SITES, PARAMETERS);

            assertThat(laid.walls().get(0).segment().readStart())
                .satisfies(point -> {

                    assertThat(point[0])
                        .isCloseTo(0, within(SAME_POINT));
                    assertThat(point[1])
                        .isCloseTo(200, within(SAME_POINT));
                });
            assertThat(laid.walls().get(2).segment().readEnd())
                .satisfies(point -> {

                    assertThat(point[0])
                        .isCloseTo(1000, within(SAME_POINT));
                    assertThat(point[1])
                        .isCloseTo(200, within(SAME_POINT));
                });
        }

        @Test
        void theStubsShareTheirEndsWithTheReachExactly() {
            // Shared to the bit, not to a tolerance: the walk joins what is laid at rounding,
            // and a stub ending a whisker off the reach's end is a stub joined to nothing.
            var laid = LakeCoast.layCoastWalls(List.of(REACH), SITES, PARAMETERS);

            assertThat(laid.walls().get(0).segment().readEnd())
                .containsExactly(0, 300);
            assertThat(laid.walls().get(2).segment().readStart())
                .containsExactly(1000, 300);
        }

        @Test
        void everyWallCarriesTheLakeCoastLabel() {

            var laid = LakeCoast.layCoastWalls(List.of(REACH), SITES, PARAMETERS);

            assertThat(laid.walls())
                .extracting(LabelledWall::label)
                .containsOnly(LakeCoast.THE_LAKE_COAST);
        }

        @Test
        void onlyTheReachIsDrawn() {

            var laid = LakeCoast.layCoastWalls(List.of(REACH), SITES, PARAMETERS);

            assertThat(laid.reachLines())
                .hasSize(1);

            assertThat(laid.reachLines().get(0).get(0))
                .containsExactly(0, 300);
            assertThat(laid.reachLines().get(0).get(1))
                .containsExactly(1000, 300);
        }

        @Test
        void noReachesLayNothing() {

            var laid = LakeCoast.layCoastWalls(List.of(), SITES, PARAMETERS);

            assertThat(laid.walls())
                .isEmpty();
            assertThat(laid.reachLines())
                .isEmpty();
        }
    }
}
