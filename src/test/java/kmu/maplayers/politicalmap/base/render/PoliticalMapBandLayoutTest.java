package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.base.render.MapOverlayBand;
import kmu.settings.KmuPoliticalMapSettings;
import kmu.settings.NebulaDrawOrderChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the two things standing between the player's four choices and where the overlay paints: that
 * each choice lands in the sub-layer it was picked for, and that the one combination the map cannot
 * draw is resolved here rather than left for an emitting pass to notice.
 *
 * <p>Both fail silently in play. Four settings of one type are interchangeable by the compiler, so
 * a swapped pair reads as an overlay stacked oddly rather than as a fault; and fills lifted over
 * their own borders leave a blank cell, which looks like territory nobody holds.
 */
final class PoliticalMapBandLayoutTest {

    private static final MapOverlayBand BENEATH = MapOverlayBand.BENEATH_STARSCAPE_NEBULAE;
    private static final MapOverlayBand ABOVE = MapOverlayBand.ABOVE_STARSCAPE_NEBULAE;

    @Nested
    class ReadChosenLayout {

        @Test
        void readChosenLayoutPlacesEachSubLayerOnTheSideItsOwnSettingPicked() {
            // Each of the four asked for a different side from its neighbours where it can be, so a
            // slot reading another's setting shows as a band rather than passing on a shared answer.
            // The borders go up with the fills because that pairing is the one the layout resolves;
            // it is the group below that holds it to the rule.
            try (var settingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubChosenDrawOrders(
                    settingsMock,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.BELOW,
                    NebulaDrawOrderChoice.ABOVE);

                var layout = PoliticalMapBandLayout.readChosenLayout();

                assertThat(layout.fillBand())
                    .isEqualTo(ABOVE);
                assertThat(layout.borderBand())
                    .isEqualTo(ABOVE);
                assertThat(layout.ribbonBand())
                    .isEqualTo(BENEATH);
                assertThat(layout.labelBand())
                    .isEqualTo(ABOVE);
            }
        }

        @Test
        void readChosenLayoutReproducesTheShippedSplitWhereEveryChoiceIsTheDefault() {
            // The picture the overlay painted before the draw order became a choice: the cell geometry
            // fogged, the two readouts laid over cells clear of the fog. A player who never opens
            // the group is meant to see no change at all, and this is where that is settled.
            try (var settingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubChosenDrawOrders(
                    settingsMock,
                    NebulaDrawOrderChoice.BELOW,
                    NebulaDrawOrderChoice.BELOW,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE);

                var layout = PoliticalMapBandLayout.readChosenLayout();

                assertThat(layout.fillBand())
                    .isEqualTo(BENEATH);
                assertThat(layout.borderBand())
                    .isEqualTo(BENEATH);
                assertThat(layout.ribbonBand())
                    .isEqualTo(ABOVE);
                assertThat(layout.labelBand())
                    .isEqualTo(ABOVE);
            }
        }
    }

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
            var layout = new PoliticalMapBandLayout(ABOVE, BENEATH, ABOVE, ABOVE);

            assertThat(layout.borderBand())
                .isEqualTo(ABOVE);
        }

        @Test
        void borderLiftLeavesBordersRaisedAloneWhereTheirFillsStayedBeneath() {
            // The asymmetry is the picture's: a border drawn over its own fill is still a border,
            // so this half of the pair is offered and must survive the resolution untouched.
            var layout = new PoliticalMapBandLayout(BENEATH, ABOVE, ABOVE, ABOVE);

            assertThat(layout.fillBand())
                .isEqualTo(BENEATH);
            assertThat(layout.borderBand())
                .isEqualTo(ABOVE);
        }

        @Test
        void borderLiftLeavesTheOtherSubLayersWhereTheyWerePlaced() {
            // The resolution touches one field. A layout that lifted its neighbours with the
            // borders would move readouts the player never asked to move.
            var layout = new PoliticalMapBandLayout(ABOVE, BENEATH, BENEATH, BENEATH);

            assertThat(layout.ribbonBand())
                .isEqualTo(BENEATH);
            assertThat(layout.labelBand())
                .isEqualTo(BENEATH);
        }
    }

    // The four reads a layout is resolved from, in the order the record states them.
    private static void stubChosenDrawOrders(
            MockedStatic<KmuPoliticalMapSettings> settingsMock,
            NebulaDrawOrderChoice fillDrawOrder,
            NebulaDrawOrderChoice borderDrawOrder,
            NebulaDrawOrderChoice ribbonDrawOrder,
            NebulaDrawOrderChoice labelDrawOrder) {

        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapFillNebulaDrawOrder)
            .thenReturn(fillDrawOrder);
        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapBorderNebulaDrawOrder)
            .thenReturn(borderDrawOrder);
        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapRibbonNebulaDrawOrder)
            .thenReturn(ribbonDrawOrder);
        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapLabelNebulaDrawOrder)
            .thenReturn(labelDrawOrder);
    }
}
