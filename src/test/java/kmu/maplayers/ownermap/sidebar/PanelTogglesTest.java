package kmu.maplayers.ownermap.sidebar;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.ownermap.preferences.UninhabitedOutlinePreference;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the flip every sidebar checkbox makes: the preference is read on the panel the box was placed
 * on and the opposite is written back there, raising on that panel's board. The preference is a mock,
 * so the read-then-write wiring is what is asserted rather than how the choice persists.
 */
final class PanelTogglesTest {

    // The board and screen the checkbox was placed on, held by identity: what each case pins is that
    // these exact two reach the write.
    private static final MapLayerRefreshBoard BUILT_BOARD = new MapLayerRefreshBoard();
    private static final ScreenMemoryScope BUILT_SCREEN = ScreenMemoryScopes.createStandInScreen();
    private static final BodyControlTarget BUILT_TARGET =
        new BodyControlTarget(BUILT_BOARD, BUILT_SCREEN);

    // Any on/off preference will do; the outline stands for all of them.
    private final UninhabitedOutlinePreference outlineMock = mock(UninhabitedOutlinePreference.class);

    @Nested
    class FlipToggle {

        @Test
        void flipToggleTurnsOffAPreferenceThatIsOnThere() {

            when(outlineMock.isOutlineDrawn(BUILT_SCREEN))
                .thenReturn(true);

            PanelToggles.flipToggle(
                BUILT_TARGET,
                outlineMock::isOutlineDrawn,
                outlineMock::setOutlineDrawn);

            verify(outlineMock).setOutlineDrawn(BUILT_SCREEN, false, BUILT_BOARD);
        }

        @Test
        void flipToggleTurnsOnAPreferenceThatIsOffThere() {

            when(outlineMock.isOutlineDrawn(BUILT_SCREEN))
                .thenReturn(false);

            PanelToggles.flipToggle(
                BUILT_TARGET,
                outlineMock::isOutlineDrawn,
                outlineMock::setOutlineDrawn);

            verify(outlineMock).setOutlineDrawn(BUILT_SCREEN, true, BUILT_BOARD);
        }
    }
}
