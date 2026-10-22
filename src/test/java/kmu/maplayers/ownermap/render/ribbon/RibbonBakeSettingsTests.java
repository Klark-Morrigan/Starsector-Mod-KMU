package kmu.maplayers.ownermap.render.ribbon;

import kmlib.testfixtures.statics.StaticSeams;

import kmu.maplayers.ownermap.ribbon.RibbonSegmentLengths;
import kmu.settings.KmuOwnerMapDiagnosticsSettings;
import kmu.settings.KmuOwnerMapRibbonSettings;
import kmu.settings.RibbonNameClearanceChoice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that each band knob lands in the slot it names, and that the mitre limit stays the code's
 * own rather than becoming a knob beside them.
 *
 * <p>Worth pinning by value because the sizes are interchangeable by type: a width read into the
 * pad, or the two run lengths handed over the wrong way round, compiles clean and shows only as a
 * band that looks slightly off in play - and "slightly off" is indistinguishable from a slider set
 * somewhere odd. Distinct sentinels per knob turn that into a failing test.
 *
 * <p>Every switch is answered the opposite way to how it ships, so a reader hard-wiring the shipped
 * answer instead of asking for the player's shows here rather than only on a map nobody is
 * looking at.
 */
final class RibbonBakeSettingsTests {

    // Distinct per knob so a slot reading its neighbour's setting is caught by value.
    private static final double WIDTH_WORLD = 111.0;
    private static final double INSET_PAD_WORLD = 22.0;
    private static final int SEGMENT_LENGTH_UNITS = 7;
    private static final int INTERJECTION_LENGTH_UNITS = 4;

    // Each switch and the one choice read as the opposite of what they ship as.
    private static final boolean RIBBONS_DRAWN = false;
    private static final boolean BAND_ALWAYS_DRAWN = false;
    private static final boolean UNCONTESTED_RUNS_SHORTENED = false;
    private static final boolean RIBBONS_KEPT_CLEAR_OF_NAMES = false;
    private static final boolean RIBBON_PATHS_SHOWN = true;
    private static final RibbonNameClearanceChoice NAME_CLEARANCE = RibbonNameClearanceChoice.FITTED_BOX;

    // The authored mitre limit, which is no knob: it is the angle past which a corner's mitre
    // becomes a spike, a property of stroking rather than of how the readout looks.
    private static final double AUTHORED_MITER_SPIKE_LIMIT = 2.0;

    // The classes this arrangement stands in for, closed on the way out.
    private final StaticSeams seams = new StaticSeams();

    @BeforeEach
    void stubBandKnobs() {

        var settingsMock = seams.openSeam(KmuOwnerMapRibbonSettings.class);

        settingsMock
            .when(KmuOwnerMapRibbonSettings::shouldDrawOwnerMapRibbons)
            .thenReturn(RIBBONS_DRAWN);
        settingsMock
            .when(KmuOwnerMapRibbonSettings::getOwnerMapRibbonWidth)
            .thenReturn(WIDTH_WORLD);
        settingsMock
            .when(KmuOwnerMapRibbonSettings::getOwnerMapRibbonInsetPad)
            .thenReturn(INSET_PAD_WORLD);
        settingsMock
            .when(KmuOwnerMapRibbonSettings::getOwnerMapRibbonSegmentLength)
            .thenReturn(SEGMENT_LENGTH_UNITS);
        settingsMock
            .when(KmuOwnerMapRibbonSettings::getOwnerMapRibbonInterjectionLength)
            .thenReturn(INTERJECTION_LENGTH_UNITS);
        settingsMock
            .when(KmuOwnerMapRibbonSettings::shouldAlwaysDrawOwnerMapRibbons)
            .thenReturn(BAND_ALWAYS_DRAWN);
        settingsMock
            .when(KmuOwnerMapRibbonSettings::shouldShortenOwnerMapUncontestedRibbonRuns)
            .thenReturn(UNCONTESTED_RUNS_SHORTENED);
        settingsMock
            .when(KmuOwnerMapRibbonSettings::shouldKeepOwnerMapRibbonsClearOfNames)
            .thenReturn(RIBBONS_KEPT_CLEAR_OF_NAMES);
        settingsMock
            .when(KmuOwnerMapRibbonSettings::getOwnerMapRibbonNameClearance)
            .thenReturn(NAME_CLEARANCE);

        seams
            .openSeam(KmuOwnerMapDiagnosticsSettings.class)
            .when(KmuOwnerMapDiagnosticsSettings::shouldShowOwnerMapRibbonPaths)
            .thenReturn(RIBBON_PATHS_SHOWN);
    }

    @AfterEach
    void closeSeams() {
        seams.closeEverySeam();
    }

    @Nested
    class ReadFromLunaSettings {

        @Test
        void threadsEachSizeKnobIntoItsMatchingSize() {

            // ARRANGE
            var expectedWidthWorld = 111.0;
            var expectedInsetPadWorld = 22.0;
            var expectedIsBandAlwaysDrawn = false;

            // ACT
            var actualStyle = RibbonBakeSettings
                .readFromLunaSettings()
                .ribbonStyle();

            // ASSERT
            assertThat(actualStyle.widthWorld())
                .isEqualTo(expectedWidthWorld);
            assertThat(actualStyle.insetPadWorld())
                .isEqualTo(expectedInsetPadWorld);
            assertThat(actualStyle.isBandAlwaysDrawn())
                .isEqualTo(expectedIsBandAlwaysDrawn);
        }

        @Test
        void keepsTheMitreLimitAuthored() {

            // ARRANGE
            var expectedMiterSpikeLimit = AUTHORED_MITER_SPIKE_LIMIT;

            // ACT
            var actualMiterSpikeLimit = RibbonBakeSettings
                .readFromLunaSettings()
                .ribbonStyle()
                .miterSpikeLimit();

            // ASSERT
            assertThat(actualMiterSpikeLimit)
                .isEqualTo(expectedMiterSpikeLimit);
        }

        // The shortening is answered off, so an uncontested cell keeps the authored pair: a reader
        // that ignored the switch and shortened anyway would cut the stake run to one width.
        @Test
        void laysTheRunsAtTheAuthoredLengthsWhileTheShorteningIsOff() {

            // ARRANGE
            var expectedContestedLengths = new RibbonSegmentLengths(7, 4);
            var expectedUncontestedLengths = new RibbonSegmentLengths(7, 4);

            // ACT
            var actualPlanRules = RibbonBakeSettings
                .readFromLunaSettings()
                .planRules();

            // ASSERT
            assertThat(actualPlanRules.getContestedLengths())
                .isEqualTo(expectedContestedLengths);
            assertThat(actualPlanRules.getUncontestedLengths())
                .isEqualTo(expectedUncontestedLengths);
        }

        @Test
        void threadsEachSwitchIntoItsMatchingAnswer() {

            // ARRANGE
            var expectedAreRibbonsDrawn = false;
            var expectedIsRibbonKeptClearOfNames = false;
            var expectedNameClearance = RibbonNameClearanceChoice.FITTED_BOX;
            var expectedIsRibbonPathShown = true;

            // ACT
            var actualSettings = RibbonBakeSettings.readFromLunaSettings();

            // ASSERT
            assertThat(actualSettings.areRibbonsDrawn())
                .isEqualTo(expectedAreRibbonsDrawn);
            assertThat(actualSettings.isRibbonKeptClearOfNames())
                .isEqualTo(expectedIsRibbonKeptClearOfNames);
            assertThat(actualSettings.nameClearance())
                .isEqualTo(expectedNameClearance);
            assertThat(actualSettings.isRibbonPathShown())
                .isEqualTo(expectedIsRibbonPathShown);
        }
    }
}
