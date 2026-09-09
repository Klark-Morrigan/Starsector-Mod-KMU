package kmu.maplayers.base.chrome;

import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.ControlBackedMapLayerVisibility;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRosters;
import kmu.maplayers.base.layer.MapLayerVisibility;
import kmu.maplayers.base.layer.NoLayer;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.layer.ScreenMemoryScopes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what the pass adds over the rule it asks: that every screen is asked rather than the one being
 * looked at, since one dialog moves both screens' rows and a screen nobody is on still has to be right
 * when they open it; and that a frame with nothing to settle writes nothing, which is what makes a
 * per-frame pass affordable at all.
 *
 * <p>What the heal does to a stranded pick, and which row it judges one against, is
 * {@link kmu.maplayers.base.layer.ScreenLayerTabsTest}'s.
 *
 * <p>Also the two answers that are not decisions of its own but which the heal rests on: that it goes
 * on running for the session, rows moving for as long as the player keeps arranging their bar, and that
 * it runs while the campaign is paused - every screen the bar draws on pauses it, so a pass standing
 * down under one would never see a row that had just moved.
 */
final class MapLayerPickUpkeepTest {

    // The campaign is paused on every screen the bar draws on, so this is the delta the pass actually
    // runs at.
    private static final float PAUSED_FRAME = 0f;

    // The id the arrangement store would name the layer that paints by, which nothing here arranges -
    // stated because registration reads every layer's id.
    private static final String PAINTING_LAYER_ID = "painting";

    private final MapLayer paintingLayerMock = mock(MapLayer.class);

    @AfterEach
    void restoreTheRosterTheCasesReplaced() {
        // The registry is static, so a roster of stand-ins would otherwise outlive its case.
        MapLayerRosters.restoreNonEmptyRoster();
    }

    @Nested
    class Advance {

        @Test
        void advanceHealsEveryScreenRatherThanTheOneOnShow() {

            registerTheEmptyViewBesideALayerThatPaints();

            var mapScreenPicks = buildScreenPicksOnTheEmptyView();
            var intelScreenPicks = buildScreenPicksOnTheEmptyView();

            new MapLayerPickUpkeep(() -> List.of(mapScreenPicks, intelScreenPicks))
                .advance(PAUSED_FRAME);

            // One dialog moves both screens' rows at once, and the screen nobody is looking at is the
            // one whose disagreement would first be seen on the frame it is opened at.
            verify(mapScreenPicks.layerSelection())
                .selectLayer(paintingLayerMock);
            verify(intelScreenPicks.layerSelection())
                .selectLayer(paintingLayerMock);
        }

        @Test
        void advanceLeavesAScreenAlreadyOnAnOfferedTabAlone() {

            registerTheEmptyViewBesideALayerThatPaints();

            var screenPicks = buildScreenPicks();

            when(screenPicks.layerSelection().getActiveLayer())
                .thenReturn(paintingLayerMock);

            new MapLayerPickUpkeep(() -> List.of(screenPicks)).advance(PAUSED_FRAME);

            // The common frame, and the whole of what makes a per-frame pass affordable: nothing has
            // moved, so nothing is written and no save is touched.
            verify(screenPicks.layerSelection(), never())
                .selectLayer(any());
        }

        @Test
        void advanceLeavesAHealedPickAloneOnTheNextFrame() {

            registerTheEmptyViewBesideALayerThatPaints();

            var screenPicks = buildScreenPicks();

            // The pick the first frame finds, then the one that frame left behind: the stand-in save
            // is what a heal would have written to, read back on the frame after.
            when(screenPicks.layerSelection().getActiveLayer())
                .thenReturn(NoLayer.INSTANCE, paintingLayerMock);

            var upkeep = new MapLayerPickUpkeep(() -> List.of(screenPicks));

            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);

            // A heal lands the pick on a tab the row offers, so the frame after it finds nothing to
            // do. Posed because a pass that rewrote its own answer would write a save every frame.
            verify(screenPicks.layerSelection(), times(1))
                .selectLayer(any());
        }
    }

    @Nested
    class IsDone {

        @Test
        void isDoneIsFalseSoThePassRunsForTheSession() {
            // Rows go on moving for as long as the player keeps arranging their bar, so a pass that
            // ended would leave every arrangement made after it unhealed.
            assertThat(new MapLayerPickUpkeep().isDone())
                .isFalse();
        }
    }

    @Nested
    class RunWhilePaused {

        @Test
        void runWhilePausedIsTrueSoTheHealReachesTheScreensThatCanMoveARow() {
            // Every screen the bar draws on pauses the campaign, so a pass that stood down while
            // paused would run on none of the frames it exists for.
            assertThat(new MapLayerPickUpkeep().runWhilePaused())
                .isTrue();
        }
    }

    // The roster the heal is about: the empty view beside a layer that paints, which is the shape
    // every install ships.
    private void registerTheEmptyViewBesideALayerThatPaints() {

        when(paintingLayerMock.getId())
            .thenReturn(PAINTING_LAYER_ID);

        MapLayerRosters.replaceRosterWith(NoLayer.INSTANCE, paintingLayerMock);
    }

    // One screen carrying a control of its own, which is what withholds the empty view's tab from it.
    private static ScreenLayerPicks buildScreenPicks() {

        var screenPicks = new ScreenLayerPicks(
            mock(ActiveLayerSelection.class),
            new ControlBackedMapLayerVisibility(mock(MapLayerVisibility.class)),
            ScreenMemoryScopes.createStandInScreen());

        screenPicks.layerVisibility().recordControlAttached();

        return screenPicks;
    }

    // The same screen still set to the tab its control has taken over, which is a pick its row offers
    // no place for and so the state the heal acts on.
    private static ScreenLayerPicks buildScreenPicksOnTheEmptyView() {

        var screenPicks = buildScreenPicks();

        when(screenPicks.layerSelection().getActiveLayer())
            .thenReturn(NoLayer.INSTANCE);

        return screenPicks;
    }
}
