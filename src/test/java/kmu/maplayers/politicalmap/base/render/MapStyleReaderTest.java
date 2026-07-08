package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.politicalmap.base.render.model.MapStyle;
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;
import kmu.settings.NeutralColorChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins how each political-map category's LunaLib settings fold into one {@link MapStyle}
 * bundle: that an owned category threads its eight settings into the matching slots (a
 * swapped fill/outer/inner or opacity/width would show here), and that a factionless
 * category collapses to a single outline - no fill, no seam - drawn or hidden by its
 * neutral-color choice.
 */
final class MapStyleReaderTest {

    // Distinct sentinels so a slot that reads the wrong setting is caught by value, not
    // just by type.
    private static final double FILL_OPACITY = 0.11;
    private static final double OUTER_OPACITY = 0.22;
    private static final double OUTER_WIDTH = 3.3;
    private static final double INNER_OPACITY = 0.44;
    private static final double INNER_WIDTH = 5.5;
    private static final double NEUTRAL_OPACITY = 0.66;
    private static final double NEUTRAL_WIDTH = 7.7;

    @Nested
    class ReadFactionStyle {

        @Test
        void readFactionStyleThreadsEachFactionSettingIntoItsMatchingSlot() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(KmuLunaSettings::getFactionFillColor)
                        .thenReturn(FactionPaletteChoice.PRIMARY);
                settingsMock.when(KmuLunaSettings::getFactionFillOpacity).thenReturn(FILL_OPACITY);
                settingsMock.when(KmuLunaSettings::getFactionOuterBorderColor)
                        .thenReturn(FactionPaletteChoice.SECONDARY);
                settingsMock.when(KmuLunaSettings::getFactionOuterBorderOpacity)
                        .thenReturn(OUTER_OPACITY);
                settingsMock.when(KmuLunaSettings::getFactionOuterBorderWidth)
                        .thenReturn(OUTER_WIDTH);
                settingsMock.when(KmuLunaSettings::getFactionInnerBorderColor)
                        .thenReturn(FactionPaletteChoice.NONE);
                settingsMock.when(KmuLunaSettings::getFactionInnerBorderOpacity)
                        .thenReturn(INNER_OPACITY);
                settingsMock.when(KmuLunaSettings::getFactionInnerBorderWidth)
                        .thenReturn(INNER_WIDTH);

                var style = MapStyleReader.readFactionStyle();

                assertThat(style).isEqualTo(new MapStyle(
                        FactionPaletteChoice.PRIMARY, FILL_OPACITY,
                        FactionPaletteChoice.SECONDARY, OUTER_OPACITY, OUTER_WIDTH,
                        FactionPaletteChoice.NONE, INNER_OPACITY, INNER_WIDTH));
            }
        }
    }

    @Nested
    class ReadIndependentStyle {

        @Test
        void readIndependentStyleThreadsEachIndependentSettingIntoItsMatchingSlot() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(KmuLunaSettings::getIndependentFillColor)
                        .thenReturn(FactionPaletteChoice.SECONDARY);
                settingsMock.when(KmuLunaSettings::getIndependentFillOpacity)
                        .thenReturn(FILL_OPACITY);
                settingsMock.when(KmuLunaSettings::getIndependentOuterBorderColor)
                        .thenReturn(FactionPaletteChoice.PRIMARY);
                settingsMock.when(KmuLunaSettings::getIndependentOuterBorderOpacity)
                        .thenReturn(OUTER_OPACITY);
                settingsMock.when(KmuLunaSettings::getIndependentOuterBorderWidth)
                        .thenReturn(OUTER_WIDTH);
                settingsMock.when(KmuLunaSettings::getIndependentInnerBorderColor)
                        .thenReturn(FactionPaletteChoice.NONE);
                settingsMock.when(KmuLunaSettings::getIndependentInnerBorderOpacity)
                        .thenReturn(INNER_OPACITY);
                settingsMock.when(KmuLunaSettings::getIndependentInnerBorderWidth)
                        .thenReturn(INNER_WIDTH);

                var style = MapStyleReader.readIndependentStyle();

                assertThat(style).isEqualTo(new MapStyle(
                        FactionPaletteChoice.SECONDARY, FILL_OPACITY,
                        FactionPaletteChoice.PRIMARY, OUTER_OPACITY, OUTER_WIDTH,
                        FactionPaletteChoice.NONE, INNER_OPACITY, INNER_WIDTH));
            }
        }
    }

    @Nested
    class ReadDecivilisedStyle {

        @Test
        void readDecivilisedStyleDrawsTheOutlineInTheNeutralColorWhenTheChoiceIsDrawn() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(KmuLunaSettings::getDecivilisedBorderColor)
                        .thenReturn(NeutralColorChoice.NEUTRAL);
                settingsMock.when(KmuLunaSettings::getDecivilisedBorderOpacity)
                        .thenReturn(NEUTRAL_OPACITY);
                settingsMock.when(KmuLunaSettings::getDecivilisedBorderWidth)
                        .thenReturn(NEUTRAL_WIDTH);

                var style = MapStyleReader.readDecivilisedStyle();

                // A factionless category is outline-only: the drawn choice routes to the
                // outer border via a PRIMARY palette slot, with fill and inner seam off.
                assertThat(style).isEqualTo(new MapStyle(
                        FactionPaletteChoice.NONE, 0,
                        FactionPaletteChoice.PRIMARY, NEUTRAL_OPACITY, NEUTRAL_WIDTH,
                        FactionPaletteChoice.NONE, 0, 0));
            }
        }

        @Test
        void readDecivilisedStyleHidesTheOutlineButKeepsItsGeometryWhenTheChoiceIsNone() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(KmuLunaSettings::getDecivilisedBorderColor)
                        .thenReturn(NeutralColorChoice.NONE);
                settingsMock.when(KmuLunaSettings::getDecivilisedBorderOpacity)
                        .thenReturn(NEUTRAL_OPACITY);
                settingsMock.when(KmuLunaSettings::getDecivilisedBorderWidth)
                        .thenReturn(NEUTRAL_WIDTH);

                var style = MapStyleReader.readDecivilisedStyle();

                // The "No color" choice turns the outer slot off, yet its opacity and width
                // still pass through so the sole difference from the drawn case is the slot.
                assertThat(style.outerColor()).isEqualTo(FactionPaletteChoice.NONE);
                assertThat(style.outerOpacity()).isEqualTo(NEUTRAL_OPACITY);
                assertThat(style.outerWidth()).isEqualTo(NEUTRAL_WIDTH);
            }
        }
    }

    @Nested
    class ReadUninhabitedStyle {

        @Test
        void readUninhabitedStyleDrawsTheOutlineInTheNeutralColorWhenTheChoiceIsDrawn() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(KmuLunaSettings::getUninhabitedBorderColor)
                        .thenReturn(NeutralColorChoice.NEUTRAL);
                settingsMock.when(KmuLunaSettings::getUninhabitedBorderOpacity)
                        .thenReturn(NEUTRAL_OPACITY);
                settingsMock.when(KmuLunaSettings::getUninhabitedBorderWidth)
                        .thenReturn(NEUTRAL_WIDTH);

                var style = MapStyleReader.readUninhabitedStyle();

                assertThat(style).isEqualTo(new MapStyle(
                        FactionPaletteChoice.NONE, 0,
                        FactionPaletteChoice.PRIMARY, NEUTRAL_OPACITY, NEUTRAL_WIDTH,
                        FactionPaletteChoice.NONE, 0, 0));
            }
        }

        @Test
        void readUninhabitedStyleHidesTheOutlineButKeepsItsGeometryWhenTheChoiceIsNone() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(KmuLunaSettings::getUninhabitedBorderColor)
                        .thenReturn(NeutralColorChoice.NONE);
                settingsMock.when(KmuLunaSettings::getUninhabitedBorderOpacity)
                        .thenReturn(NEUTRAL_OPACITY);
                settingsMock.when(KmuLunaSettings::getUninhabitedBorderWidth)
                        .thenReturn(NEUTRAL_WIDTH);

                var style = MapStyleReader.readUninhabitedStyle();

                assertThat(style.outerColor()).isEqualTo(FactionPaletteChoice.NONE);
                assertThat(style.outerOpacity()).isEqualTo(NEUTRAL_OPACITY);
                assertThat(style.outerWidth()).isEqualTo(NEUTRAL_WIDTH);
            }
        }
    }
}
