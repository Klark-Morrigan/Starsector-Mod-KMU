package kmu.maplayers.base.layer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
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
 * <p>Pins the other subtraction beside it: the row is the player's own before any of the above is asked
 * of it, so the strip follows their order and drops the tabs they took off. What the two subtractions do
 * together is the part neither tier can be held to alone - the reconciliation itself is
 * {@link ArrangedLayersTest}'s - so what is pinned here is that the strip carries the arrangement at all,
 * and that a row either of them would empty keeps its last tab.
 *
 * <p>And the heal that keeps the bar, the map and the control agreeing: a pick the row does not offer is
 * put on the leading tab it does offer, and the control set to whether that tab paints. Both ways a row
 * can strand a pick are posed here, since the point of stating the rule as "not among the tabs" is that
 * they are one case. When the heal is asked - every frame, for every screen - is
 * {@link kmu.maplayers.base.chrome.MapLayerPickUpkeepTest}'s, this answering only what it does.
 */
final class ScreenLayerTabsTest {

    // The ids the stored arrangement names its layers by, the store holding ids rather than layers.
    private static final String NO_LAYER_ID = "no_layer";
    private static final String PAINTING_LAYER_ID = "painting";
    private static final String OTHER_PAINTING_LAYER_ID = "other_painting";

    private final MapLayer paintingLayerMock = mock(MapLayer.class);
    private final MapLayer otherPaintingLayerMock = mock(MapLayer.class);

    @AfterEach
    void restoreTheRosterThisCaseReplaced() {
        // The registry is static, so a roster of stand-ins would otherwise outlive its case.
        MapLayerRosters.restoreNonEmptyRoster();
    }

    @AfterEach
    void forgetTheArrangementThisCaseMade() {
        // The holder is static too, so a bar arranged here would otherwise reorder every later row.
        MapLayerArrangements.forgetTheArrangement();
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
            MapLayerRosters.replaceRosterWith(NoLayer.INSTANCE);

            assertThat(ScreenLayerTabs.resolveTabbedLayers(createPicksWithAControlStanding()))
                .containsExactly(NoLayer.INSTANCE);
        }

        @Test
        void resolveTabbedLayersPutsTheStripInThePlayersOwnOrder() {
            // The whole of what the arrangement buys: the row is theirs, laid over whatever the load
            // order registered, and it is the same row on every screen and in every campaign.
            registerTheEmptyViewBesideALayerThatPaints();

            MapLayerArrangements.arrangeBarWith(
                List.of(PAINTING_LAYER_ID, NO_LAYER_ID),
                List.of());

            assertThat(ScreenLayerTabs.resolveTabbedLayers(createPicksWithNoControl()))
                .containsExactly(paintingLayerMock, NoLayer.INSTANCE);
        }

        @Test
        void resolveTabbedLayersTakesAHiddenLayerOffTheStripAndLeavesItOnTheRoster() {
            // Hiding is not switching off: what the player took off is the way to reach the layer by
            // tab, not the layer. Held to through the roster lookup a stored pick resolves by, that
            // being what decides whether a save left on a hidden layer goes on painting it.
            registerTheEmptyViewBesideALayerThatPaints();

            MapLayerArrangements.arrangeBarWith(
                List.of(),
                List.of(PAINTING_LAYER_ID));

            assertThat(ScreenLayerTabs.resolveTabbedLayers(createPicksWithNoControl()))
                .containsExactly(NoLayer.INSTANCE);

            assertThat(MapLayerRegistry.resolveLayerById(PAINTING_LAYER_ID))
                .isSameAs(paintingLayerMock);
        }

        @Test
        void resolveTabbedLayersComposesHidingWithTheWithheldEmptyView() {
            // The two subtractions answer different questions - what the player took off the bar, and
            // what this screen's own control has taken over - so a row has to survive both being asked
            // of it at once.
            registerTwoLayersThatPaintBesideTheEmptyView();

            MapLayerArrangements.arrangeBarWith(
                List.of(),
                List.of(OTHER_PAINTING_LAYER_ID));

            assertThat(ScreenLayerTabs.resolveTabbedLayers(createPicksWithAControlStanding()))
                .containsExactly(paintingLayerMock);
        }

        @Test
        void resolveTabbedLayersKeepsTheLastTabHidingAndWithholdingWouldBothTakeOff() {
            // The two guards read as one here: hiding leaves the empty view alone on the row, and this
            // screen's control would take that too. A bar with no tabs has no way back to itself
            // however it was emptied, so the one left standing is offered.
            registerTheEmptyViewBesideALayerThatPaints();

            MapLayerArrangements.arrangeBarWith(
                List.of(),
                List.of(PAINTING_LAYER_ID));

            assertThat(ScreenLayerTabs.resolveTabbedLayers(createPicksWithAControlStanding()))
                .containsExactly(NoLayer.INSTANCE);
        }
    }

    @Nested
    class HealPickOntoOfferedTabs {

        @Test
        void healPickOntoOfferedTabsMovesAPickOffATabThePlayerTookOffTheBar() {
            // A layer painting from a tab that is not there is a map nothing on screen accounts for,
            // and the only way back to it is a dialog the player has to remember to open. So the pick
            // follows the tabs, and the control goes down with it because the tab it lands on paints
            // nothing.
            registerTheEmptyViewBesideALayerThatPaints();

            MapLayerArrangements.arrangeBarWith(
                List.of(),
                List.of(PAINTING_LAYER_ID));

            var screenPicks = createPicksWithAControlStanding();

            when(screenPicks.layerSelection().getActiveLayer())
                .thenReturn(paintingLayerMock);

            ScreenLayerTabs.healPickOntoOfferedTabs(screenPicks);

            verify(screenPicks.layerSelection())
                .selectLayer(NoLayer.INSTANCE);
            verify(screenPicks.layerVisibility().getStoredVisibility())
                .showLayers(false);
        }

        @Test
        void healPickOntoOfferedTabsPutsThePickBackOnATabThePlayerRestores() {
            // The other half of the round trip, and the case that needs both halves of the rule: the
            // pick left on the empty view has nowhere to sit once that tab is withheld again, and the
            // tab it lands on would light over a map the control was still holding down.
            registerTheEmptyViewBesideALayerThatPaints();

            var screenPicks = createPicksWithAControlStanding();
            var pick = new AtomicReference<MapLayer>(paintingLayerMock);

            when(screenPicks.layerSelection().getActiveLayer())
                .thenAnswer(read -> pick.get());
            doAnswer(selection -> {
                pick.set(selection.getArgument(0));
                return null;
            }).when(screenPicks.layerSelection()).selectLayer(any());

            MapLayerArrangements.arrangeBarWith(List.of(), List.of(PAINTING_LAYER_ID));
            ScreenLayerTabs.healPickOntoOfferedTabs(screenPicks);

            MapLayerArrangements.arrangeBarWith(List.of(), List.of());
            ScreenLayerTabs.healPickOntoOfferedTabs(screenPicks);

            assertThat(pick.get())
                .isSameAs(paintingLayerMock);
            verify(screenPicks.layerVisibility().getStoredVisibility())
                .showLayers(true);
        }

        @Test
        void healPickOntoOfferedTabsMovesAPickOffTheTabAControlHasTakenOver() {
            // The case the one-time migration used to cover, and the answer moved with the rule: the
            // pick lands on the tab that is actually there and the control stands up under it, rather
            // than the map staying blank beneath a lit tab.
            registerTheEmptyViewBesideALayerThatPaints();

            var screenPicks = createPicksOnTheEmptyView();

            ScreenLayerTabs.healPickOntoOfferedTabs(screenPicks);

            verify(screenPicks.layerSelection())
                .selectLayer(paintingLayerMock);
            verify(screenPicks.layerVisibility().getStoredVisibility())
                .showLayers(true);
        }

        @Test
        void healPickOntoOfferedTabsLeavesAPickTheRowStillOffersAlone() {
            // Which is every call but the ones just after something moved: a screen sitting on a tab
            // its own bar carries has nothing to settle, and a write here would move a player off a
            // map they are looking at.
            registerTheEmptyViewBesideALayerThatPaints();

            var screenPicks = createPicksWithAControlStanding();

            when(screenPicks.layerSelection().getActiveLayer())
                .thenReturn(paintingLayerMock);

            ScreenLayerTabs.healPickOntoOfferedTabs(screenPicks);

            verify(screenPicks.layerSelection(), never())
                .selectLayer(any());
            verify(screenPicks.layerVisibility().getStoredVisibility(), never())
                .showLayers(anyBoolean());
        }

        @Test
        void healPickOntoOfferedTabsKeepsAnEmptyViewPickWhereThatTabStands() {
            // The empty view is a first-class tab wherever no control has taken it over, so a screen
            // set to it has made a choice its own bar still shows. Nothing to heal, and a heal that
            // fired would start painting over the map of a player who asked for nothing.
            registerTheEmptyViewBesideALayerThatPaints();

            var screenPicks = createPicksWithNoControl();

            when(screenPicks.layerSelection().getActiveLayer())
                .thenReturn(NoLayer.INSTANCE);

            ScreenLayerTabs.healPickOntoOfferedTabs(screenPicks);

            verify(screenPicks.layerSelection(), never())
                .selectLayer(any());
            verify(screenPicks.layerVisibility().getStoredVisibility(), never())
                .showLayers(anyBoolean());
        }

        @Test
        void healPickOntoOfferedTabsLandsThePickOnTheLastTabARowWasHidDownTo() {
            // The row asked about is the offered one, guard and all, so a bar hidden down to its last
            // tab lands the pick on that tab - the guard being the only reason anything is standing
            // there to land on.
            registerTwoLayersThatPaintBesideTheEmptyView();

            MapLayerArrangements.arrangeBarWith(
                List.of(),
                List.of(PAINTING_LAYER_ID, OTHER_PAINTING_LAYER_ID));

            var screenPicks = createPicksWithNoControl();

            when(screenPicks.layerSelection().getActiveLayer())
                .thenReturn(otherPaintingLayerMock);

            ScreenLayerTabs.healPickOntoOfferedTabs(screenPicks);

            verify(screenPicks.layerSelection())
                .selectLayer(NoLayer.INSTANCE);
            verify(screenPicks.layerVisibility().getStoredVisibility())
                .showLayers(false);
        }

        @Test
        void healPickOntoOfferedTabsLandsAScreenSetToNoLayerAtAllOnTheLeadingTab() {
            // A selection seam answering nothing over a populated bar - which a foreign mod's own
            // implementation may - reads as a pick the row does not offer, so it is landed on a tab
            // rather than left lighting none.
            registerTheEmptyViewBesideALayerThatPaints();

            var screenPicks = createPicksWithNoControl();

            when(screenPicks.layerSelection().getActiveLayer())
                .thenReturn(null);

            ScreenLayerTabs.healPickOntoOfferedTabs(screenPicks);

            verify(screenPicks.layerSelection())
                .selectLayer(NoLayer.INSTANCE);
        }

        @Test
        void healPickOntoOfferedTabsWritesNothingWithNothingRegistered() {
            // A bare bar has no tab to land a pick on. Reachable before a composition root has
            // registered anything, which is a frame the pass can run on rather than a state the
            // player can be in.
            MapLayerRosters.forgetEveryLayer();

            var screenPicks = createPicksOnTheEmptyView();

            ScreenLayerTabs.healPickOntoOfferedTabs(screenPicks);

            verify(screenPicks.layerSelection(), never())
                .selectLayer(any());
            verify(screenPicks.layerVisibility().getStoredVisibility(), never())
                .showLayers(anyBoolean());
        }
    }

    // The roster the withholding is about: the empty view beside a layer that paints, which is the
    // shape every install ships and the only one in which anything is withheld at all.
    private void registerTheEmptyViewBesideALayerThatPaints() {

        stubTheLayerThatPaints();
        MapLayerRosters.replaceRosterWith(NoLayer.INSTANCE, paintingLayerMock);
    }

    // The same roster with a second painting layer on the end, which is the install a foreign mod's
    // layer makes and the only shape in which hiding and withholding can both bite at once.
    private void registerTwoLayersThatPaintBesideTheEmptyView() {

        stubTheLayerThatPaints();

        when(otherPaintingLayerMock.getId())
            .thenReturn(OTHER_PAINTING_LAYER_ID);

        MapLayerRosters.replaceRosterWith(
            NoLayer.INSTANCE, paintingLayerMock, otherPaintingLayerMock);
    }

    // What the layer that paints answers, whichever roster it stands in: it offers itself as the
    // default pick, which is what a migration off the withheld tab moves the screen to, and it answers
    // the id a stored arrangement would name it by - the store holding ids rather than layers. The
    // empty view leads the row and declines the pick, as it does in play.
    private void stubTheLayerThatPaints() {

        when(paintingLayerMock.isOfferedAsDefaultPick())
            .thenReturn(true);
        when(paintingLayerMock.getId())
            .thenReturn(PAINTING_LAYER_ID);
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
            new ControlBackedMapLayerVisibility(mock(MapLayerVisibility.class)),
            ScreenMemoryScopes.createStandInScreen());
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
