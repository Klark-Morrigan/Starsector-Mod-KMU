package kmu.maplayers.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static kmu.maplayers.base.geometry.SectorPipeline.traceContinentCoast;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for where a continent coast lands on the cells it passes, over real
 * sectors.
 *
 * <p>What a landing can be wrong in two ways, and the two pull against each other. A cell can be
 * reduced to a single point, which leaves nothing for a wall to anchor on; or a run can be moved
 * to avoid that and end up cutting through a cell, which is the one thing the placement exists to
 * prevent. So both are asked here, of the same traces.
 *
 * <p>The bounds are measured figures rather than derived ones, and they are ratchets: a change
 * that opens more frontage or cuts fewer cells passes, and one that gives either back fails. That
 * is the only useful shape for a claim about a whole sector, where the honest expectation is a
 * number nobody can work out from first principles.
 */
class CoastLandingsIntegrationTest {

    private static final String SECTORS = SectorPipeline.SECTORS;

    // The most of a sector's eligible stretches that may be a bare point rather than a run.
    //
    // A third, which both fixtures now clear and one of them did not before the crossed landings
    // were opened up. Not zero, and never can be: a cell whose two neighbours can both see its
    // middle has its coast pass through that middle, and one point is what the smoothing is FOR.
    private static final double MOST_FRONTAGE_AS_POINTS = 1.0 / 3;

    // How many runs of a sector's coast may cut into a cell, at the width the map draws it.
    //
    // Not zero, and these are not the opening's doing - the same five and four stand with the
    // pass switched off. They are here so that opening a crossed landing cannot quietly add to
    // them, which is the one way this pass could do real harm.
    private static final int MOST_RUNS_CUTTING_A_CELL = 5;

    // How wide a cell's border is drawn, which is what decides whether a crossing is one a
    // reader could see: a run less than half of this inside a border is under the line drawing
    // it. The shipped width, stated here rather than read off the drawing, which this package
    // may not reach into.
    private static final double DRAWN_CELL_BORDER = 90;

    @Nested
    class TraceContinentCoasts {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void mostBridgeableFrontageIsAStretchRatherThanABarePoint(String sector) {
            // What opening a crossed landing buys. A cell whose two landings crossed has border
            // between them that the coast could run along, and collapsing to the midpoint reports
            // one place a wall may anchor where there is a stretch of them.
            var frontages = CoastFrontages.Shore.EXTERIOR.collectFrontages(traceContinentCoast(sector));
            var points = 0;
            var runs = 0;

            for (var byCell : frontages.values()) {
                for (var run : byCell) {
                    runs++;
                    if (run.size() == 1) {
                        points++;
                    }
                }
            }

            assertThat(runs)
                .as("%s: no eligible frontage at all", sector)
                .isPositive();

            assertThat((double) points / runs)
                .as("%s: %d of %d eligible stretches are a bare point", sector, points, runs)
                .isLessThan(MOST_FRONTAGE_AS_POINTS);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void openingACrossedLandingLeavesTheCoastOutsideTheCells(String sector) {
            // The other half, and the reason the opening is not simply always done. Putting two
            // landings back in order moves BOTH runs either side of the cell, and a run that has
            // moved can cut a cell neither of its ends knows about. Where it would, the crossing
            // is left standing - so the count here has to hold whatever the opening did.
            assertThat(CoastCrossings.findVisibleCrossings(
                    traceContinentCoast(sector), DRAWN_CELL_BORDER))
                .as("%s: coast runs cutting into a cell", sector)
                .hasSizeLessThanOrEqualTo(MOST_RUNS_CUTTING_A_CELL);
        }
    }
}
