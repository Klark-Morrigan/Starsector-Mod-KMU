package kmu.maplayers.politicalmap.alliances;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.politicalmap.base.sidebar.RecedeControl;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the alliances view's recede adapter: it hands the shared recede control the {@code Non-allied
 * factions are} caption and returns exactly what the control builds, so the checkbox shape and toggle
 * wiring stay the shared control's concern. The strings are stubbed so the caption is pinned by its
 * key, not its live text.
 */
final class AllianceBodyControlsTest {
    // A sentinel the shared control is stubbed to return, so the test proves the adapter passes it
    // straight through rather than building its own controls.
    private static final List<ControlSpec> BUILT_CONTROLS = List.of();

    @Nested
    class BuildControls {

        @Test
        void buildControlsHandsTheNonAlliedCaptionToTheSharedRecedeControl() {
            try (MockedStatic<RecedeControl> controlMock = mockStatic(RecedeControl.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NON_ALLIED_CAPTION))
                        .thenReturn("Non-allied factions are");
                controlMock.when(() -> RecedeControl.buildControls("Non-allied factions are"))
                        .thenReturn(BUILT_CONTROLS);

                assertThat(AllianceBodyControls.buildControls()).isSameAs(BUILT_CONTROLS);
            }
        }
    }
}
