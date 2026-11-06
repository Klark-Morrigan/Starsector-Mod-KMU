package kmu.maplayers.base.render;

import kmu.maplayers.base.machinery.SectorMapMachinery;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that each presence answer is read once per frame for every surface of one sector, against the
 * very boundary that frame's preparation is claimed on - that, with no boundary seen, every ask reads
 * afresh rather than answering off a frame nobody saw begin, and that the two questions are settled
 * apart.
 */
final class MapPresenceFrameReadTests {

    private final SectorMapMachinery machinery = new SectorMapMachinery(null);
    private final MapPresenceFrameRead frameRead = MapPresenceFrameRead.resolveReadIn(machinery);

    // What each live read answers, mutable so a case can move it between asks, and how often each
    // was asked.
    private boolean isVanillaMapDrawn = true;
    private boolean isStarscapeMapDrawn = true;
    private int drawnReadCount;
    private int starscapeReadCount;

    private final BooleanSupplier readVanillaMapDrawn = () -> {

        drawnReadCount++;
        return isVanillaMapDrawn;
    };

    private final BooleanSupplier readStarscapeMapDrawn = () -> {

        starscapeReadCount++;
        return isStarscapeMapDrawn;
    };

    @Nested
    class IsVanillaMapDrawn {

        // The warning and the render gate of every surface ask, and the answer cannot move between them.
        @Test
        void readsOnceForEveryAskOfOneFrame() {

            // ARRANGE
            beginFrame();

            // ACT
            frameRead.isVanillaMapDrawn(readVanillaMapDrawn);
            frameRead.isVanillaMapDrawn(readVanillaMapDrawn);
            frameRead.isVanillaMapDrawn(readVanillaMapDrawn);

            // ASSERT
            assertThat(drawnReadCount)
                .isEqualTo(1);
        }

        @Test
        void answersWhatTheFramesReadAnswered() {

            // ARRANGE
            beginFrame();
            frameRead.isVanillaMapDrawn(readVanillaMapDrawn);

            isVanillaMapDrawn = false;

            // ACT
            var actualDrawn = frameRead.isVanillaMapDrawn(readVanillaMapDrawn);

            // ASSERT
            assertThat(actualDrawn)
                .isTrue();
        }

        // The player can close the map between frames, so a frame's answer is that frame's.
        @Test
        void readsAfreshOnTheNextFrame() {

            // ARRANGE
            beginFrame();
            frameRead.isVanillaMapDrawn(readVanillaMapDrawn);

            isVanillaMapDrawn = false;
            beginFrame();

            // ACT
            var actualDrawn = frameRead.isVanillaMapDrawn(readVanillaMapDrawn);

            // ASSERT
            assertThat(actualDrawn)
                .isFalse();
            assertThat(drawnReadCount)
                .isEqualTo(2);
        }

        // With nothing to say where a frame begins, an answer kept from an earlier ask could
        // belong to any frame - so it costs a read rather than a stale picture.
        @Test
        void readsEveryAskBeforeAnyFrameBoundaryIsSeen() {

            // ARRANGE
            Runnable askTwice = () -> {
                frameRead.isVanillaMapDrawn(readVanillaMapDrawn);
                frameRead.isVanillaMapDrawn(readVanillaMapDrawn);
            };

            // ACT
            askTwice.run();

            // ASSERT
            assertThat(drawnReadCount)
                .isEqualTo(2);
        }

        // The two questions share a frame and nothing else: settling one must not answer the other.
        @Test
        void leavesTheStarscapeAnswerUnsettled() {

            // ARRANGE
            beginFrame();
            frameRead.isVanillaMapDrawn(readVanillaMapDrawn);

            // ACT
            frameRead.isStarscapeMapDrawn(readStarscapeMapDrawn);

            // ASSERT
            assertThat(starscapeReadCount)
                .isEqualTo(1);
        }
    }

    @Nested
    class IsStarscapeMapDrawn {

        // Both Starscape surfaces ask before drawing, and the answer cannot move between them.
        @Test
        void readsOnceForEverySurfaceOfOneFrame() {

            // ARRANGE
            beginFrame();

            // ACT
            frameRead.isStarscapeMapDrawn(readStarscapeMapDrawn);
            frameRead.isStarscapeMapDrawn(readStarscapeMapDrawn);

            // ASSERT
            assertThat(starscapeReadCount)
                .isEqualTo(1);
        }

        // The player can switch the filter between frames, so a frame's answer is that frame's.
        @Test
        void readsAfreshOnTheNextFrame() {

            // ARRANGE
            beginFrame();
            frameRead.isStarscapeMapDrawn(readStarscapeMapDrawn);

            isStarscapeMapDrawn = false;
            beginFrame();

            // ACT
            var actualDrawn = frameRead.isStarscapeMapDrawn(readStarscapeMapDrawn);

            // ASSERT
            assertThat(actualDrawn)
                .isFalse();
        }

        @Test
        void readsEveryAskBeforeAnyFrameBoundaryIsSeen() {

            // ARRANGE
            Runnable askTwice = () -> {
                frameRead.isStarscapeMapDrawn(readStarscapeMapDrawn);
                frameRead.isStarscapeMapDrawn(readStarscapeMapDrawn);
            };

            // ACT
            askTwice.run();

            // ASSERT
            assertThat(starscapeReadCount)
                .isEqualTo(2);
        }
    }

    @Nested
    class DisposeMachinery {

        @Test
        void readsBothAfreshOnceReleased() {

            // ARRANGE
            beginFrame();
            frameRead.isVanillaMapDrawn(readVanillaMapDrawn);
            frameRead.isStarscapeMapDrawn(readStarscapeMapDrawn);

            // ACT
            frameRead.disposeMachinery();

            frameRead.isVanillaMapDrawn(readVanillaMapDrawn);
            frameRead.isStarscapeMapDrawn(readStarscapeMapDrawn);

            // ASSERT
            assertThat(drawnReadCount)
                .isEqualTo(2);
            assertThat(starscapeReadCount)
                .isEqualTo(2);
        }
    }

    @Nested
    class ResolveReadIn {

        @Test
        void givesOneSectorsSurfacesOneReadBetweenThem() {

            // ARRANGE
            var expectedRead = frameRead;

            // ACT
            var actualRead = MapPresenceFrameRead.resolveReadIn(machinery);

            // ASSERT
            assertThat(actualRead)
                .isSameAs(expectedRead);
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
