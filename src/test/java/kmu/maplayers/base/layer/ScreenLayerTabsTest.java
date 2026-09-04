package kmu.maplayers.base.layer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the one rule the empty view's tab now answers to: it is withheld from a screen that has grown a
 * control of its own on the game's chrome, and offered on every screen that has not - a broken reach, a
 * closed hatch and a row with no room all being the same absence, and all of them the state in which it
 * is the player's only way to empty the map.
 *
 * <p>Pins what must not move with it. The roster is untouched, since a save's pick is an id resolved
 * against it and a filtered roster would read a save left on that tab as stale and start painting; and
 * a lone tab is never withheld, an empty strip having no way back to itself.
 *
 * <p>And the move that keeps the two controls agreeing: a pick already sitting on the withheld tab is
 * put on the default layer and stored as a hide, so the map stays as blank as it was and the box says
 * so. When that is owed - once, on the first control to stand - is
 * {@link kmu.maplayers.base.chrome.MapLayerToggleUpkeepTest}'s, this answering only what the move is.
 */
final class ScreenLayerTabsTest {

    private final MapLayer paintingLayerMock = mock(MapLayer.class);

    @AfterEach
    void restoreTheRosterThisCaseReplaced() {
        // The registry is static, so a roster of stand-ins would otherwise outlive its case.
        MapLayerRosters.restoreNonEmptyRoster();
    }

    @Nested
    class ResolveTabbedLayers {

        @Test
        void resolveTabbedLayersWithholdsTheEmptyViewFromAScreenCarryingAControl() {
            // Two controls for one thought, and the box is the more discoverable of them - it stands
            // where the player already looks for "show or hide this map furniture".
            registerTheEmptyViewBesideALayerThatPaints();

            assertThat(ScreenLayerTabs.resolveTabbedLayers(createPicksWithAControlStanding()))
                .containsExactly(paintingLayerMock);
        }

        @Test
        void resolveTabbedLayersOffersTheEmptyViewOnAScreenWithNoControl() {
            // Every way of not having a box reads the same here, which is what makes the injection
            // optional rather than load-bearing: a screen it never reached keeps the tab that empties
            // the map, whatever went wrong on the row.
            registerTheEmptyViewBesideALayerThatPaints();

            assertThat(ScreenLayerTabs.resolveTabbedLayers(createPicksWithNoControl()))
                .containsExactly(NoLayer.INSTANCE, paintingLayerMock);
        }

        @Test
        void resolveTabbedLayersLeavesTheRosterWhole() {
            // Withheld from the strip and never from the roster: a save's pick is an id resolved
            // against these, so a roster without the empty view would read a save left on it as an id
            // from an older build and fall back to the layer that paints - starting an overlay over
            // the map of a player who asked for nothing.
            registerTheEmptyViewBesideALayerThatPaints();

            ScreenLayerTabs.resolveTabbedLayers(createPicksWithAControlStanding());

            assertThat(MapLayerRegistry.getLayers())
                .containsExactly(NoLayer.INSTANCE, paintingLayerMock);
        }

        @Test
        void resolveTabbedLayersKeepsTheLastTabStandingWhateverElseIsTrue() {
            // An empty strip has no way back to itself. Unreachable while a layer that paints is
            // registered beside it, which is a composition root's arrangement rather than a rule.
            MapLayerRegistry.registerLayers(List.of(NoLayer.INSTANCE), NoLayer.INSTANCE);

            assertThat(ScreenLayerTabs.resolveTabbedLayers(createPicksWithAControlStanding()))
                .containsExactly(NoLayer.INSTANCE);
        }
    }

    @Nested
    class MigratePickOffWithheldTab {

        @Test
        void migratePickOffWithheldTabMovesThePickToTheDefaultLayerAndStoresTheHide() {
            // The picture does not change - blank map, blank map - and what the player chose is now
            // expressed through the control that can reverse it, rather than through a tab this screen
            // is no longer offered.
            registerTheEmptyViewBesideALayerThatPaints();

            var screenPicks = createPicksOnTheEmptyView();

            ScreenLayerTabs.migratePickOffWithheldTab(screenPicks);

            verify(screenPicks.layerSelection())
                .selectLayer(paintingLayerMock);
            verify(screenPicks.layerVisibility().getStoredVisibility())
                .showLayers(false);
        }

        @Test
        void migratePickOffWithheldTabLeavesAPickTheStripStillOffersAlone() {
            // Which is every call but the one that is owed: a screen already on a layer that paints has
            // nothing to settle, and a hide written here would empty a map the player is looking at.
            registerTheEmptyViewBesideALayerThatPaints();

            var screenPicks = createPicksWithAControlStanding();

            when(screenPicks.layerSelection().getActiveLayer())
                .thenReturn(paintingLayerMock);

            ScreenLayerTabs.migratePickOffWithheldTab(screenPicks);

            verify(screenPicks.layerSelection(), never())
                .selectLayer(paintingLayerMock);
            verify(screenPicks.layerVisibility().getStoredVisibility(), never())
                .showLayers(false);
        }

        @Test
        void migratePickOffWithheldTabLeavesAPickOnTheLastTabStandingAlone() {
            // The guard above, read from the other end: a tab that is not withheld is not migrated off
            // either, so a strip of one leaves the player where they were rather than storing a hide
            // over the only tab there is.
            MapLayerRegistry.registerLayers(List.of(NoLayer.INSTANCE), NoLayer.INSTANCE);

            var screenPicks = createPicksOnTheEmptyView();

            ScreenLayerTabs.migratePickOffWithheldTab(screenPicks);

            verify(screenPicks.layerVisibility().getStoredVisibility(), never())
                .showLayers(false);
        }
    }

    // The roster the withholding is about: the empty view beside a layer that paints, which is the
    // shape every install ships and the only one in which anything is withheld at all.
    private void registerTheEmptyViewBesideALayerThatPaints() {
        MapLayerRegistry.registerLayers(
            List.of(NoLayer.INSTANCE, paintingLayerMock), paintingLayerMock);
    }

    // A screen whose box has stood at least once this session, which is what withholds its tab.
    private static ScreenLayerPicks createPicksWithAControlStanding() {

        var screenPicks = createPicksWithNoControl();
        screenPicks.layerVisibility().recordControlAttached();

        return screenPicks;
    }

    // The same screen before any box has stood on it - and, since the word is taken back with the
    // control, after one has been taken away again.
    private static ScreenLayerPicks createPicksWithNoControl() {
        return new ScreenLayerPicks(
            mock(ActiveLayerSelection.class),
            new ControlBackedMapLayerVisibility(mock(MapLayerVisibility.class)));
    }

    // A screen carrying a box and still set to the tab that box takes over from, which is the one
    // arrangement the move exists for.
    private static ScreenLayerPicks createPicksOnTheEmptyView() {

        var screenPicks = createPicksWithAControlStanding();

        when(screenPicks.layerSelection().getActiveLayer())
            .thenReturn(NoLayer.INSTANCE);

        return screenPicks;
    }
}
