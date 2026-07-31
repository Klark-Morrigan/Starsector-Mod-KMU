package kmu.maplayers.base.labels.anchor.specifications;

import kmu.maplayers.base.geometry.CellShaper;
import kmu.settings.KmuLunaSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the read that turns the live settings into the anchor search's whole tuning surface: every
 * knob lands in the sub-record that owns it, the end-inset multiple is resolved against the
 * border channel here rather than downstream, and the border trace comes from the same source the
 * drawn border uses.
 *
 * <p>Worth pinning because the search takes this value and reads nothing else: a knob wired into
 * the wrong component would move the wrong part of the search with nothing at the call site to
 * catch it.
 */
class LabelAnchorSpecificationTest {
    private static final double END_INSET_MULTIPLE = 2.0;
    private static final double ICON_CLEARANCE = 45.0;
    private static final int DIRECTION_COUNT = 12;
    private static final int OFFSET_COUNT = 5;
    private static final double VERTICAL_PENALTY_STRENGTH = 0.8;
    private static final double VERTICAL_PENALTY_EXPONENT = 1.5;
    private static final double MAX_SLANT_DEGREES = 30.0;
    private static final double MIN_FONT_SIZE = 8.0;
    private static final double MAX_FONT_SIZE = 40.0;
    private static final int MAX_LINES = 3;
    private static final double LINE_SPACING = 1.2;
    private static final double WELD_TOLERANCE = 0.5;
    private static final double MITER_LIMIT = 4.0;

    // Every knob the read touches, given a distinct value so a component wired to the wrong
    // getter shows up as the wrong number rather than as a coincidence.
    private static void stubEveryAnchorSetting(MockedStatic<KmuLunaSettings> settingsMock) {
        settingsMock.when(KmuLunaSettings::getMapAnchorEndInsetMultiple)
            .thenReturn(END_INSET_MULTIPLE);
        settingsMock.when(KmuLunaSettings::getMapAnchorIconClearance)
            .thenReturn(ICON_CLEARANCE);
        settingsMock.when(KmuLunaSettings::getMapAnchorDirectionCount)
            .thenReturn(DIRECTION_COUNT);
        settingsMock.when(KmuLunaSettings::getMapAnchorOffsetCount)
            .thenReturn(OFFSET_COUNT);
        settingsMock.when(KmuLunaSettings::getMapAnchorVerticalPenaltyStrength)
            .thenReturn(VERTICAL_PENALTY_STRENGTH);
        settingsMock.when(KmuLunaSettings::getMapAnchorVerticalPenaltyExponent)
            .thenReturn(VERTICAL_PENALTY_EXPONENT);
        settingsMock.when(KmuLunaSettings::getMapAnchorMaxSlantDegrees)
            .thenReturn(MAX_SLANT_DEGREES);
        settingsMock.when(KmuLunaSettings::getMapShowRejectedAxes).thenReturn(true);
        settingsMock.when(KmuLunaSettings::getMapShowUnbiasedAxes).thenReturn(false);
        settingsMock.when(KmuLunaSettings::getMapNameMinFontSize)
            .thenReturn(MIN_FONT_SIZE);
        settingsMock.when(KmuLunaSettings::getMapNameMaxFontSize)
            .thenReturn(MAX_FONT_SIZE);
        settingsMock.when(KmuLunaSettings::getMapNameMaxLines).thenReturn(MAX_LINES);
        settingsMock.when(KmuLunaSettings::getMapNameLineSpacing)
            .thenReturn(LINE_SPACING);
        settingsMock.when(KmuLunaSettings::getMapBorderWeldTolerance)
            .thenReturn(WELD_TOLERANCE);
        settingsMock.when(KmuLunaSettings::getMapBorderMiterLimit)
            .thenReturn(MITER_LIMIT);
    }

    @Nested
    class ReadFromLunaSettings {

        @Test
        void readFromLunaSettingsResolvesTheEndInsetMultipleAgainstTheBorderChannel() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubEveryAnchorSetting(settingsMock);

                var spec = LabelAnchorSpecification.readFromLunaSettings();

                // Authored as a multiple of the channel and resolved here, so the search itself
                // works in plain distances and never has to know what it was a multiple of.
                assertThat(spec.search().endInsetDistance())
                    .isEqualTo(END_INSET_MULTIPLE * CellShaper.BORDER_INSET_DISTANCE);
            }
        }

        @Test
        void readFromLunaSettingsPutsEachSearchKnobOnTheSearchRecord() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubEveryAnchorSetting(settingsMock);

                var search = LabelAnchorSpecification.readFromLunaSettings().search();

                assertThat(search.iconClearance()).isEqualTo(ICON_CLEARANCE);
                assertThat(search.directionCount()).isEqualTo(DIRECTION_COUNT);
                assertThat(search.offsetCount()).isEqualTo(OFFSET_COUNT);
            }
        }

        @Test
        void readFromLunaSettingsTakesItsBorderTraceFromTheDrawnBorderSettings() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubEveryAnchorSetting(settingsMock);

                var borderTrace = LabelAnchorSpecification.readFromLunaSettings()
                    .search()
                    .borderTrace();

                // The same two trace parameters the border renders with, so a name is clipped
                // against the rings the player actually sees.
                assertThat(borderTrace.weldTolerance()).isEqualTo(WELD_TOLERANCE);
                assertThat(borderTrace.miterSpikeLimit()).isEqualTo(MITER_LIMIT);
            }
        }

        @Test
        void readFromLunaSettingsPutsEachLeanKnobOnTheScoringRecord() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubEveryAnchorSetting(settingsMock);

                var scoring = LabelAnchorSpecification.readFromLunaSettings().scoring();

                assertThat(scoring.verticalPenaltyStrength())
                    .isEqualTo(VERTICAL_PENALTY_STRENGTH);
                assertThat(scoring.verticalPenaltyExponent())
                    .isEqualTo(VERTICAL_PENALTY_EXPONENT);
                assertThat(scoring.maxSlantDegrees())
                    .isEqualTo(MAX_SLANT_DEGREES);
            }
        }

        @Test
        void readFromLunaSettingsCarriesTheTwoDiagnosticTogglesSeparately() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubEveryAnchorSetting(settingsMock);

                var diagnostics = LabelAnchorSpecification.readFromLunaSettings().diagnostics();

                // Stubbed opposite ways round, so a read that crossed the two would fail rather
                // than agree with itself.
                assertThat(diagnostics.showRejectedAxis()).isTrue();
                assertThat(diagnostics.showUnbiasedAxis()).isFalse();
            }
        }

        @Test
        void readFromLunaSettingsPutsEachNameKnobOnTheNameFitRecord() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubEveryAnchorSetting(settingsMock);

                var nameFit = LabelAnchorSpecification.readFromLunaSettings().nameFit();

                assertThat(nameFit.minFontHeight()).isEqualTo(MIN_FONT_SIZE);
                assertThat(nameFit.maxFontHeight()).isEqualTo(MAX_FONT_SIZE);
                assertThat(nameFit.maxLines()).isEqualTo(MAX_LINES);
                assertThat(nameFit.lineSpacing()).isEqualTo(LINE_SPACING);
            }
        }
    }
}
