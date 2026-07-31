package kmu.maplayers.politicalmap.base.render.style;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.politicalmap.base.UninhabitedOutlinePreference;
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins how the political-map settings fold into one theme: each owned category threads its
 * eight settings into the matching {@link CategoryStyle} slots (a swapped fill/outer/inner or
 * opacity/width would show here), a factionless category collapses to neutral-colour elements
 * with no colour choice - decivilised ground a fill plus an outline, uninhabited ground an
 * outline whose on/off is the sidebar toggle - the {@link GlobalStyle} global tier gathers the
 * hatch, smoothing, and desaturation knobs, and {@code readRenderStyle} carries all four
 * categories plus the global tier as one snapshot.
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
    private static final double NEUTRAL_FILL_OPACITY = 0.88;

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
                        new ElementStyle(FactionPaletteShade.resolveElementPaintOf(FactionPaletteChoice.PRIMARY), FILL_OPACITY),
                        new ElementStyle(FactionPaletteShade.resolveElementPaintOf(FactionPaletteChoice.SECONDARY), OUTER_OPACITY),
                        OUTER_WIDTH,
                        new ElementStyle(FactionPaletteShade.resolveElementPaintOf(FactionPaletteChoice.NONE), INNER_OPACITY),
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
                        new ElementStyle(FactionPaletteShade.resolveElementPaintOf(FactionPaletteChoice.SECONDARY), FILL_OPACITY),
                        new ElementStyle(FactionPaletteShade.resolveElementPaintOf(FactionPaletteChoice.PRIMARY), OUTER_OPACITY),
                        OUTER_WIDTH,
                        new ElementStyle(FactionPaletteShade.resolveElementPaintOf(FactionPaletteChoice.NONE), INNER_OPACITY),
                        INNER_WIDTH));
            }
        }
    }

    @Nested
    class ReadDecivilisedStyle {

        @Test
        void readDecivilisedStyleDrawsTheFillAndOutlineInTheNeutralColor() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(KmuLunaSettings::getDecivilisedFillOpacity)
                        .thenReturn(NEUTRAL_FILL_OPACITY);
                settingsMock.when(KmuLunaSettings::getDecivilisedBorderOpacity)
                        .thenReturn(NEUTRAL_OPACITY);
                settingsMock.when(KmuLunaSettings::getDecivilisedBorderWidth)
                        .thenReturn(NEUTRAL_WIDTH);

                var style = RenderStyleReader.readDecivilisedStyle();

                // Both drawn elements route through a PRIMARY palette slot (which resolves to
                // the neutral colour for factionless ground), with only the inner seam off -
                // and each takes its own opacity, so a swapped pair would show as a value swap.
                assertThat(style).isEqualTo(new CategoryStyle(
                        new ElementStyle(FactionPaletteShade.resolveElementPaintOf(FactionPaletteChoice.PRIMARY), NEUTRAL_FILL_OPACITY),
                        new ElementStyle(FactionPaletteShade.resolveElementPaintOf(FactionPaletteChoice.PRIMARY), NEUTRAL_OPACITY),
                        NEUTRAL_WIDTH,
                        ElementStyle.NOT_DRAWN,
                        0));
            }
        }

        @Test
        void readDecivilisedStyleLeavesTheFillUndrawnAtZeroOpacity() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(KmuLunaSettings::getDecivilisedFillOpacity).thenReturn(0.0);
                settingsMock.when(KmuLunaSettings::getDecivilisedBorderOpacity)
                        .thenReturn(NEUTRAL_OPACITY);
                settingsMock.when(KmuLunaSettings::getDecivilisedBorderWidth)
                        .thenReturn(NEUTRAL_WIDTH);

                var style = RenderStyleReader.readDecivilisedStyle();

                // The fill opacity is the fill's only on/off - factionless ground has no colour
                // choice to turn off - so zero has to read as "not drawn" all the way down.
                assertThat(style.fill().isDrawn()).isFalse();
                assertThat(style.outer().isDrawn()).isTrue();
            }
        }
    }

    @Nested
    class ReadUninhabitedStyle {

        @Test
        void readUninhabitedStyleDrawsTheOutlineInTheNeutralColorWhenTheSidebarToggleIsOn() {
            // The on/off comes from the sidebar preference rather than a settings field, so this
            // category reads two sources; both are stubbed so neither can satisfy the assertion
            // alone.
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class);
                    MockedStatic<UninhabitedOutlinePreference> preferenceMock =
                            mockStatic(UninhabitedOutlinePreference.class)) {
                preferenceMock.when(UninhabitedOutlinePreference::isOutlineDrawn).thenReturn(true);
                settingsMock.when(KmuLunaSettings::getUninhabitedBorderOpacity)
                        .thenReturn(NEUTRAL_OPACITY);
                settingsMock.when(KmuLunaSettings::getUninhabitedBorderWidth)
                        .thenReturn(NEUTRAL_WIDTH);

                var style = RenderStyleReader.readUninhabitedStyle();

                assertThat(style).isEqualTo(new CategoryStyle(
                        ElementStyle.NOT_DRAWN,
                        new ElementStyle(FactionPaletteShade.resolveElementPaintOf(FactionPaletteChoice.PRIMARY), NEUTRAL_OPACITY),
                        NEUTRAL_WIDTH,
                        ElementStyle.NOT_DRAWN,
                        0));
            }
        }

        @Test
        void readUninhabitedStyleHidesTheOutlineButKeepsItsGeometryWhenTheSidebarToggleIsOff() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class);
                    MockedStatic<UninhabitedOutlinePreference> preferenceMock =
                            mockStatic(UninhabitedOutlinePreference.class)) {
                preferenceMock.when(UninhabitedOutlinePreference::isOutlineDrawn).thenReturn(false);
                settingsMock.when(KmuLunaSettings::getUninhabitedBorderOpacity)
                        .thenReturn(NEUTRAL_OPACITY);
                settingsMock.when(KmuLunaSettings::getUninhabitedBorderWidth)
                        .thenReturn(NEUTRAL_WIDTH);

                var style = RenderStyleReader.readUninhabitedStyle();

                // The outer slot turns off, yet its opacity and width still pass through so the
                // sole difference from the drawn case is the slot. Off reaches the theme as an
                // absent selection rather than as a named "No color", since the tier reads
                // absence as "paints nothing" without knowing this map's option set.
                assertThat(style.outer().color()).isNull();
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
        // One distinctive hover knob, enough to prove the tier carries the hover style it
        // gathers; ReadHoverHighlightStyle pins the rest of that bundle's threading.
        private static final double HOVER_GLOW_OPACITY = 0.65;

        @Test
        void readGlobalStyleGathersEverySectorWideKnobIntoOneTier() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(KmuLunaSettings::getPoliticalMapHoverGlowOpacity)
                        .thenReturn(HOVER_GLOW_OPACITY);
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
                assertThat(global.hoverHighlight().glow().opacity()).isEqualTo(HOVER_GLOW_OPACITY);
            }
        }
    }

    @Nested
    class ReadBorderSmoothingStyle {

        private static final double SPIKE_HEIGHT = 12.0;
        private static final double SPIKE_ANGLE = 1.1;
        private static final double CORNER_RADIUS = 250.0;
        private static final int CORNER_SEGMENTS = 6;
        private static final double CHAMFER_ANGLE = 0.4;

        @Test
        void readBorderSmoothingStyleCarriesBothPassesShapeAndNotOnlyTheGates() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(KmuLunaSettings::shouldSandBorderSpikes).thenReturn(true);
                settingsMock.when(KmuLunaSettings::shouldRoundBorderCorners).thenReturn(true);
                settingsMock.when(KmuLunaSettings::getPoliticalMapBorderSpikeHeight)
                        .thenReturn(SPIKE_HEIGHT);
                settingsMock.when(KmuLunaSettings::getPoliticalMapBorderSpikeAngleRadians)
                        .thenReturn(SPIKE_ANGLE);
                settingsMock.when(KmuLunaSettings::getPoliticalMapBorderCornerRadius)
                        .thenReturn(CORNER_RADIUS);
                settingsMock.when(KmuLunaSettings::getPoliticalMapBorderCornerSegments)
                        .thenReturn(CORNER_SEGMENTS);
                settingsMock.when(KmuLunaSettings::getPoliticalMapBorderChamferAngleRadians)
                        .thenReturn(CHAMFER_ANGLE);

                var smoothing = RenderStyleReader.readBorderSmoothingStyle();

                // The sanding shape belongs on the profile alongside the rounding shape: it is
                // what lets the passes work from one value rather than each reading its own half.
                assertThat(smoothing.shouldSandSpikes()).isTrue();
                assertThat(smoothing.shouldRoundCorners()).isTrue();
                assertThat(smoothing.spikeHeight()).isEqualTo(SPIKE_HEIGHT);
                assertThat(smoothing.spikeAngleRadians()).isEqualTo(SPIKE_ANGLE);
                assertThat(smoothing.cornerRadius()).isEqualTo(CORNER_RADIUS);
                assertThat(smoothing.cornerSegments()).isEqualTo(CORNER_SEGMENTS);
                assertThat(smoothing.chamferAngleRadians()).isEqualTo(CHAMFER_ANGLE);
            }
        }
    }

    @Nested
    class ReadHoverHighlightStyle {

        // Distinct values per knob, so a getter wired into the wrong slot reads as a swap
        // rather than matching by luck.
        private static final double GLOW_OPACITY = 0.65;
        private static final double GLOW_WIDTH = 17.0;
        private static final int GLOW_LAYERS = 6;
        private static final double GLOW_PULSE_STRENGTH = 0.4;
        private static final double GLOW_PULSE_PERIOD = 2.5;
        private static final double WASH_OPACITY = 0.3;
        private static final double WASH_OUTLINE_OPACITY = 0.85;
        private static final double WASH_OUTLINE_WIDTH = 3.5;

        @Test
        void readHoverHighlightStyleThreadsEachHoverSettingIntoItsMatchingSlot() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(KmuLunaSettings::getPoliticalMapHoverHighlightColor)
                        .thenReturn(FactionPaletteChoice.SECONDARY);
                settingsMock.when(KmuLunaSettings::getPoliticalMapHoverGlowOpacity)
                        .thenReturn(GLOW_OPACITY);
                settingsMock.when(KmuLunaSettings::getPoliticalMapHoverGlowWidth)
                        .thenReturn(GLOW_WIDTH);
                settingsMock.when(KmuLunaSettings::getPoliticalMapHoverGlowLayers)
                        .thenReturn(GLOW_LAYERS);
                settingsMock.when(KmuLunaSettings::getPoliticalMapHoverGlowPulseStrength)
                        .thenReturn(GLOW_PULSE_STRENGTH);
                settingsMock.when(KmuLunaSettings::getPoliticalMapHoverGlowPulsePeriod)
                        .thenReturn(GLOW_PULSE_PERIOD);
                settingsMock.when(KmuLunaSettings::getPoliticalMapHoverWashOpacity)
                        .thenReturn(WASH_OPACITY);
                settingsMock.when(KmuLunaSettings::getPoliticalMapHoverWashOutlineOpacity)
                        .thenReturn(WASH_OUTLINE_OPACITY);
                settingsMock.when(KmuLunaSettings::getPoliticalMapHoverWashOutlineWidth)
                        .thenReturn(WASH_OUTLINE_WIDTH);

                var hover = RenderStyleReader.readHoverHighlightStyle();

                assertThat(hover.color()).isEqualTo(FactionPaletteShade.SECONDARY);
                assertThat(hover.glow().opacity()).isEqualTo(GLOW_OPACITY);
                assertThat(hover.glow().width()).isEqualTo(GLOW_WIDTH);
                assertThat(hover.glow().layers()).isEqualTo(GLOW_LAYERS);
                assertThat(hover.glow().pulseStrength()).isEqualTo(GLOW_PULSE_STRENGTH);
                assertThat(hover.glow().pulsePeriodSeconds()).isEqualTo(GLOW_PULSE_PERIOD);
                assertThat(hover.wash().fillOpacity()).isEqualTo(WASH_OPACITY);
                assertThat(hover.wash().outlineOpacity()).isEqualTo(WASH_OUTLINE_OPACITY);
                assertThat(hover.wash().outlineWidth()).isEqualTo(WASH_OUTLINE_WIDTH);
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
                // Every category getter can default here: the assertions below only check that
                // each category slot is populated, not its values. The uninhabited category's
                // toggle reads sector memory, which is absent here and resolves to off.

                var renderStyle = RenderStyleReader.readRenderStyle();

                assertThat(renderStyle.global()).isNotNull();
                assertThat(renderStyle.categories())
                        .containsOnlyKeys(PoliticalMapCategory.values());
                for (var category : PoliticalMapCategory.values()) {
                    assertThat(renderStyle.categoryStyle(category)).isNotNull();
                }
            }
        }
    }
}
