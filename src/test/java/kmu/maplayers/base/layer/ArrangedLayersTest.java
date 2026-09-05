package kmu.maplayers.base.layer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins how a stored arrangement lands over a live roster, which is the whole of what makes the
 * store safe to write once and read for years: it is a preference over what is registered rather
 * than a roster of its own, so an id nothing registers costs the player nothing and a layer the
 * store never heard of still gets a tab.
 *
 * <p>And the guard that makes hiding survivable: a hand-edited store that hides everything leaves
 * the leading tab standing, because the dialog that would put a tab back is reached from the bar.
 */
final class ArrangedLayersTest {

    private final MapLayer alphaLayerMock = mock(MapLayer.class);
    private final MapLayer betaLayerMock = mock(MapLayer.class);
    private final MapLayer gammaLayerMock = mock(MapLayer.class);

    // The roster as three mods registered it, left to right. Named once because every case below is
    // about what an arrangement does to this order rather than about the order itself.
    private List<MapLayer> rosterLayers;

    @BeforeEach
    void registerThreeLayersInLoadOrder() {

        when(alphaLayerMock.getId())
            .thenReturn("alpha");
        when(betaLayerMock.getId())
            .thenReturn("beta");
        when(gammaLayerMock.getId())
            .thenReturn("gamma");

        rosterLayers = List.of(alphaLayerMock, betaLayerMock, gammaLayerMock);
    }

    @Nested
    class ArrangeVisibleLayers {

        @Test
        void arrangeVisibleLayersFollowsRegistrationOrderWithNothingArranged() {
            // The install every player starts on: no stored file, so the row is what load order
            // built - the layers a mod depends on to the left of its own.
            assertThat(ArrangedLayers.arrangeVisibleLayers(
                    MapLayerArrangement.UNARRANGED,
                    rosterLayers))
                .containsExactly(alphaLayerMock, betaLayerMock, gammaLayerMock);
        }

        @Test
        void arrangeVisibleLayersPutsTheRowInTheStoredOrder() {

            var arrangement = new MapLayerArrangement(
                List.of("gamma", "beta", "alpha"),
                List.of());

            assertThat(ArrangedLayers.arrangeVisibleLayers(arrangement, rosterLayers))
                .containsExactly(gammaLayerMock, betaLayerMock, alphaLayerMock);
        }

        @Test
        void arrangeVisibleLayersSkipsAStoredIdNothingRegisters() {
            // A mod uninstalled since the arrangement was made, or one that renamed its layer.
            // Skipped rather than answered for, which is what lets the store outlive the mods it
            // names without a migration.
            var arrangement = new MapLayerArrangement(
                List.of("removed_long_ago", "gamma"),
                List.of());

            assertThat(ArrangedLayers.arrangeVisibleLayers(arrangement, rosterLayers))
                .containsExactly(gammaLayerMock, alphaLayerMock, betaLayerMock);
        }

        @Test
        void arrangeVisibleLayersAppendsARegisteredIdTheStoreDoesNotName() {
            // A mod installed after the arrangement was stored: its layer lands where registration
            // order would have put it, and the player moves it from there if they want to.
            var arrangement = new MapLayerArrangement(
                List.of("gamma"),
                List.of());

            assertThat(ArrangedLayers.arrangeVisibleLayers(arrangement, rosterLayers))
                .containsExactly(gammaLayerMock, alphaLayerMock, betaLayerMock);
        }

        @Test
        void arrangeVisibleLayersPlacesADuplicatedStoredIdOnce() {
            // Only a hand-edit reaches this, and the row it would otherwise build carries one layer
            // under two tabs - both writing and reading the same stored pick.
            var arrangement = new MapLayerArrangement(
                List.of("beta", "beta"),
                List.of());

            assertThat(ArrangedLayers.arrangeVisibleLayers(arrangement, rosterLayers))
                .containsExactly(betaLayerMock, alphaLayerMock, gammaLayerMock);
        }

        @Test
        void arrangeVisibleLayersDropsAHiddenLayerFromTheRow() {

            var arrangement = new MapLayerArrangement(
                List.of(),
                List.of("beta"));

            assertThat(ArrangedLayers.arrangeVisibleLayers(arrangement, rosterLayers))
                .containsExactly(alphaLayerMock, gammaLayerMock);
        }

        @Test
        void arrangeVisibleLayersKeepsTheLeadingLayerWhereTheArrangementHidesEverything() {
            // A bar with no tabs has no way back to itself. The layer left standing is the leading
            // one of the player's own order rather than of the roster's, so the fallback lands where
            // they would look for it.
            var arrangement = new MapLayerArrangement(
                List.of("gamma"),
                List.of("alpha", "beta", "gamma"));

            assertThat(ArrangedLayers.arrangeVisibleLayers(arrangement, rosterLayers))
                .containsExactly(gammaLayerMock);
        }

        @Test
        void arrangeVisibleLayersAnswersAnEmptyRowBeforeAnythingIsRegistered() {
            // The state a process that has registered nothing is in. The guard above must not
            // invent a tab for it.
            assertThat(ArrangedLayers.arrangeVisibleLayers(
                    MapLayerArrangement.UNARRANGED,
                    List.of()))
                .isEmpty();
        }
    }
}
