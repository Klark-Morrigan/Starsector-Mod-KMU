package kmu.maplayers.ownermap.render;

import kmu.maplayers.base.render.MapOverlayBand;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one combination of bands the map cannot draw being resolved by the layout itself rather
 * than left for an emitting pass to notice: fills lifted over their own borders leave a blank cell,
 * which reads as a cell nobody holds.
 */
final class OwnerMapBandLayoutTest {

    private static final MapOverlayBand BENEATH = MapOverlayBand.BENEATH_STARSCAPE_NEBULAE;
    private static final MapOverlayBand ABOVE = MapOverlayBand.ABOVE_STARSCAPE_NEBULAE;

    /**
     * The rule the settings screen cannot state and the map cannot survive being without: a border
     * may be lifted clear of its fogged fill, but never sunk beneath the fill it draws around.
     * Held against a directly built layout rather than against settings, since the point is that
     * there is no way to hold an incoherent one at all.
     */
    @Nested
    class BorderLift {

        @Test
        void borderLiftRaisesTheBordersWithTheFillsTheyWereLeftBeneath() {
            var layout = new OwnerMapBandLayout(ABOVE, BENEATH, ABOVE, ABOVE);

            assertThat(layout.borderBand())
                .isEqualTo(ABOVE);
        }

        @Test
        void borderLiftLeavesBordersRaisedAloneWhereTheirFillsStayedBeneath() {
            // The asymmetry is the picture's: a border drawn over its own fill is still a border,
            // so this half of the pair is offered and must survive the resolution untouched.
            var layout = new OwnerMapBandLayout(BENEATH, ABOVE, ABOVE, ABOVE);

            assertThat(layout.fillBand())
                .isEqualTo(BENEATH);
            assertThat(layout.borderBand())
                .isEqualTo(ABOVE);
        }

        @Test
        void borderLiftLeavesTheOtherSubLayersWhereTheyWerePlaced() {
            // The resolution touches one field. A layout that lifted its neighbours with the
            // borders would move readouts the player never asked to move.
            var layout = new OwnerMapBandLayout(ABOVE, BENEATH, BENEATH, BENEATH);

            assertThat(layout.ribbonBand())
                .isEqualTo(BENEATH);
            assertThat(layout.labelBand())
                .isEqualTo(BENEATH);
        }
    }
}
