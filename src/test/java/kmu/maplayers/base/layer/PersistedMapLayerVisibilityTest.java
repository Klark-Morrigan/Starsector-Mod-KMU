package kmu.maplayers.base.layer;

import kmlib.profiling.Timings;
import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the per-screen show-or-hide pick: the slot it composes for the screen it was built for, that it
 * defaults to shown, and that it never touches a second screen's slot - the independence the whole
 * per-screen split rests on.
 *
 * <p>The ramp cases are the reason the clock and the pace are injected rather than read: a fade derived
 * from elapsed real time is only checkable at instants a case can name, and a fade the shipped knob paces
 * would be re-tuned by any later pass over the settings screen.
 *
 * <p>Each expected fade is stated as the eased value, with the linear position it comes off named beside
 * it. The two are worth keeping apart in the reading: the linear position is what a flip records and what
 * a reversal sets off from, while the eased one is all a consumer ever sees.
 */
final class PersistedMapLayerVisibilityTest {

    private static final ScreenMemoryScope SCREEN_SCOPE = ScreenMemoryScopes.createStandInScreen();

    // The slot that screen composes, as a literal: the base key is a save-serialised identity, so a rename
    // must break this test rather than ship and return every existing save to shown.
    private static final String KEY = "$kmu_political_layers_shown_test";

    // The same base key under a second screen, which is what this pick must never write.
    private static final String OTHER_KEY = "$kmu_political_layers_shown_other";

    // A pace no shipped default shares, so a reading that happens to match one is not mistaken for a
    // reading taken through the injected one.
    private static final double RAMP_SECONDS = 0.4;

    // How close an eased reading has to land. Loose enough to absorb the float arithmetic behind the
    // curve, tight enough that no two positions in these cases could be confused for each other.
    private static final float FADE_TOLERANCE = 1e-6f;

    private SectorMemoryFake sectorMemoryFake;
    private PersistedMapLayerVisibility visibility;

    // The instant the injected clock reads, moved by the ramp cases rather than by real time passing.
    private long elapsedNanos;

    // The injected pace, which one case winds to nothing to reach the cut branch.
    private double rampSeconds = RAMP_SECONDS;

    @BeforeEach
    void openASectorMemory() {

        sectorMemoryFake = new SectorMemoryFake();

        visibility = new PersistedMapLayerVisibility(
            SCREEN_SCOPE,
            () -> elapsedNanos,
            () -> rampSeconds);
    }

    @AfterEach
    void closeTheSectorMemory() {
        sectorMemoryFake.close();
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

            sectorMemoryFake.storeValue(KEY, false);

            assertThat(visibility.areLayersShown())
                .isFalse();
        }
    }

    @Nested
    class ShowLayers {

        @Test
        void showLayersWritesThePickUnderItsOwnKey() {

            visibility.showLayers(false);

            assertThat(sectorMemoryFake.readStoredValue(KEY))
                .isEqualTo(false);
        }

        @Test
        void showLayersOnOneScreenLeavesAnotherScreensPickUntouched() {
            // The independence the per-screen split needs: hiding on one screen must never write a
            // second screen's slot, or the player would lose the layers on a screen they are not on.
            visibility.showLayers(false);

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_KEY))
                .isFalse();
        }

        @Test
        void showLayersWritesNothingForAnUnchangedPick() {

            sectorMemoryFake.storeValue(KEY, false);
            visibility.showLayers(false);

            assertThat(sectorMemoryFake.countWritesTo(KEY))
                .isZero();
        }

        @Test
        void showLayersRecordsNoRampWhenThereIsNoSectorToWriteInto() {

            sectorMemoryFake.removeSector();
            visibility.showLayers(false);

            // The write was dropped, so the pick still reads shown - and a ramp recorded here would
            // dissolve the layers away towards a state nothing stored.
            assertThat(visibility.resolveShownFade())
                .isCloseTo(1f, within(FADE_TOLERANCE));
        }
    }

    @Nested
    class ResolveShownFade {

        @Test
        void resolveShownFadeIsFullyShownForAShownPickThatHasNotFlipped() {

            assertThat(visibility.resolveShownFade())
                .isCloseTo(1f, within(FADE_TOLERANCE));
        }

        @Test
        void resolveShownFadeIsFullyHiddenForAStoredHiddenPickThatHasNotFlipped() {
            // What a save loaded with the layers hidden reads on its first frame: already gone, rather
            // than dissolving away a picture the screen never drew.
            sectorMemoryFake.storeValue(KEY, false);

            assertThat(visibility.resolveShownFade())
                .isCloseTo(0f, within(FADE_TOLERANCE));
        }

        @Test
        void resolveShownFadeRidesDownTheRampAfterHiding() {

            visibility.showLayers(false);
            advanceClockBySeconds(0.1);

            // A quarter of the way down, so linearly 0.75 - eased, still 0.84 on screen, the curve
            // being at its flattest where it leaves an end.
            assertThat(visibility.resolveShownFade())
                .isCloseTo(0.84375f, within(FADE_TOLERANCE));
        }

        @Test
        void resolveShownFadeSettlesFullyHiddenOnceTheRampIsPast() {

            visibility.showLayers(false);
            advanceClockBySeconds(0.4);

            assertThat(visibility.resolveShownFade())
                .isCloseTo(0f, within(FADE_TOLERANCE));
        }

        @Test
        void resolveShownFadeRidesUpTheRampAfterShowing() {

            sectorMemoryFake.storeValue(KEY, false);

            visibility.showLayers(true);
            advanceClockBySeconds(0.1);

            // Linearly 0.25, eased to 0.15625 - the same flat start the way down has, mirrored.
            assertThat(visibility.resolveShownFade())
                .isCloseTo(0.15625f, within(FADE_TOLERANCE));
        }

        @Test
        void resolveShownFadeSettlesFullyShownOnceTheRampIsPast() {
            // The far end of the ramp, which is the bound a fade left running would climb past: a
            // consumer multiplying by it would brighten the overlay beyond what it paints at rest.
            sectorMemoryFake.storeValue(KEY, false);

            visibility.showLayers(true);
            advanceClockBySeconds(4);

            assertThat(visibility.resolveShownFade())
                .isCloseTo(1f, within(FADE_TOLERANCE));
        }

        @Test
        void resolveShownFadeCutsStraightToThePickWithARampOfNoTimeAtAll() {

            rampSeconds = 0;
            visibility.showLayers(false);

            // Settled on the frame of the flip rather than dividing by the pace the player wound to
            // nothing.
            assertThat(visibility.resolveShownFade())
                .isCloseTo(0f, within(FADE_TOLERANCE));
        }

        @Test
        void resolveShownFadeReversesFromTheProgressTheFlipCaughtItAt() {

            visibility.showLayers(false);
            advanceClockBySeconds(0.1);

            visibility.showLayers(true);
            advanceClockBySeconds(0.05);

            // Set off again from the 0.75 the hide had linearly reached and an eighth further up, so
            // 0.875 linearly and 0.957 eased. A restart would read 0.043 here, and a resume from the
            // far end would already be settled at 1.
            assertThat(visibility.resolveShownFade())
                .isCloseTo(0.95703125f, within(FADE_TOLERANCE));
        }
    }

    // Moves the injected clock on, which is what the ramp cases stand in place of real time passing.
    private void advanceClockBySeconds(double seconds) {
        elapsedNanos += Timings.convertSecondsToNanos(seconds);
    }
}
