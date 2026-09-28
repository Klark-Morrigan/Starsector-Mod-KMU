package kmu.maplayers.base.geometry.v4;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for what the lake coast adds to carrying a line: its own label.
 *
 * <p>How a reach becomes walls is {@link CarriedLinesTest}'s. What is left here is the one thing
 * this tier decides, which is what the pieces its reaches close are told they were closed by.
 */
class LakeCoastTest {

    private static final SectorGeometryParameters PARAMETERS =
        SectorGeometryParameters.createDefaults();

    private static final List<double[]> SITES = List.of(new double[] {0, 0}, new double[] {1000, 0});

    private static final CellGap REACH =
        new CellGap(0, 1, new double[] {0, 300}, new double[] {1000, 300}, 1000);

    @Nested
    class LayCoastWalls {

        @Test
        void everyWallCarriesTheLakeCoastLabel() {

            var laid = LakeCoast.layCoastWalls(List.of(REACH), SITES, PARAMETERS);

            assertThat(laid.walls())
                .isNotEmpty()
                .extracting(LabelledWall::label)
                .containsOnly(LakeCoast.THE_LAKE_COAST);
        }
    }
}
