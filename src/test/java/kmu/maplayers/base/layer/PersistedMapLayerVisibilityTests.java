package kmu.maplayers.base.layer;

import kmlib.animation.TraverseDurations;
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
 * <p>The ramp itself is KMLib's {@code ClockedTraverseFractionTests}' to pin at named instants; the fade
 * cases here pin only that a flip turns it and that it is read toward the stored pick. The pace is injected
 * because a fade the shipped knob paces would be re-tuned by any later pass over the settings screen.
 */
final class PersistedMapLayerVisibilityTests {

    private static final ScreenMemoryScope SCREEN_SCOPE = ScreenMemoryScopes.createStandInScreen();

    // The slot that screen composes, as a literal: the base key is a save-serialised identity, so a rename
    // must break this test rather than ship and return every existing save to shown.
    private static final String KEY = "$kmu_political_layers_shown_test";

    // The same base key under a second screen, which is what this pick must never write.
    private static final String OTHER_KEY = "$kmu_political_layers_shown_other";

    // A pace no shipped default shares, so a reading that happens to match one is not mistaken for a
    // reading taken through the injected one.
    private static final float RAMP_SECONDS = 0.4f;

    // A pace long enough that no case can run through a measurable share of it on the real clock.
    private static final float AN_HOUR_IN_SECONDS = 3600f;

    // Below anything a ramp an hour long reads in the moment after it turns, and far above the nothing a
    // cut reads.
    private static final float ALL_BUT_WHOLE = 0.99f;

    // How close a settled reading has to land. Loose enough to absorb the float arithmetic behind the
    // curve.
    private static final float FADE_TOLERANCE = 1e-6f;

    private SectorMemoryFake sectorMemoryFake;
    private PersistedMapLayerVisibility visibility;

    // The injected pace, which cases wind to nothing to reach the cut branch or out to an hour.
    private float rampSeconds = RAMP_SECONDS;

    @BeforeEach
    void openASectorMemory() {

        sectorMemoryFake = new SectorMemoryFake();

        visibility = new PersistedMapLayerVisibility(
            SCREEN_SCOPE,
            () -> TraverseDurations.createSymmetric(rampSeconds));
    }

    @AfterEach
    void closeTheSectorMemory() {
        sectorMemoryFake.close();
    }

    @Nested
    class AreLayersShown {

        @Test
        void defaultsToShownWithoutAStoredPick() {

            assertThat(visibility.areLayersShown())
                .isTrue();
        }

        @Test
        void readsTheStoredPickUnderItsOwnKey() {

            sectorMemoryFake.storeValue(KEY, false);

            assertThat(visibility.areLayersShown())
                .isFalse();
        }
    }

    @Nested
    class ShowLayers {

        @Test
        void writesThePickUnderItsOwnKey() {

            visibility.showLayers(false);

            assertThat(sectorMemoryFake.readStoredValue(KEY))
                .isEqualTo(false);
        }

        @Test
        void onOneScreenLeavesAnotherScreensPickUntouched() {
            // The independence the per-screen split needs: hiding on one screen must never write a
            // second screen's slot, or the player would lose the layers on a screen they are not on.
            visibility.showLayers(false);

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_KEY))
                .isFalse();
        }

        @Test
        void writesNothingForAnUnchangedPick() {

            sectorMemoryFake.storeValue(KEY, false);
            visibility.showLayers(false);

            assertThat(sectorMemoryFake.countWritesTo(KEY))
                .isZero();
        }

        @Test
        void recordsNoRampWhenThereIsNoSectorToWriteInto() {

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
        void isFullyShownForAShownPickThatHasNotFlipped() {

            assertThat(visibility.resolveShownFade())
                .isCloseTo(1f, within(FADE_TOLERANCE));
        }

        @Test
        void isFullyHiddenForAStoredHiddenPickThatHasNotFlipped() {
            // What a save loaded with the layers hidden reads on its first frame: already gone, rather
            // than dissolving away a picture the screen never drew.
            sectorMemoryFake.storeValue(KEY, false);

            assertThat(visibility.resolveShownFade())
                .isCloseTo(0f, within(FADE_TOLERANCE));
        }

        @Test
        void startsDissolvingFromFullyShownOnAHide() {
            // The hide turns the ramp rather than cutting: an hour-long pace has barely begun, so the
            // layers still read all but whole where a cut would read nothing.
            rampSeconds = AN_HOUR_IN_SECONDS;

            visibility.showLayers(false);

            assertThat(visibility.resolveShownFade())
                .isGreaterThan(ALL_BUT_WHOLE);
        }

        @Test
        void cutsStraightToThePickWithARampOfNoTimeAtAll() {

            rampSeconds = 0;
            visibility.showLayers(false);

            // Settled on the frame of the flip rather than dividing by the pace the player wound to
            // nothing.
            assertThat(visibility.resolveShownFade())
                .isCloseTo(0f, within(FADE_TOLERANCE));
        }
    }
}
