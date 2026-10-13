package kmu.maplayers.base.hover.cover;

import kmu.maplayers.base.sidebar.runtime.ScreenClaim;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins which of the claim's two answers the cover reads. Which claimants claim the screen, and how
 * each is read, is the claim's own suite's business; the delegation itself is pinned once for every
 * flag-shaped cover.
 */
final class ScreenClaimMapCoverTests {

    @Nested
    class IsCoveringCursor {

        @Test
        void coversTheMapWhileTheScreenIsClaimed() {

            var screenClaimMock = mock(ScreenClaim.class);

            when(screenClaimMock.isScreenClaimed())
                .thenReturn(true);

            assertThat(new ScreenClaimMapCover(screenClaimMock).isCoveringCursor())
                .isTrue();
        }

        @Test
        void leavesTheMapUncoveredOverAClaimantOnlyFadingOut() {
            // The crisp answer rather than the fade: a dialog dismissed on the press claims nothing
            // from then on, so the map answers the cursor while its box is still dissolving - the
            // same frame the sidebar takes input back.
            var screenClaimMock = mock(ScreenClaim.class);

            when(screenClaimMock.isScreenClaimed())
                .thenReturn(false);
            when(screenClaimMock.resolveClaimStrength())
                .thenReturn(1f);

            assertThat(new ScreenClaimMapCover(screenClaimMock).isCoveringCursor())
                .isFalse();
        }
    }
}
