package kmu.maplayers.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that the union built from the cells' knobs stands at the cells' reach and at nothing
 * else the knobs hold.
 *
 * <p>Five knobs, four of them lengths: a factory reading the wrong one compiles cleanly and
 * hands back discs at the channel's depth or the weld's, which is a sector traced at somebody
 * else's setting with nothing to say so.
 */
class DiscUnionTest {

    private static final List<double[]> SITES = List.of(
        new double[] {0, 0},
        new double[] {700, 0});

    // Distinct values throughout, so a factory reading the wrong knob is caught by the value it
    // read rather than passing because two happened to match.
    private static final SectorGeometryParameters PARAMETERS =
        new SectorGeometryParameters(4000, 48, 150, 100, 4);

    @Nested
    class BuildAtCellReach {

        @Test
        void theUnionStandsAtTheCellsReach() {

            assertThat(DiscUnion.buildAtCellReach(SITES, PARAMETERS).reach())
                .isEqualTo(4000);
        }

        @Test
        void theUnionHoldsTheSitesItWasGiven() {

            assertThat(DiscUnion.buildAtCellReach(SITES, PARAMETERS).sites())
                .isSameAs(SITES);
        }
    }
}
