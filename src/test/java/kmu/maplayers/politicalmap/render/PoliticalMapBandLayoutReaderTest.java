package kmu.maplayers.politicalmap.render;

import kmu.maplayers.base.render.MapOverlayBand;
import kmu.settings.KmuPoliticalMapDrawOrderSettings;
import kmu.settings.NebulaDrawOrderChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins that each of the player's four choices lands in the sub-layer it was picked for.
 *
 * <p>It fails silently in play: four settings of one type are interchangeable by the compiler, so a
 * swapped pair reads as an overlay stacked oddly rather than as a fault.
 */
final class PoliticalMapBandLayoutReaderTest {

    private static final MapOverlayBand BENEATH = MapOverlayBand.BENEATH_STARSCAPE_NEBULAE;
    private static final MapOverlayBand ABOVE = MapOverlayBand.ABOVE_STARSCAPE_NEBULAE;

    @Nested
    class ReadChosenLayout {

        @Test
        void readChosenLayoutPlacesEachSubLayerOnTheSideItsOwnSettingPicked() {
            // Each of the four asked for a different side from its neighbours where it can be, so a
            // slot reading another's setting shows as a band rather than passing on a shared answer.
            // The borders go up with the fills because that pairing is the one the layout resolves;
            // the layout's own suite holds it to the rule.
            try (var settingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class)) {

                NebulaDrawOrderFixtures.stubChosenDrawOrders(
                    settingsMock,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.BELOW,
                    NebulaDrawOrderChoice.ABOVE);

                var layout = PoliticalMapBandLayoutReader.readChosenLayout();

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
            // The shipped split: the cell geometry fogged, the two readouts laid over cells clear of
            // the fog. A player who never opens the group sees this, and this is where it is settled.
            try (var settingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class)) {

                NebulaDrawOrderFixtures.stubChosenDrawOrders(
                    settingsMock,
                    NebulaDrawOrderChoice.BELOW,
                    NebulaDrawOrderChoice.BELOW,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE);

                var layout = PoliticalMapBandLayoutReader.readChosenLayout();

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
}
