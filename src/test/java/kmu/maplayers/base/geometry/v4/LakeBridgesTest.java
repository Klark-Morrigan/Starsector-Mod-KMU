package kmu.maplayers.base.geometry.v4;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for what the lake bridges add to carrying a line: their own label.
 *
 * <p>How a bridge becomes walls is {@link CarriedLinesTest}'s. What is left here is the one thing
 * this tier decides, which is that a piece a bridge closes is not told the coast closed it.
 */
class LakeBridgesTest {

    private static final SectorGeometryParameters PARAMETERS =
        SectorGeometryParameters.createDefaults();

    private static final List<double[]> SITES = List.of(new double[] {0, 0}, new double[] {1000, 0});

    private static final CellGap BRIDGE =
        new CellGap(0, 1, new double[] {0, 300}, new double[] {1000, 300}, 1000);

    @Nested
    class LayBridgeWalls {

        @Test
        void everyWallCarriesTheLakeBridgesLabel() {

            var laid = LakeBridges.layBridgeWalls(List.of(BRIDGE), SITES, PARAMETERS);

            assertThat(laid.walls())
                .isNotEmpty()
                .extracting(LabelledWall::label)
                .containsOnly(LakeBridges.THE_LAKE_BRIDGES);
        }

        @Test
        void theLabelIsNotTheCoasts() {
            // Two tiers under one number would name every piece either closed after whichever
            // was asked about.
            assertThat(LakeBridges.THE_LAKE_BRIDGES)
                .isNotEqualTo(LakeCoast.THE_LAKE_COAST)
                .isNotEqualTo(VoidPartition.THE_FRAME)
                .isNegative();
        }
    }
}
