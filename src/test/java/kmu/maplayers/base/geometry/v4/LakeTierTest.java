package kmu.maplayers.base.geometry.v4;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for what the lake tier adds to carrying a line: which label each of its
 * substeps lays under.
 *
 * <p>How a line becomes walls is {@link CarriedLinesTest}'s. What is left here is the one thing
 * this tier decides, which is what the pieces its lines close are told they were closed by.
 */
class LakeTierTest {

    private static final SectorGeometryParameters PARAMETERS =
        SectorGeometryParameters.createDefaults();

    private static final List<double[]> SITES = List.of(new double[] {0, 0}, new double[] {1000, 0});

    private static final CellGap LINE =
        new CellGap(0, 1, new double[] {0, 300}, new double[] {1000, 300}, 1000);

    @Nested
    class LayCoastWalls {

        @Test
        void everyWallCarriesTheLakeCoastLabel() {

            var laid = LakeTier.layCoastWalls(List.of(LINE), SITES, PARAMETERS);

            assertThat(laid.walls())
                .isNotEmpty()
                .extracting(LabelledWall::label)
                .containsOnly(LakeTier.THE_LAKE_COAST);
        }
    }

    @Nested
    class LayBridgeWalls {

        @Test
        void everyWallCarriesTheLakeBridgesLabel() {

            var laid = LakeTier.layBridgeWalls(List.of(LINE), SITES, PARAMETERS);

            assertThat(laid.walls())
                .isNotEmpty()
                .extracting(LabelledWall::label)
                .containsOnly(LakeTier.THE_LAKE_BRIDGES);
        }

        @Test
        void theBridgesLabelIsNeitherTheCoastsNorTheFrames() {
            // Two lines under one number would name every piece either closed after whichever
            // was asked about.
            assertThat(LakeTier.THE_LAKE_BRIDGES)
                .isNotEqualTo(LakeTier.THE_LAKE_COAST)
                .isNotEqualTo(VoidPartition.THE_FRAME)
                .isNegative();
        }
    }
}
