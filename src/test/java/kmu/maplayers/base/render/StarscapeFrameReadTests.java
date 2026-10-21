package kmu.maplayers.base.render;

import kmu.maplayers.base.machinery.SectorMapMachinery;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that the Starscape read is made once per frame for every Starscape surface of one sector,
 * against the very boundary that frame's preparation is claimed on - and that, with no boundary seen,
 * every ask reads afresh rather than answering off a frame nobody saw begin.
 */
final class StarscapeFrameReadTests {

    private final SectorMapMachinery machinery = new SectorMapMachinery(null);
    private final StarscapeFrameRead frameRead = StarscapeFrameRead.resolveReadIn(machinery);

    // What the live read answers, mutable so a case can move it between asks, and how often it was
    // asked.
    private boolean isStarscapeShowing = true;
    private int liveReadCount;

    private final BooleanSupplier readStarscapeShowing = () -> {

        liveReadCount++;
        return isStarscapeShowing;
    };

    @Nested
    class IsStarscapeMapShowing {

        @Test
        void readsOnceForEverySurfaceOfOneFrame() {
            // Both Starscape surfaces ask before drawing, and the answer cannot move between them.
            beginFrame();

            frameRead.isStarscapeMapShowing(readStarscapeShowing);
            frameRead.isStarscapeMapShowing(readStarscapeShowing);

            assertThat(liveReadCount)
                .isEqualTo(1);
        }

        @Test
        void answersWhatTheFramesReadAnswered() {

            beginFrame();
            frameRead.isStarscapeMapShowing(readStarscapeShowing);

            isStarscapeShowing = false;

            assertThat(frameRead.isStarscapeMapShowing(readStarscapeShowing))
                .isTrue();
        }

        @Test
        void readsAfreshOnTheNextFrame() {
            // The player can switch the filter between frames, so a frame's answer is that frame's.
            beginFrame();
            frameRead.isStarscapeMapShowing(readStarscapeShowing);

            isStarscapeShowing = false;
            beginFrame();

            assertThat(frameRead.isStarscapeMapShowing(readStarscapeShowing))
                .isFalse();
            assertThat(liveReadCount)
                .isEqualTo(2);
        }

        @Test
        void readsEveryAskBeforeAnyFrameBoundaryIsSeen() {
            // With nothing to say where a frame begins, an answer kept from an earlier ask could
            // belong to any frame - so it costs a read rather than a stale picture.
            frameRead.isStarscapeMapShowing(readStarscapeShowing);
            frameRead.isStarscapeMapShowing(readStarscapeShowing);

            assertThat(liveReadCount)
                .isEqualTo(2);
        }
    }

    @Nested
    class DisposeMachinery {

        @Test
        void readsAfreshOnceReleased() {

            beginFrame();

            frameRead.isStarscapeMapShowing(readStarscapeShowing);
            frameRead.disposeMachinery();
            frameRead.isStarscapeMapShowing(readStarscapeShowing);

            assertThat(liveReadCount)
                .isEqualTo(2);
        }
    }

    @Nested
    class ResolveReadIn {

        @Test
        void givesOneSectorsSurfacesOneReadBetweenThem() {

            assertThat(StarscapeFrameRead.resolveReadIn(machinery))
                .isSameAs(frameRead);
        }
    }

    // A frame beginning on this sector, the way the engine begins one: through the claim's boundary
    // pass.
    private void beginFrame() {

        MapFramePreparationClaim
            .resolveClaimIn(machinery)
            .renderInUICoordsBelowUI(null);
    }
}
