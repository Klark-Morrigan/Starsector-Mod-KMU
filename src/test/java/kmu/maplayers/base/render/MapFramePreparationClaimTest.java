package kmu.maplayers.base.render;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one rule the map surfaces cannot enforce between themselves: a frame's preparation goes
 * to exactly one of them. Each surface decides for itself whether it draws, so nothing on that side
 * can count how many reached the same frame - which is what puts the counting here.
 *
 * <p>The fail-open case carries as much weight as the counting does, and in the opposite direction.
 * Granting every claim costs a duplicated preparation; denying one that should have been granted
 * costs the overlay entirely, since the draw lists are brought up to date nowhere else. So "no frame
 * boundary known" has to read as "prepare" and never as "already prepared", and that is asserted
 * rather than left to the reading of a flag.
 *
 * <p>Driven through the render hook rather than through a seam of its own: opening a frame is what
 * the engine does to this object, so a case that opened one any other way would pin a path nothing
 * takes. The viewport is null throughout - the hook is read purely as a boundary and never draws.
 */
final class MapFramePreparationClaimTest {

    @Nested
    class ClaimPreparation {

        @Test
        void claimPreparationGrantsEveryClaimBeforeAnyFrameBoundaryIsSeen() {
            // The state the surfaces run in until the listener is registered, and the state a load
            // that failed to register it leaves behind. Preparing twice is the tolerable fault here;
            // preparing never would leave the map painting whatever the last prepared frame built.
            var claim = new MapFramePreparationClaim();

            assertThat(claim.claimPreparation())
                .isTrue();
            assertThat(claim.claimPreparation())
                .isTrue();
        }

        @Test
        void claimPreparationGrantsTheFirstSurfaceToReachAFrame() {

            var claim = new MapFramePreparationClaim();

            claim.renderInUICoordsBelowUI(null);

            assertThat(claim.claimPreparation())
                .isTrue();
        }

        @Test
        void claimPreparationRefusesEverySurfaceAfterTheFirstInOneFrame() {
            // The whole point: two surfaces painting the lower band of one frame prepare it once
            // between them, rather than each stepping the cursor's arrival latch for the same frame.
            var claim = new MapFramePreparationClaim();

            claim.renderInUICoordsBelowUI(null);
            claim.claimPreparation();

            assertThat(claim.claimPreparation())
                .isFalse();
            assertThat(claim.claimPreparation())
                .isFalse();
        }

        @Test
        void claimPreparationStaysRefusedThroughTheRenderPassesAboveTheUi() {
            // Which pass is read as the boundary is the whole of what makes this land before the map
            // rather than after it. Both passes above the UI run once the map has already drawn, so
            // releasing the preparation from either would hand it to the next frame's surfaces - the
            // ordering assumption this class exists to stop being made.
            var claim = new MapFramePreparationClaim();

            claim.renderInUICoordsBelowUI(null);
            claim.claimPreparation();
            claim.renderInUICoordsAboveUIBelowTooltips(null);
            claim.renderInUICoordsAboveUIAndTooltips(null);

            assertThat(claim.claimPreparation())
                .isFalse();
        }

        @Test
        void claimPreparationGrantsAgainOnceTheNextFrameOpens() {
            // A claim is spent by the frame it was taken for and no longer, or the overlay would be
            // prepared once and then never again.
            var claim = new MapFramePreparationClaim();

            claim.renderInUICoordsBelowUI(null);
            claim.claimPreparation();
            claim.renderInUICoordsBelowUI(null);

            assertThat(claim.claimPreparation())
                .isTrue();
        }
    }

    @Nested
    class DiscardFrameTrackingFromPreviousSave {

        @Test
        void discardFrameTrackingFromPreviousSaveGrantsEveryClaimAgain() {
            // A load whose registration never happens leaves nothing to open another frame, so the
            // claim has to forget that boundaries were ever seen - otherwise the frame it was left
            // mid-way through denies every preparation for the rest of the session.
            var claim = new MapFramePreparationClaim();

            claim.renderInUICoordsBelowUI(null);
            claim.claimPreparation();
            claim.discardFrameTrackingFromPreviousSave();

            assertThat(claim.claimPreparation())
                .isTrue();
            assertThat(claim.claimPreparation())
                .isTrue();
        }
    }
}
