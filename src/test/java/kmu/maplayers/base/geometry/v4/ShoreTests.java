package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Segment;

import kmu.maplayers.base.geometry.SectorGeometryParameters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Unit coverage for the shore every tier lands its lines on, and its frontier read per cell. */
class ShoreTests {

    // Three sides round the circle, so the sagitta is half the radius: 100 * (1 - cos 60) = 50.
    private static final SectorGeometryParameters PARAMETERS =
        new SectorGeometryParameters(100, 3, 10, 1, 8);

    private static final List<double[]> SITES = List.of(new double[] {0, 0}, new double[] {500, 0});

    private static final LabelledWall FIRST_OF_CELL_ZERO =
        new LabelledWall(new Segment(0, 100, 50, 90), 0);

    private static final LabelledWall ONLY_OF_CELL_ONE =
        new LabelledWall(new Segment(500, 100, 550, 90), 1);

    private static final LabelledWall SECOND_OF_CELL_ZERO =
        new LabelledWall(new Segment(50, 90, 90, 50), 0);

    private static final List<LabelledWall> FRONTIER =
        List.of(FIRST_OF_CELL_ZERO, ONLY_OF_CELL_ONE, SECOND_OF_CELL_ZERO);

    private static final int CELL_FACING_NO_VOID = 7;

    @Nested
    class ListFrontierOf {

        @Test
        void aCellHoldsItsOwnEdgesInFrontierOrder() {

            var shore = Shore.indexShore(FRONTIER, SITES, PARAMETERS);

            assertThat(shore.listFrontierOf(0))
                .containsExactly(FIRST_OF_CELL_ZERO, SECOND_OF_CELL_ZERO);
            assertThat(shore.listFrontierOf(1))
                .containsExactly(ONLY_OF_CELL_ONE);
        }

        @Test
        void aCellFacingNoVoidHoldsNothing() {

            var shore = Shore.indexShore(FRONTIER, SITES, PARAMETERS);

            assertThat(shore.listFrontierOf(CELL_FACING_NO_VOID))
                .isEmpty();
        }
    }

    @Nested
    class GetFrontier {

        @Test
        void theWholeFrontierComesBackInTheOrderItWasRead() {

            var shore = Shore.indexShore(FRONTIER, SITES, PARAMETERS);

            assertThat(shore.getFrontier())
                .containsExactly(FIRST_OF_CELL_ZERO, ONLY_OF_CELL_ONE, SECOND_OF_CELL_ZERO);
        }
    }

    @Nested
    class GetSagitta {

        @Test
        void theSagittaIsTheCellsBounds() {

            var shore = Shore.indexShore(FRONTIER, SITES, PARAMETERS);

            assertThat(shore.getSagitta())
                .isCloseTo(50, within(1e-9));
        }
    }
}
