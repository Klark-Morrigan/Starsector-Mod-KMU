package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlKind;
import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the reusable recede control: the caller's caption then a Mute and a Desaturate checkbox, each
 * lit from the shared preferences and each flipping that preference on a click. The strings and the
 * preference reads/writes are stubbed so this pins the control shape and the toggle wiring alone, not
 * how a string resolves or how a toggle persists.
 */
final class RecedeControlTest {
    // A sample caption the caller supplies, echoed into the first cell; a fixed value so the test
    // depends on no context's real caption.
    private static final String CAPTION_LABEL = "Non-allied factions are";

    // The three cell positions the controls are built in, so a test names the control it inspects
    // rather than reaching for a bare index.
    private static final int CAPTION = 0;
    private static final int MUTE_CHECKBOX = 1;
    private static final int DESATURATE_CHECKBOX = 2;

    @Nested
    class BuildControls {

        @Test
        void buildControlsHeadsWithTheCallerCaptionLabel() {
            try (MockedStatic<RecedePreferences> preferencesMock =
                            mockStatic(RecedePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);

                var caption = RecedeControl.buildControls(CAPTION_LABEL).get(CAPTION);

                // A caption is a text-only LABEL - drawn but never clicked, so it carries no lit cell.
                assertThat(caption.kind()).isEqualTo(ControlKind.LABEL);
                assertThat(caption.labels()).containsExactly(CAPTION_LABEL);
                assertThat(caption.selectedIndex()).isEqualTo(ControlSpec.NO_SELECTION);
            }
        }

        @Test
        void buildControlsPlacesMuteThenDesaturateCheckboxes() {
            // The two toggles read left-to-right: Mute before Desaturate, both checkboxes, so the
            // control reads "<caption> [ ] Muted [ ] Desaturated".
            try (MockedStatic<RecedePreferences> preferencesMock =
                            mockStatic(RecedePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);

                var controls = RecedeControl.buildControls(CAPTION_LABEL);

                assertThat(controls.get(MUTE_CHECKBOX).kind()).isEqualTo(ControlKind.CHECKBOX);
                assertThat(controls.get(MUTE_CHECKBOX).labels()).containsExactly("Muted");
                assertThat(controls.get(DESATURATE_CHECKBOX).kind())
                        .isEqualTo(ControlKind.CHECKBOX);
                assertThat(controls.get(DESATURATE_CHECKBOX).labels()).containsExactly("Desaturated");
            }
        }

        @Test
        void buildControlsLightsTheMuteCheckboxWhenGroundIsMuted() {
            // The checkbox reflects the live toggle, so a save that muted receded ground shows the
            // box ticked (its one cell, index 0, lit) on the next rebuild.
            try (MockedStatic<RecedePreferences> preferencesMock =
                            mockStatic(RecedePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                preferencesMock.when(RecedePreferences::isMuted).thenReturn(true);

                assertThat(RecedeControl.buildControls(CAPTION_LABEL).get(MUTE_CHECKBOX)
                        .selectedIndex()).isEqualTo(0);
            }
        }

        @Test
        void buildControlsLeavesTheMuteCheckboxOffWhenGroundIsNotMuted() {
            try (MockedStatic<RecedePreferences> preferencesMock =
                            mockStatic(RecedePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                preferencesMock.when(RecedePreferences::isMuted).thenReturn(false);

                assertThat(RecedeControl.buildControls(CAPTION_LABEL).get(MUTE_CHECKBOX)
                        .selectedIndex()).isEqualTo(ControlSpec.NO_SELECTION);
            }
        }

        @Test
        void buildControlsLightsTheDesaturateCheckboxWhenGroundIsDesaturated() {
            try (MockedStatic<RecedePreferences> preferencesMock =
                            mockStatic(RecedePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                preferencesMock.when(RecedePreferences::isDesaturated).thenReturn(true);

                assertThat(RecedeControl.buildControls(CAPTION_LABEL).get(DESATURATE_CHECKBOX)
                        .selectedIndex()).isEqualTo(0);
            }
        }

        @Test
        void buildControlsLeavesTheDesaturateCheckboxOffWhenGroundIsNotDesaturated() {
            try (MockedStatic<RecedePreferences> preferencesMock =
                            mockStatic(RecedePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                preferencesMock.when(RecedePreferences::isDesaturated).thenReturn(false);

                assertThat(RecedeControl.buildControls(CAPTION_LABEL).get(DESATURATE_CHECKBOX)
                        .selectedIndex()).isEqualTo(ControlSpec.NO_SELECTION);
            }
        }

        @Test
        void clickingTheMuteCheckboxTurnsMutingOnWhenItIsOff() {
            // A checkbox click flips the toggle, so clicking an unticked Mute box turns muting on.
            try (MockedStatic<RecedePreferences> preferencesMock =
                            mockStatic(RecedePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                preferencesMock.when(RecedePreferences::isMuted).thenReturn(false);

                RecedeControl.buildControls(CAPTION_LABEL).get(MUTE_CHECKBOX).action()
                        .activateCell(0);

                preferencesMock.verify(() -> RecedePreferences.setMuted(true));
            }
        }

        @Test
        void clickingTheMuteCheckboxTurnsMutingOffWhenItIsOn() {
            try (MockedStatic<RecedePreferences> preferencesMock =
                            mockStatic(RecedePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                preferencesMock.when(RecedePreferences::isMuted).thenReturn(true);

                RecedeControl.buildControls(CAPTION_LABEL).get(MUTE_CHECKBOX).action()
                        .activateCell(0);

                preferencesMock.verify(() -> RecedePreferences.setMuted(false));
            }
        }

        @Test
        void clickingTheDesaturateCheckboxTurnsDesaturationOnWhenItIsOff() {
            try (MockedStatic<RecedePreferences> preferencesMock =
                            mockStatic(RecedePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                preferencesMock.when(RecedePreferences::isDesaturated).thenReturn(false);

                RecedeControl.buildControls(CAPTION_LABEL).get(DESATURATE_CHECKBOX).action()
                        .activateCell(0);

                preferencesMock.verify(() -> RecedePreferences.setDesaturated(true));
            }
        }

        @Test
        void clickingTheDesaturateCheckboxTurnsDesaturationOffWhenItIsOn() {
            try (MockedStatic<RecedePreferences> preferencesMock =
                            mockStatic(RecedePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                preferencesMock.when(RecedePreferences::isDesaturated).thenReturn(true);

                RecedeControl.buildControls(CAPTION_LABEL).get(DESATURATE_CHECKBOX).action()
                        .activateCell(0);

                preferencesMock.verify(() -> RecedePreferences.setDesaturated(false));
            }
        }
    }

    // Stubs the two checkbox labels to their plain text so the assertions read the wiring - which key
    // lands in which position - without depending on the live strings table. The caption is the
    // caller's own string, passed in, so it needs no stub.
    private static void stubCheckboxLabels(MockedStatic<KmuStrings> stringsMock) {
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_MUTED))
                .thenReturn("Muted");
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_DESATURATED))
                .thenReturn("Desaturated");
    }
}
