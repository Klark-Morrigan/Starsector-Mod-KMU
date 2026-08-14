package kmu.maplayers.politicalmap.dominance.alliances;

import kmlib.starsector.ui.controls.ControlSpec;

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
 * live text.
 */
final class AllianceBodyControlsTest {
    // A sentinel the shared control is stubbed to return, so the test proves the adapter passes it
    // straight through rather than building its own controls.
    private static final List<ControlSpec> BUILT_CONTROLS = List.of();

    @Nested
    class BuildControls {

        @Test
        void buildControlsHandsTheNonAlliedSetAndCaptionToTheSharedRecedeControl() {
            try (MockedStatic<RecedeControl> controlMock = mockStatic(RecedeControl.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NON_ALLIED_CAPTION))
                        .thenReturn("Non-allied factions are");
                controlMock.when(() -> RecedeControl.buildControls(
                        RecedePreferences.ALLIANCE_NON_ALLIED, "Non-allied factions are"))
                        .thenReturn(BUILT_CONTROLS);

                assertThat(AllianceBodyControls.buildControls()).isSameAs(BUILT_CONTROLS);
            }
        }
    }
}
