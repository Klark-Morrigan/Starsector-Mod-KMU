package kmu.maplayers.base.layer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the per-screen show-or-hide pick: it reads and writes one sector-memory key, defaults to shown,
 * and never touches a second screen's key - the independence the whole per-screen split rests on.
 *
 * <p>The ramp cases are the reason the clock and the pace are injected rather than read: a fade derived
 * from elapsed real time is only checkable at instants a test can name, and a fade the shipped knob paces
 * would be re-tuned by any later pass over the settings screen.
 */
final class PersistedMapLayerVisibilityTest {

    private static final String KEY = "$kmu_test_layers_shown";
    private static final String OTHER_KEY = "$kmu_test_layers_shown_other";

    // A pace no shipped default shares, so a reading that happens to match one is not mistaken for a
    // reading taken through the injected one.
    private static final double RAMP_SECONDS = 0.4;

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private MockedStatic<Global> globalMock;
    private MemoryAPI memoryMock;
    private PersistedMapLayerVisibility visibility;

    // The one value the mocked memory holds under KEY, null standing for a key never written - the
    // distinction the pick's default answers to.
    private Boolean storedPick;

    // The instant the injected clock reads, moved by the ramp cases rather than by real time passing.
    private long elapsedNanos;

    // The injected pace, which one case winds to nothing to reach the cut branch.
    private double rampSeconds = RAMP_SECONDS;

    @BeforeEach
    void linkAStubbedSectorMemory() {

        memoryMock = mock(MemoryAPI.class);

        when(memoryMock.contains(KEY))
            .thenAnswer(invocation -> storedPick != null);
        when(memoryMock.getBoolean(KEY))
            .thenAnswer(invocation -> storedPick);

        doAnswer(
            invocation -> {
                storedPick = invocation.getArgument(1);
                return null;
            })
            .when(memoryMock)
            .set(eq(KEY), any());

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getMemoryWithoutUpdate())
            .thenReturn(memoryMock);

        globalMock = mockStatic(Global.class);
        globalMock
            .when(Global::getSector)
            .thenReturn(sectorMock);

        visibility = new PersistedMapLayerVisibility(
            KEY,
            () -> elapsedNanos,
            () -> rampSeconds);
    }

    @AfterEach
    void releaseTheStaticMock() {
        globalMock.close();
    }

    @Nested
    class AreLayersShown {

        @Test
        void areLayersShownDefaultsToShownWithoutAStoredPick() {

            assertThat(visibility.areLayersShown())
                .isTrue();
        }

        @Test
        void areLayersShownReadsTheStoredPickUnderItsOwnKey() {

            storedPick = false;

            assertThat(visibility.areLayersShown())
                .isFalse();
        }
    }

    @Nested
    class ShowLayers {

        @Test
        void showLayersWritesThePickUnderItsOwnKey() {

            visibility.showLayers(false);

            verify(memoryMock)
                .set(KEY, false);
        }

        @Test
        void showLayersUnderOneKeyLeavesAnotherKeysPickUntouched() {
            // The independence the per-screen split needs: hiding on one screen must never write a
            // second screen's key, or the player would lose the layers on a screen they are not on.
            visibility.showLayers(false);

            verify(memoryMock, never())
                .set(eq(OTHER_KEY), any());
        }

        @Test
        void showLayersWritesNothingForAnUnchangedPick() {

            storedPick = false;
            visibility.showLayers(false);

            verify(memoryMock, never())
                .set(eq(KEY), any());
        }

        @Test
        void showLayersRecordsNoRampWhenThereIsNoSectorToWriteInto() {

            globalMock
                .when(Global::getSector)
                .thenReturn(null);

            visibility.showLayers(false);

            // The write was dropped, so the pick still reads shown - and a ramp recorded here would
            // dissolve the layers away towards a state nothing stored.
            assertThat(visibility.resolveShownFade())
                .isCloseTo(1f, within(1e-6f));
        }
    }

    @Nested
    class ResolveShownFade {

        @Test
        void resolveShownFadeIsFullyShownForAShownPickThatHasNotFlipped() {

            assertThat(visibility.resolveShownFade())
                .isCloseTo(1f, within(1e-6f));
        }

        @Test
        void resolveShownFadeIsFullyHiddenForAStoredHiddenPickThatHasNotFlipped() {
            // What a save loaded with the layers hidden reads on its first frame: already gone, rather
            // than dissolving away a picture the screen never drew.
            storedPick = false;

            assertThat(visibility.resolveShownFade())
                .isCloseTo(0f, within(1e-6f));
        }

        @Test
        void resolveShownFadeRidesDownTheRampAfterHiding() {

            visibility.showLayers(false);
            advanceClockBySeconds(0.1);

            assertThat(visibility.resolveShownFade())
                .isCloseTo(0.75f, within(1e-6f));
        }

        @Test
        void resolveShownFadeSettlesFullyHiddenOnceTheRampIsPast() {

            visibility.showLayers(false);
            advanceClockBySeconds(0.4);

            assertThat(visibility.resolveShownFade())
                .isCloseTo(0f, within(1e-6f));
        }

        @Test
        void resolveShownFadeRidesUpTheRampAfterShowing() {

            storedPick = false;
            visibility.showLayers(true);
            advanceClockBySeconds(0.1);

            assertThat(visibility.resolveShownFade())
                .isCloseTo(0.25f, within(1e-6f));
        }

        @Test
        void resolveShownFadeCutsStraightToThePickWithARampOfNoTimeAtAll() {

            rampSeconds = 0;
            visibility.showLayers(false);

            // Settled on the frame of the flip rather than dividing by the pace the player wound to
            // nothing.
            assertThat(visibility.resolveShownFade())
                .isCloseTo(0f, within(1e-6f));
        }

        @Test
        void resolveShownFadeReversesFromTheFadeTheFlipCaughtItAt() {

            visibility.showLayers(false);
            advanceClockBySeconds(0.1);

            visibility.showLayers(true);
            advanceClockBySeconds(0.05);

            // Set off again from the 0.75 the hide had reached, not from either end of the ramp: a
            // restart would read 0.125 here and a resume from the far end would already be settled.
            assertThat(visibility.resolveShownFade())
                .isCloseTo(0.875f, within(1e-6f));
        }
    }

    // Moves the injected clock on, which is what the ramp cases stand in place of real time passing.
    private void advanceClockBySeconds(double seconds) {
        elapsedNanos += (long) (seconds * NANOS_PER_SECOND);
    }
}
