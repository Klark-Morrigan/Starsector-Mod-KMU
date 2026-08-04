package kmu.maplayers.politicalmap.base.sidebar;

import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.starsector.StarsectorSettingsFake;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the reusable recede control: the caller's caption then a Mute and a Desaturate checkbox, each
 * lit from the passed preferences set and each flipping that set on a click. The strings are stubbed
 * and the preferences set is a mock, so this pins the control shape and the toggle wiring alone - not
 * how a string resolves or how a set persists - and proves the control drives whichever set it is
 * handed rather than a fixed one.
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

    // The engine tone the control's labels carry, stood in for so the controls can be built without
    // the live palette in reach.
    private static final Color TEXT = Color.LIGHT_GRAY;

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void installColours() {
        // Settings first, then the Misc statics: Misc's class initialiser reads the settings, so
        // mocking it against an uninstalled settings proxy would fail on class load.
        StarsectorSettingsFake.installSettings();

        miscMock = Mockito.mockStatic(Misc.class);
        miscMock
            .when(Misc::getTextColor)
            .thenReturn(TEXT);
    }

    @AfterEach
    void clearColours() {
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class BuildControls {

        @Test
        void buildControlsHeadsWithTheCallerCaptionLabel() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                var preferencesMock = mock(RecedePreferences.class);

                var caption = RecedeControl.buildControls(preferencesMock, CAPTION_LABEL).get(CAPTION);

                // A caption is a text-only Label - drawn but never clicked, so it is not Interactive and
                // carries no lit cell at all.
                assertThat(caption).isInstanceOf(ControlSpec.Label.class);
                assertThat(caption.labels()).containsExactly(CAPTION_LABEL);
            }
        }

        @Test
        void buildControlsPlacesMuteThenDesaturateCheckboxes() {
            // The two toggles read left-to-right: Mute before Desaturate, both checkboxes, so the
            // control reads "<caption> [ ] Muted [ ] Desaturated".
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                var preferencesMock = mock(RecedePreferences.class);

                var controls = RecedeControl.buildControls(preferencesMock, CAPTION_LABEL);

                assertThat(controls.get(MUTE_CHECKBOX)).isInstanceOf(ControlSpec.Checkbox.class);
                assertThat(controls.get(MUTE_CHECKBOX).labels()).containsExactly("Muted");
                assertThat(controls.get(DESATURATE_CHECKBOX)).isInstanceOf(ControlSpec.Checkbox.class);
                assertThat(controls.get(DESATURATE_CHECKBOX).labels()).containsExactly("Desaturated");
            }
        }

        @Test
        void buildControlsLightsTheMuteCheckboxWhenTheBackdropIsMuted() {
            // The checkbox reflects the passed set's live toggle, so a set that muted its receded
            // backdrop shows the box ticked (its one cell, index 0, lit) on the next rebuild.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                var preferencesMock = mock(RecedePreferences.class);
                when(preferencesMock.isMuted()).thenReturn(true);

                assertThat(interactiveAt(preferencesMock, MUTE_CHECKBOX).selectedIndex()).isEqualTo(0);
            }
        }

        @Test
        void buildControlsLeavesTheMuteCheckboxOffWhenTheBackdropIsNotMuted() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                var preferencesMock = mock(RecedePreferences.class);
                when(preferencesMock.isMuted()).thenReturn(false);

                assertThat(interactiveAt(preferencesMock, MUTE_CHECKBOX).selectedIndex())
                        .isEqualTo(ControlSpec.NO_SELECTION);
            }
        }

        @Test
        void buildControlsLightsTheDesaturateCheckboxWhenTheBackdropIsDesaturated() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                var preferencesMock = mock(RecedePreferences.class);
                when(preferencesMock.isDesaturated()).thenReturn(true);

                assertThat(interactiveAt(preferencesMock, DESATURATE_CHECKBOX).selectedIndex())
                        .isEqualTo(0);
            }
        }

        @Test
        void buildControlsLeavesTheDesaturateCheckboxOffWhenTheBackdropIsNotDesaturated() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                var preferencesMock = mock(RecedePreferences.class);
                when(preferencesMock.isDesaturated()).thenReturn(false);

                assertThat(interactiveAt(preferencesMock, DESATURATE_CHECKBOX).selectedIndex())
                        .isEqualTo(ControlSpec.NO_SELECTION);
            }
        }

        @Test
        void clickingTheMuteCheckboxTurnsMutingOnWhenItIsOff() {
            // A checkbox click flips the passed set's toggle, so clicking an unticked Mute box turns
            // muting on for that set.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                var preferencesMock = mock(RecedePreferences.class);
                when(preferencesMock.isMuted()).thenReturn(false);

                interactiveAt(preferencesMock, MUTE_CHECKBOX).action().activateCell(0);

                verify(preferencesMock).setMuted(true);
            }
        }

        @Test
        void clickingTheMuteCheckboxTurnsMutingOffWhenItIsOn() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                var preferencesMock = mock(RecedePreferences.class);
                when(preferencesMock.isMuted()).thenReturn(true);

                interactiveAt(preferencesMock, MUTE_CHECKBOX).action().activateCell(0);

                verify(preferencesMock).setMuted(false);
            }
        }

        @Test
        void clickingTheDesaturateCheckboxTurnsDesaturationOnWhenItIsOff() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                var preferencesMock = mock(RecedePreferences.class);
                when(preferencesMock.isDesaturated()).thenReturn(false);

                interactiveAt(preferencesMock, DESATURATE_CHECKBOX).action().activateCell(0);

                verify(preferencesMock).setDesaturated(true);
            }
        }

        @Test
        void clickingTheDesaturateCheckboxTurnsDesaturationOffWhenItIsOn() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCheckboxLabels(stringsMock);
                var preferencesMock = mock(RecedePreferences.class);
                when(preferencesMock.isDesaturated()).thenReturn(true);

                interactiveAt(preferencesMock, DESATURATE_CHECKBOX).action().activateCell(0);

                verify(preferencesMock).setDesaturated(false);
            }
        }
    }

    // The control at index, built over the given set and read as the Interactive control it is - a
    // caption is chrome and not Interactive, so only the checkboxes below it expose the lit cell and
    // click action a test drives. Rebuilt fresh each call, so a test reads the state its stubs set.
    private static ControlSpec.Interactive interactiveAt(RecedePreferences preferences, int index) {
        return (ControlSpec.Interactive) RecedeControl.buildControls(preferences, CAPTION_LABEL)
                .get(index);
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
