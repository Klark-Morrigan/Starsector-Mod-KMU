package kmu.maplayers.politicalmap.views;

import kmlib.starsector.ui.controls.specs.ControlSpec;

import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.ownermap.sidebar.BodyControlTarget;
import kmu.maplayers.ownermap.sidebar.RecedeControl;
import kmu.util.KmuStringKeys;

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

    // The panel the tab hands down, stood in for so the pass-through can be pinned by value.
    private static final BodyControlTarget PASSED_TARGET =
        new BodyControlTarget(PASSED_BOARD, ScreenMemoryScopes.createStandInScreen());

    @Nested
    class BuildControls {

        @Test
        void buildControlsHandsTheNonAlliedSetCaptionAndPanelToTheSharedRecedeControl() {
            try (MockedStatic<RecedeControl> controlMock = mockStatic(RecedeControl.class);
                    MockedStatic<KmuStringKeys> stringsMock = mockStatic(KmuStringKeys.class)) {
                stringsMock.when(() -> KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_CTL_NON_ALLIED_CAPTION))
                        .thenReturn("Non-allied factions are");
                controlMock.when(() -> RecedeControl.buildControls(
                        AlliancesView.NON_ALLIED_RECEDE,
                        "Non-allied factions are",
                        PASSED_TARGET))
                        .thenReturn(BUILT_CONTROLS);

                assertThat(AllianceBodyControls.buildControls(PASSED_TARGET))
                        .isSameAs(BUILT_CONTROLS);
            }
        }
    }
}
