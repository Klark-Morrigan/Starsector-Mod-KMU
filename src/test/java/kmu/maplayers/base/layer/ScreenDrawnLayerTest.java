package kmu.maplayers.base.layer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the one thing that parts what a screen draws from what it is set to: a switched-off screen goes on
 * showing the picture that was on it until its dissolve is over, whatever its pick has done meanwhile.
 *
 * <p>That is not a nicety. Taking the last painting tab off the bar switches the screen off and lands its
 * pick on the empty view in the one frame - both are owed at once, or the bar, the map and the box
 * disagree - so a dissolve reading the live pick would find nothing to dissolve on the very frame it
 * began, and the map would cut while the sidebar beside it thinned. The case that pins it poses exactly
 * that: a picture drawn, then the screen switched off and the pick moved together.
 *
 * <p>The two picks are stand-ins throughout. What either of them holds, and how the ramp between the ends
 * is paced, are {@link PersistedActiveLayerSelectionTest}'s and {@link PersistedMapLayerVisibilityTest}'s;
 * what is left here is which of the two a reader is answered from, and when.
 */
final class ScreenDrawnLayerTest {

    // Part-way through a dissolve: some of the screen's picture still on it. Any value short of the far
    // end serves, the reading being "more than none".
    private static final float HALF_FADED_OUT = 0.5f;

    private static final float FULLY_SHOWN = 1f;
    private static final float FULLY_HIDDEN = 0f;

    private final MapLayer drawingLayerMock = mock(MapLayer.class);
    private final MapLayer otherLayerMock = mock(MapLayer.class);

    private final ActiveLayerSelection layerSelectionMock = mock(ActiveLayerSelection.class);
    private final MapLayerVisibility layerVisibilityMock = mock(MapLayerVisibility.class);

    private final ScreenDrawnLayer drawnLayer =
        new ScreenDrawnLayer(layerSelectionMock, layerVisibilityMock);

    @Nested
    class ResolveDrawnLayer {

        @Test
        void resolveDrawnLayerAnswersThePickWhileTheScreenIsShown() {

            showTheScreenOn(drawingLayerMock);

            assertThat(drawnLayer.resolveDrawnLayer())
                .isSameAs(drawingLayerMock);
        }

        @Test
        void resolveDrawnLayerAnswersAShownScreenWithoutReadingTheDissolve() {
            // Worth pinning rather than left as an accident of the order two conditions are written in:
            // the fade is derived from a clock with a settings read behind it, and this sits on the map's
            // hottest path, so a screen that is simply on must not pay for it.
            when(layerVisibilityMock.areLayersShown())
                .thenReturn(true);
            when(layerSelectionMock.getActiveLayer())
                .thenReturn(drawingLayerMock);

            drawnLayer.resolveDrawnLayer();

            verify(layerVisibilityMock, never())
                .resolveShownFade();
        }

        @Test
        void resolveDrawnLayerKeepsDrawingThePictureThePickHasMovedOffOfWhileItDissolves() {
            // The case the memory exists for. Taking the last painting tab off the bar switches the
            // screen off and lands the pick on the empty view together, so a dissolve reading the live
            // pick would have nothing to dissolve on the frame it began - the map cutting away while the
            // sidebar beside it thins.
            showTheScreenOn(drawingLayerMock);
            drawnLayer.resolveDrawnLayer();

            startDissolvingTheScreen();

            when(layerSelectionMock.getActiveLayer())
                .thenReturn(NoLayer.INSTANCE);

            assertThat(drawnLayer.resolveDrawnLayer())
                .isSameAs(drawingLayerMock);
        }

        @Test
        void resolveDrawnLayerKeepsThePictureThatWasLeavingWhenAnotherTabIsPickedMidDissolve() {
            // The same memory answering the other way a pick moves under a dissolve: a tab switched by
            // key while the screen is on its way off. What leaves the screen is what was on it, not
            // whatever has been picked since - a layer that was never drawn cannot be seen to go.
            showTheScreenOn(drawingLayerMock);
            drawnLayer.resolveDrawnLayer();

            startDissolvingTheScreen();

            when(layerSelectionMock.getActiveLayer())
                .thenReturn(otherLayerMock);

            assertThat(drawnLayer.resolveDrawnLayer())
                .isSameAs(drawingLayerMock);
        }

        @Test
        void resolveDrawnLayerIsNothingOnceTheDissolveIsOver() {
            // The end every dissolve reaches, and the one read that takes the overlay, the labels and the
            // hover box off the screen together.
            showTheScreenOn(drawingLayerMock);
            drawnLayer.resolveDrawnLayer();

            when(layerVisibilityMock.areLayersShown())
                .thenReturn(false);
            when(layerVisibilityMock.resolveShownFade())
                .thenReturn(FULLY_HIDDEN);

            assertThat(drawnLayer.resolveDrawnLayer())
                .isNull();
        }

        @Test
        void resolveDrawnLayerDropsThePictureItHeldOnceTheDissolveIsOver() {
            // Released rather than kept: a screen switched on again catches its pick on the next frame,
            // so a picture held past the end of its own dissolve is a layer nothing is drawing that the
            // next dissolve would show in place of the one that was really there.
            showTheScreenOn(drawingLayerMock);
            drawnLayer.resolveDrawnLayer();

            when(layerVisibilityMock.areLayersShown())
                .thenReturn(false);
            when(layerVisibilityMock.resolveShownFade())
                .thenReturn(FULLY_HIDDEN);

            drawnLayer.resolveDrawnLayer();

            when(layerVisibilityMock.resolveShownFade())
                .thenReturn(HALF_FADED_OUT);

            assertThat(drawnLayer.resolveDrawnLayer())
                .isNull();
        }

        @Test
        void resolveDrawnLayerIsNothingForAScreenSwitchedOffBeforeItEverDrew() {
            // Nothing was on the screen, so nothing dissolves off it. The honest answer rather than a
            // guess at the pick: every control that can switch a screen off stands on that screen, so a
            // switch-off in play always has drawn frames behind it and this is a state a session does
            // not reach.
            startDissolvingTheScreen();

            when(layerSelectionMock.getActiveLayer())
                .thenReturn(drawingLayerMock);

            assertThat(drawnLayer.resolveDrawnLayer())
                .isNull();
        }

        @Test
        void resolveDrawnLayerFollowsThePickAgainOnceTheScreenComesBack() {
            // The way back up the ramp: a screen coming on draws its pick from the first frame, so the
            // memory must give way to the pick rather than the picture it was holding.
            showTheScreenOn(drawingLayerMock);
            drawnLayer.resolveDrawnLayer();

            startDissolvingTheScreen();
            drawnLayer.resolveDrawnLayer();

            showTheScreenOn(otherLayerMock);

            assertThat(drawnLayer.resolveDrawnLayer())
                .isSameAs(otherLayerMock);
        }
    }

    @Nested
    class ForgetDrawnLayer {

        @Test
        void forgetDrawnLayerLeavesTheReadingAScreenThatHasNeverDrawnGives() {

            showTheScreenOn(drawingLayerMock);
            drawnLayer.resolveDrawnLayer();

            drawnLayer.forgetDrawnLayer();
            startDissolvingTheScreen();

            assertThat(drawnLayer.resolveDrawnLayer())
                .isNull();
        }
    }

    // Poses a screen with its layers on it, set to the given tab.
    private void showTheScreenOn(MapLayer layer) {

        when(layerVisibilityMock.areLayersShown())
            .thenReturn(true);
        when(layerVisibilityMock.resolveShownFade())
            .thenReturn(FULLY_SHOWN);
        when(layerSelectionMock.getActiveLayer())
            .thenReturn(layer);
    }

    // Poses that screen switched off with part of its picture still on it.
    private void startDissolvingTheScreen() {

        when(layerVisibilityMock.areLayersShown())
            .thenReturn(false);
        when(layerVisibilityMock.resolveShownFade())
            .thenReturn(HALF_FADED_OUT);
    }
}
