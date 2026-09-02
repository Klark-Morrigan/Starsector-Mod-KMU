package kmu.maplayers.base.layer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the rule that keeps the control on the game's own filter row optional: a stored hide is acted on
 * only by a screen that has one, and read as shown by a screen that never got one.
 *
 * <p>The case that matters most is the one no player should ever reach - a save holding the layers
 * hidden on a session where the reach could not put a box up. Without the rule that would leave the
 * feature switched off with nothing on screen to switch it back on, so it is pinned here rather than
 * left to be noticed in play, where it can only be noticed by the player it has already stranded.
 *
 * <p>The stored pick is a stand-in throughout: what it holds, and how it paces its own ramp, is
 * {@link PersistedMapLayerVisibilityTest}'s subject. These cases pin only which of the two answers -
 * its own, or the rule's - a reader is given.
 */
final class ControlBackedMapLayerVisibilityTest {

    // A fade no end of the ramp shares, so a reading answered from the stored pick cannot be mistaken
    // for one the rule settled.
    private static final float PART_WAY_THROUGH_A_HIDE = 0.37f;

    private static final float FULLY_SHOWN = 1f;

    private final MapLayerVisibility storedVisibilityMock = mock(MapLayerVisibility.class);

    private final ControlBackedMapLayerVisibility visibility =
        new ControlBackedMapLayerVisibility(storedVisibilityMock);

    @Nested
    class AreLayersShown {

        @Test
        void areLayersShownIsTrueForAScreenThatNeverGotAControl() {
            // The rule itself: a hide the player cannot take back is not acted on, whatever the save
            // holds, so a reach that stops working between one session and the next costs them a
            // control rather than the feature.
            hideTheStoredPick();

            assertThat(visibility.areLayersShown())
                .isTrue();
        }

        @Test
        void areLayersShownDoesNotAskTheStoredPickWithoutAControl() {
            // Pinned rather than left as an accident of how the branch is written: the reading a
            // screen with no control gets is the rule's own and not a stored pick that happens to
            // agree with it.
            assertThat(visibility.areLayersShown())
                .isTrue();

            verifyNoInteractions(storedVisibilityMock);
        }

        @Test
        void areLayersShownFollowsTheStoredPickOnceAControlStands() {

            hideTheStoredPick();
            visibility.recordControlAttached();

            assertThat(visibility.areLayersShown())
                .isFalse();
        }
    }

    @Nested
    class ForgetControlAttached {

        @Test
        void forgetControlAttachedReturnsTheScreenToTheReadingItHadBeforeAControl() {
            // A control can be taken away as well as never obtained - a campaign unloaded under the
            // screens, or the player closing the switch that permits the reach at all. Either leaves
            // the screen with no way to reverse a hide, so either has to put the rule back in force;
            // a word that only ever went one way would strand a player exactly as a broken reach does.
            hideTheStoredPick();
            visibility.recordControlAttached();

            visibility.forgetControlAttached();

            assertThat(visibility.areLayersShown())
                .isTrue();
            assertThat(visibility.resolveShownFade())
                .isEqualTo(FULLY_SHOWN);
        }

        @Test
        void forgetControlAttachedLeavesTheStoredPickAsThePlayerLeftIt() {
            // The rule is about whether the mod acts on the choice, never about the choice: honoured
            // again the moment a control exists to reverse it, so a session that lost one costs the
            // player nothing beyond that session.
            hideTheStoredPick();
            visibility.recordControlAttached();

            visibility.forgetControlAttached();
            visibility.recordControlAttached();

            assertThat(visibility.areLayersShown())
                .isFalse();
        }
    }

    @Nested
    class ResolveShownFade {

        @Test
        void resolveShownFadeIsFullyShownForAScreenThatNeverGotAControl() {
            // The two readings stand down together: a screen the rule says is wholly shown cannot
            // also be part-way through dissolving off it.
            when(storedVisibilityMock.resolveShownFade())
                .thenReturn(PART_WAY_THROUGH_A_HIDE);

            assertThat(visibility.resolveShownFade())
                .isEqualTo(FULLY_SHOWN);
        }

        @Test
        void resolveShownFadeFollowsTheStoredPickOnceAControlStands() {

            when(storedVisibilityMock.resolveShownFade())
                .thenReturn(PART_WAY_THROUGH_A_HIDE);

            visibility.recordControlAttached();

            assertThat(visibility.resolveShownFade())
                .isEqualTo(PART_WAY_THROUGH_A_HIDE);
        }
    }

    @Nested
    class ShowLayers {

        @Test
        void showLayersWritesThroughForAScreenThatNeverGotAControl() {
            // The player's choice is kept whatever the rule reads: it is honoured again the moment a
            // control exists to reverse it, so a session that could not put a box up must not be able
            // to swallow one.
            visibility.showLayers(false);

            verify(storedVisibilityMock).showLayers(false);
        }
    }

    @Nested
    class GetStoredVisibility {

        @Test
        void getStoredVisibilityHandsBackThePickBeneathTheRule() {
            // What a control is bound to, and the reason it is the stored pick rather than the reading
            // above it: a box seeded from the reading would come up ticked over a save holding the
            // layers hidden, and stay at odds with them until it was clicked twice.
            assertThat(visibility.getStoredVisibility())
                .isSameAs(storedVisibilityMock);
        }
    }

    // Poses a save in which this screen's layers were switched off in an earlier session.
    private void hideTheStoredPick() {

        when(storedVisibilityMock.areLayersShown())
            .thenReturn(false);
    }
}
