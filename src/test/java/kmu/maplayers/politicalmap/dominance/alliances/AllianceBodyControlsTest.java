package kmu.maplayers.politicalmap.dominance.alliances;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.maplayers.politicalmap.base.sidebar.RecedeControl;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the alliances view's recede adapter: it hands the reusable recede control the view's own
 * non-allied recede set and the {@code Non-allied factions are} caption, then returns exactly what the
 * control builds - so the checkbox shape and toggle wiring stay the shared control's concern while the
 * set and caption are the view's. The strings are stubbed so the caption is pinned by its key, not its
 * live text. The board and the screen it was handed travel through with them, so the flip a checkbox
 * makes lands on the sector whose sidebar the view is drawn in, and on the panel it was placed on.
 */
final class AllianceBodyControlsTest {
    // A sentinel the shared control is stubbed to return, so the test proves the adapter passes it
    // straight through rather than building its own controls.
    private static final List<ControlSpec> BUILT_CONTROLS = List.of();

    // The board the tab hands down, stood in for so the pass-through can be pinned by identity.
    private static final MapLayerRefreshBoard PASSED_BOARD = new MapLayerRefreshBoard();

    // The screen the tab hands down, stood in for so the pass-through can be pinned by value.
    private static final ScreenMemoryScope PASSED_SCREEN = ScreenMemoryScopes.createStandInScreen();

    @Nested
    class BuildControls {

        @Test
        void buildControlsHandsTheNonAlliedSetCaptionBoardAndScreenToTheSharedRecedeControl() {
            try (MockedStatic<RecedeControl> controlMock = mockStatic(RecedeControl.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NON_ALLIED_CAPTION))
                        .thenReturn("Non-allied factions are");
                controlMock.when(() -> RecedeControl.buildControls(
                        RecedePreferences.ALLIANCE_NON_ALLIED,
                        "Non-allied factions are",
                        PASSED_BOARD,
                        PASSED_SCREEN))
                        .thenReturn(BUILT_CONTROLS);

                assertThat(AllianceBodyControls.buildControls(PASSED_BOARD, PASSED_SCREEN))
                        .isSameAs(BUILT_CONTROLS);
            }
        }
    }
}
