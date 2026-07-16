package kmu.maplayers.politicalmap.base.render.style;

import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;
import kmu.settings.NeutralColorChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins how the political-map settings fold into one theme: each owned category threads its
 * eight settings into the matching {@link CategoryStyle} slots (a swapped fill/outer/inner or
 * opacity/width would show here), a factionless category collapses to a single outline drawn
 * or hidden by its neutral-color choice, the {@link GlobalStyle} global tier gathers the hatch,
 * smoothing, and desaturation knobs, and {@code readRenderStyle} carries all four categories
 * plus the global tier as one snapshot.
 */
final class RenderStyleReaderTest {

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

                var style = RenderStyleReader.readFactionStyle();

                assertThat(style).isEqualTo(new CategoryStyle(
                        new ElementStyle(FactionPaletteChoice.PRIMARY, FILL_OPACITY),
                        new ElementStyle(FactionPaletteChoice.SECONDARY, OUTER_OPACITY),
                        OUTER_WIDTH,
                        new ElementStyle(FactionPaletteChoice.NONE, INNER_OPACITY),
                        INNER_WIDTH));
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

                var style = RenderStyleReader.readIndependentStyle();

                assertThat(style).isEqualTo(new CategoryStyle(
                        new ElementStyle(FactionPaletteChoice.SECONDARY, FILL_OPACITY),
                        new ElementStyle(FactionPaletteChoice.PRIMARY, OUTER_OPACITY),
                        OUTER_WIDTH,
                        new ElementStyle(FactionPaletteChoice.NONE, INNER_OPACITY),
                        INNER_WIDTH));
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

                var style = RenderStyleReader.readDecivilisedStyle();

                // A factionless category is outline-only: the drawn choice routes to the
                // outer border via a PRIMARY palette slot, with fill and inner seam off.
                assertThat(style).isEqualTo(new CategoryStyle(
                        ElementStyle.NOT_DRAWN,
                        new ElementStyle(FactionPaletteChoice.PRIMARY, NEUTRAL_OPACITY),
                        NEUTRAL_WIDTH,
                        ElementStyle.NOT_DRAWN,
                        0));
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

                var style = RenderStyleReader.readDecivilisedStyle();

                // The "No color" choice turns the outer slot off, yet its opacity and width
                // still pass through so the sole difference from the drawn case is the slot.
                assertThat(style.outer().color()).isEqualTo(FactionPaletteChoice.NONE);
                assertThat(style.outer().opacity()).isEqualTo(NEUTRAL_OPACITY);
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

                var style = RenderStyleReader.readUninhabitedStyle();

                assertThat(style).isEqualTo(new CategoryStyle(
                        ElementStyle.NOT_DRAWN,
                        new ElementStyle(FactionPaletteChoice.PRIMARY, NEUTRAL_OPACITY),
                        NEUTRAL_WIDTH,
                        ElementStyle.NOT_DRAWN,
                        0));
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

                var style = RenderStyleReader.readUninhabitedStyle();

                assertThat(style.outer().color()).isEqualTo(FactionPaletteChoice.NONE);
                assertThat(style.outer().opacity()).isEqualTo(NEUTRAL_OPACITY);
                assertThat(style.outerWidth()).isEqualTo(NEUTRAL_WIDTH);
            }
        }
    }

    @Nested
    class ReadGlobalStyle {

        private static final double HATCH_SPACING = 123.0;
        private static final double HATCH_ANGLE = 0.75;
        private static final double HATCH_WIDTH = 2.5;
        private static final double CORNER_RADIUS = 300.0;
        private static final int CORNER_SEGMENTS = 4;
        private static final double CHAMFER_ANGLE = 0.6;
        private static final double DESATURATION_DARKENING = 0.4;

        @Test
        void readGlobalStyleGathersTheHatchSmoothingAndDesaturationKnobsIntoOneTier() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(KmuLunaSettings::getPoliticalMapHatchSpacing)
                        .thenReturn(HATCH_SPACING);
                settingsMock.when(KmuLunaSettings::getPoliticalMapHatchAngleRadians)
                        .thenReturn(HATCH_ANGLE);
                settingsMock.when(KmuLunaSettings::getPoliticalMapHatchWidth)
                        .thenReturn(HATCH_WIDTH);
                settingsMock.when(KmuLunaSettings::shouldSandBorderSpikes).thenReturn(true);
                settingsMock.when(KmuLunaSettings::shouldRoundBorderCorners).thenReturn(false);
                settingsMock.when(KmuLunaSettings::getPoliticalMapBorderCornerRadius)
                        .thenReturn(CORNER_RADIUS);
                settingsMock.when(KmuLunaSettings::getPoliticalMapBorderCornerSegments)
                        .thenReturn(CORNER_SEGMENTS);
                settingsMock.when(KmuLunaSettings::getPoliticalMapBorderChamferAngleRadians)
                        .thenReturn(CHAMFER_ANGLE);
                settingsMock.when(KmuLunaSettings::getPoliticalMapDesaturationDarkening)
                        .thenReturn(DESATURATION_DARKENING);

                var global = RenderStyleReader.readGlobalStyle();

                assertThat(global.hatch().spacing()).isEqualTo(HATCH_SPACING);
                assertThat(global.hatch().angleRadians()).isEqualTo(HATCH_ANGLE);
                assertThat(global.hatch().width()).isEqualTo(HATCH_WIDTH);
                assertThat(global.borderSmoothing().shouldSandSpikes()).isTrue();
                assertThat(global.borderSmoothing().shouldRoundCorners()).isFalse();
                assertThat(global.borderSmoothing().cornerRadius()).isEqualTo(CORNER_RADIUS);
                assertThat(global.borderSmoothing().cornerSegments()).isEqualTo(CORNER_SEGMENTS);
                assertThat(global.borderSmoothing().chamferAngleRadians()).isEqualTo(CHAMFER_ANGLE);
                assertThat(global.desaturationDarkening()).isEqualTo(DESATURATION_DARKENING);
            }
        }
    }

    @Nested
    class ReadRenderStyle {

        @Test
        void readRenderStyleCarriesTheGlobalTierAndAllFourCategories() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(KmuLunaSettings::getPoliticalMapDesaturationDarkening)
                        .thenReturn(0.3);
                // The two factionless categories dereference their neutral-color choice, so give
                // them a concrete one; every other getter can default since the assertions below
                // only check that each category slot is populated, not its values.
                settingsMock.when(KmuLunaSettings::getDecivilisedBorderColor)
                        .thenReturn(NeutralColorChoice.NEUTRAL);
                settingsMock.when(KmuLunaSettings::getUninhabitedBorderColor)
                        .thenReturn(NeutralColorChoice.NEUTRAL);

                var renderStyle = RenderStyleReader.readRenderStyle();

                assertThat(renderStyle.global()).isNotNull();
                assertThat(renderStyle.categories()).containsOnlyKeys(MapCategory.values());
                for (var category : MapCategory.values()) {
                    assertThat(renderStyle.categoryStyle(category)).isNotNull();
                }
            }
        }
    }
}
