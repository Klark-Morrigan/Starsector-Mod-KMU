package kmu.maplayers.politicalmap.alliances;

import kmu.maplayers.base.sidebar.SidebarControlKind;
import kmu.maplayers.base.sidebar.SidebarControlSpec;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the alliances view's own body controls: a caption line then a Mute and a Desaturate checkbox,
 * each lit from its per-save toggle and each flipping that toggle on a click. The strings and the
 * preference reads/writes are stubbed so this pins the control shape and the toggle wiring alone, not
 * how a string resolves or how a toggle persists.
 */
final class AllianceBodyControlsTest {
    // The three cell positions the controls are built in, so a test names the control it inspects
    // rather than reaching for a bare index.
    private static final int CAPTION = 0;
    private static final int MUTE_CHECKBOX = 1;
    private static final int DESATURATE_CHECKBOX = 2;

    @Nested
    class BuildControls {

        @Test
        void buildControlsHeadsWithTheNonAlliedCaptionLabel() {
            try (MockedStatic<AllianceStylePreferences> preferencesMock =
                            mockStatic(AllianceStylePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);

                var caption = AllianceBodyControls.buildControls().get(CAPTION);

                // A caption is a text-only LABEL - drawn but never clicked, so it carries no lit cell.
                assertThat(caption.kind()).isEqualTo(SidebarControlKind.LABEL);
                assertThat(caption.labels()).containsExactly("Non-allied factions are");
                assertThat(caption.selectedIndex()).isEqualTo(SidebarControlSpec.NO_SELECTION);
            }
        }

        @Test
        void buildControlsPlacesMuteThenDesaturateCheckboxes() {
            // The two toggles read left-to-right as the mock does: Mute before Desaturate, both
            // checkboxes, so the caption reads "... are [ ] Muted [ ] Desaturated".
            try (MockedStatic<AllianceStylePreferences> preferencesMock =
                            mockStatic(AllianceStylePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);

                var controls = AllianceBodyControls.buildControls();

                assertThat(controls.get(MUTE_CHECKBOX).kind()).isEqualTo(SidebarControlKind.CHECKBOX);
                assertThat(controls.get(MUTE_CHECKBOX).labels()).containsExactly("Muted");
                assertThat(controls.get(DESATURATE_CHECKBOX).kind())
                        .isEqualTo(SidebarControlKind.CHECKBOX);
                assertThat(controls.get(DESATURATE_CHECKBOX).labels()).containsExactly("Desaturated");
            }
        }

        @Test
        void buildControlsLightsTheMuteCheckboxWhenNonAlliedFactionsAreMuted() {
            // The checkbox reflects the live toggle, so a save that muted non-allied factions shows
            // the box ticked (its one cell, index 0, lit) on the next rebuild.
            try (MockedStatic<AllianceStylePreferences> preferencesMock =
                            mockStatic(AllianceStylePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);
                preferencesMock.when(AllianceStylePreferences::isNonAlliedMuted).thenReturn(true);

                assertThat(AllianceBodyControls.buildControls().get(MUTE_CHECKBOX).selectedIndex())
                        .isEqualTo(0);
            }
        }

        @Test
        void buildControlsLeavesTheMuteCheckboxOffWhenNonAlliedFactionsAreNotMuted() {
            try (MockedStatic<AllianceStylePreferences> preferencesMock =
                            mockStatic(AllianceStylePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);
                preferencesMock.when(AllianceStylePreferences::isNonAlliedMuted).thenReturn(false);

                assertThat(AllianceBodyControls.buildControls().get(MUTE_CHECKBOX).selectedIndex())
                        .isEqualTo(SidebarControlSpec.NO_SELECTION);
            }
        }

        @Test
        void buildControlsLightsTheDesaturateCheckboxWhenNonAlliedFactionsAreDesaturated() {
            try (MockedStatic<AllianceStylePreferences> preferencesMock =
                            mockStatic(AllianceStylePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);
                preferencesMock.when(AllianceStylePreferences::isNonAlliedDesaturated)
                        .thenReturn(true);

                assertThat(AllianceBodyControls.buildControls().get(DESATURATE_CHECKBOX)
                        .selectedIndex()).isEqualTo(0);
            }
        }

        @Test
        void buildControlsLeavesTheDesaturateCheckboxOffWhenNonAlliedFactionsAreNotDesaturated() {
            try (MockedStatic<AllianceStylePreferences> preferencesMock =
                            mockStatic(AllianceStylePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);
                preferencesMock.when(AllianceStylePreferences::isNonAlliedDesaturated)
                        .thenReturn(false);

                assertThat(AllianceBodyControls.buildControls().get(DESATURATE_CHECKBOX)
                        .selectedIndex()).isEqualTo(SidebarControlSpec.NO_SELECTION);
            }
        }

        @Test
        void clickingTheMuteCheckboxTurnsMutingOnWhenItIsOff() {
            // A checkbox click flips the toggle, so clicking an unticked Mute box turns muting on.
            try (MockedStatic<AllianceStylePreferences> preferencesMock =
                            mockStatic(AllianceStylePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);
                preferencesMock.when(AllianceStylePreferences::isNonAlliedMuted).thenReturn(false);

                AllianceBodyControls.buildControls().get(MUTE_CHECKBOX).action().activateCell(0);

                preferencesMock.verify(() -> AllianceStylePreferences.setNonAlliedMuted(true));
            }
        }

        @Test
        void clickingTheMuteCheckboxTurnsMutingOffWhenItIsOn() {
            try (MockedStatic<AllianceStylePreferences> preferencesMock =
                            mockStatic(AllianceStylePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);
                preferencesMock.when(AllianceStylePreferences::isNonAlliedMuted).thenReturn(true);

                AllianceBodyControls.buildControls().get(MUTE_CHECKBOX).action().activateCell(0);

                preferencesMock.verify(() -> AllianceStylePreferences.setNonAlliedMuted(false));
            }
        }

        @Test
        void clickingTheDesaturateCheckboxTurnsDesaturationOnWhenItIsOff() {
            try (MockedStatic<AllianceStylePreferences> preferencesMock =
                            mockStatic(AllianceStylePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);
                preferencesMock.when(AllianceStylePreferences::isNonAlliedDesaturated)
                        .thenReturn(false);

                AllianceBodyControls.buildControls().get(DESATURATE_CHECKBOX).action().activateCell(0);

                preferencesMock.verify(() -> AllianceStylePreferences.setNonAlliedDesaturated(true));
            }
        }

        @Test
        void clickingTheDesaturateCheckboxTurnsDesaturationOffWhenItIsOn() {
            try (MockedStatic<AllianceStylePreferences> preferencesMock =
                            mockStatic(AllianceStylePreferences.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);
                preferencesMock.when(AllianceStylePreferences::isNonAlliedDesaturated)
                        .thenReturn(true);

                AllianceBodyControls.buildControls().get(DESATURATE_CHECKBOX).action().activateCell(0);

                preferencesMock.verify(() -> AllianceStylePreferences.setNonAlliedDesaturated(false));
            }
        }
    }

    // Stubs the three control labels to their plain text so the assertions read the wiring - which
    // key lands in which position - without depending on the live strings table.
    private static void stubLabels(MockedStatic<KmuStrings> stringsMock) {
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NON_ALLIED_CAPTION))
                .thenReturn("Non-allied factions are");
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_MUTED))
                .thenReturn("Muted");
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_DESATURATED))
                .thenReturn("Desaturated");
    }
}
