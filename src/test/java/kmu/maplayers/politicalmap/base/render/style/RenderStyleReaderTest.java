package kmu.maplayers.politicalmap.base.render.style;

import kmlib.opengl.GlLineQuality;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.GlLineHatchStroke;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.base.theme.HoverGlowStyle;
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuPoliticalMapGeometrySettings;
import kmu.settings.KmuPoliticalMapHighlightSettings;
import kmu.settings.KmuPoliticalMapTerritorySettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins how the political-map settings fold into one theme: each owned category threads its
 * eight settings into the matching {@link CategoryStyle} slots (a swapped fill/outer/inner or
 * opacity/width would show here), a factionless category collapses to neutral-colour elements
 * with no colour choice - a decivilised cell a fill plus an outline, an uninhabited cell an
 * outline whose on/off is the sidebar toggle - the {@link GlobalStyle} global tier gathers the
 * hatch, smoothing, desaturation and both highlight tiers, and {@code readRenderStyle} carries all
 * four categories plus the global tier as one snapshot.
 *
 * <p>The two highlight tiers are the same record read out of two sets of getters, so every
 * stand-in below is distinct across both: a slot reading its counterpart in the other tier then
 * arrives unstubbed and fails by value, which is the only thing that tells the two apart.
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

            try (var territorySettingsMock = mockStatic(KmuPoliticalMapTerritorySettings.class)) {

                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getFactionFillColour)
                    .thenReturn(FactionPaletteChoice.PRIMARY);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getFactionFillOpacity)
                    .thenReturn(FILL_OPACITY);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getFactionOuterBorderColour)
                    .thenReturn(FactionPaletteChoice.SECONDARY);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getFactionOuterBorderOpacity)
                    .thenReturn(OUTER_OPACITY);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getFactionOuterBorderWidth)
                    .thenReturn(OUTER_WIDTH);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getFactionInnerBorderColour)
                    .thenReturn(FactionPaletteChoice.NONE);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getFactionInnerBorderOpacity)
                    .thenReturn(INNER_OPACITY);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getFactionInnerBorderWidth)
                    .thenReturn(INNER_WIDTH);

                var style = RenderStyleReader.readFactionStyle();

                assertThat(style)
                    .isEqualTo(new CategoryStyle(
                        new ElementStyle(
                            FactionPaletteSlot.PRIMARY,
                            FILL_OPACITY),
                        new ElementStyle(
                            FactionPaletteSlot.SECONDARY,
                            OUTER_OPACITY),
                        OUTER_WIDTH,
                        new ElementStyle(
                            null,
                            INNER_OPACITY),
                        INNER_WIDTH));
            }
        }
    }

    @Nested
    class ReadIndependentStyle {

        @Test
        void readIndependentStyleThreadsEachIndependentSettingIntoItsMatchingSlot() {

            try (var territorySettingsMock = mockStatic(KmuPoliticalMapTerritorySettings.class)) {

                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getIndependentFillColour)
                    .thenReturn(FactionPaletteChoice.SECONDARY);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getIndependentFillOpacity)
                    .thenReturn(FILL_OPACITY);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getIndependentOuterBorderColour)
                    .thenReturn(FactionPaletteChoice.PRIMARY);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getIndependentOuterBorderOpacity)
                    .thenReturn(OUTER_OPACITY);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getIndependentOuterBorderWidth)
                    .thenReturn(OUTER_WIDTH);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getIndependentInnerBorderColour)
                    .thenReturn(FactionPaletteChoice.NONE);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getIndependentInnerBorderOpacity)
                    .thenReturn(INNER_OPACITY);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getIndependentInnerBorderWidth)
                    .thenReturn(INNER_WIDTH);

                var style = RenderStyleReader.readIndependentStyle();

                assertThat(style)
                    .isEqualTo(new CategoryStyle(
                        new ElementStyle(
                            FactionPaletteSlot.SECONDARY,
                            FILL_OPACITY),
                        new ElementStyle(
                            FactionPaletteSlot.PRIMARY,
                            OUTER_OPACITY),
                        OUTER_WIDTH,
                        new ElementStyle(
                            null,
                            INNER_OPACITY),
                        INNER_WIDTH));
            }
        }
    }

    @Nested
    class ReadDecivilisedStyle {

        @Test
        void readDecivilisedStyleDrawsTheFillAndOutlineInTheNeutralColour() {

            try (var territorySettingsMock = mockStatic(KmuPoliticalMapTerritorySettings.class)) {

                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getDecivilisedFillOpacity)
                    .thenReturn(NEUTRAL_FILL_OPACITY);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getDecivilisedBorderOpacity)
                    .thenReturn(NEUTRAL_OPACITY);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getDecivilisedBorderWidth)
                    .thenReturn(NEUTRAL_WIDTH);

                var style = RenderStyleReader.readDecivilisedStyle();

                // Both drawn elements route through a PRIMARY palette slot (which resolves to
                // the neutral colour for a factionless cell), with only the inner seam off -
                // and each takes its own opacity, so a swapped pair would show as a value swap.
                assertThat(style)
                    .isEqualTo(new CategoryStyle(
                        new ElementStyle(
                            FactionPaletteSlot.PRIMARY,
                            NEUTRAL_FILL_OPACITY),
                        new ElementStyle(
                            FactionPaletteSlot.PRIMARY,
                            NEUTRAL_OPACITY),
                        NEUTRAL_WIDTH,
                        ElementStyle.NOT_DRAWN,
                        0));
            }
        }

        @Test
        void readDecivilisedStyleLeavesTheFillUndrawnAtZeroOpacity() {

            try (var territorySettingsMock = mockStatic(KmuPoliticalMapTerritorySettings.class)) {

                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getDecivilisedFillOpacity)
                    .thenReturn(0.0);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getDecivilisedBorderOpacity)
                    .thenReturn(NEUTRAL_OPACITY);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getDecivilisedBorderWidth)
                    .thenReturn(NEUTRAL_WIDTH);

                var style = RenderStyleReader.readDecivilisedStyle();

                // The fill opacity is the fill's only on/off - a factionless cell has no colour
                // choice to turn off - so zero has to read as "not drawn" all the way down.
                assertThat(style.fill().isDrawn())
                    .isFalse();
                assertThat(style.outer().isDrawn())
                    .isTrue();
            }
        }
    }

    @Nested
    class ReadUninhabitedStyle {

        @Test
        void readUninhabitedStyleDrawsTheOutlineInTheNeutralColourWhenTheSidebarToggleIsOn() {
            // The on/off arrives from the rebuild's own sampling of the sidebar preference rather
            // than from a settings field, so this category is settled by the argument and the two
            // stubbed knobs together and neither can satisfy the assertion alone.
            try (var territorySettingsMock = mockStatic(KmuPoliticalMapTerritorySettings.class)) {

                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getUninhabitedBorderOpacity)
                    .thenReturn(NEUTRAL_OPACITY);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getUninhabitedBorderWidth)
                    .thenReturn(NEUTRAL_WIDTH);

                var style = RenderStyleReader.readUninhabitedStyle(true);

                assertThat(style)
                    .isEqualTo(new CategoryStyle(
                        ElementStyle.NOT_DRAWN,
                        new ElementStyle(
                            FactionPaletteSlot.PRIMARY,
                            NEUTRAL_OPACITY
                        ),
                        NEUTRAL_WIDTH,
                        ElementStyle.NOT_DRAWN,
                        0));
            }
        }

        @Test
        void readUninhabitedStyleHidesTheOutlineButKeepsItsGeometryWhenTheSidebarToggleIsOff() {

            try (var territorySettingsMock = mockStatic(KmuPoliticalMapTerritorySettings.class)) {

                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getUninhabitedBorderOpacity)
                    .thenReturn(NEUTRAL_OPACITY);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getUninhabitedBorderWidth)
                    .thenReturn(NEUTRAL_WIDTH);

                var style = RenderStyleReader.readUninhabitedStyle(false);

                // The outer slot turns off, yet its opacity and width still pass through so the
                // sole difference from the drawn case is the slot. Off reaches the theme as an
                // absent selection rather than as a named "No color", since the tier reads
                // absence as "paints nothing" without knowing this map's option set.
                assertThat(style.outer().colour())
                    .isNull();
                assertThat(style.outer().opacity())
                    .isEqualTo(NEUTRAL_OPACITY);
                assertThat(style.outerWidth())
                    .isEqualTo(NEUTRAL_WIDTH);
            }
        }
    }

    @Nested
    class ReadGlobalStyle {

        private static final double HATCH_SPACING = 123.0;
        private static final double HATCH_ANGLE = 0.75;
        private static final double HATCH_WIDTH = 2.5;
        private static final double HATCH_JOIN_TOLERANCE = 0.002;
        private static final double CORNER_RADIUS = 300.0;
        private static final int CORNER_SEGMENTS = 4;
        private static final double CHAMFER_ANGLE = 0.6;
        private static final double DESATURATION_DARKENING = 0.4;
        private static final double PRESENCE_LIGHTENING = 0.6;

        // One distinctive knob from each highlight tier, enough to prove the global tier carries
        // both styles it gathers and carries them the right way round; ReadHoverHighlightStyle and
        // ReadPreviewHighlightStyle pin the rest of each bundle's threading.
        private static final double HOVER_GLOW_OPACITY = 0.65;
        private static final double PREVIEW_WASH_OPACITY = 0.45;

        @Test
        void readGlobalStyleGathersEverySectorWideKnobIntoOneTier() {

            try (var geometrySettingsMock = mockStatic(KmuPoliticalMapGeometrySettings.class);
                    var highlightSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class);
                    var territorySettingsMock = mockStatic(KmuPoliticalMapTerritorySettings.class)) {

                highlightSettingsMock
                    .when(KmuPoliticalMapHighlightSettings::getPoliticalMapHoverGlowOpacity)
                    .thenReturn(HOVER_GLOW_OPACITY);
                highlightSettingsMock
                    .when(KmuPoliticalMapHighlightSettings::getPoliticalMapPreviewWashOpacity)
                    .thenReturn(PREVIEW_WASH_OPACITY);

                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::getPoliticalMapHatchSpacing)
                    .thenReturn(HATCH_SPACING);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::getPoliticalMapHatchAngleRadians)
                    .thenReturn(HATCH_ANGLE);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::getPoliticalMapHatchWidth)
                    .thenReturn(HATCH_WIDTH);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::getPoliticalMapHatchJoinToleranceFraction)
                    .thenReturn(HATCH_JOIN_TOLERANCE);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::shouldSmoothHatchLines)
                    .thenReturn(false);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::shouldSandBorderSpikes)
                    .thenReturn(true);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::shouldRoundBorderCorners)
                    .thenReturn(false);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::getPoliticalMapBorderCornerRadius)
                    .thenReturn(CORNER_RADIUS);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::getPoliticalMapBorderCornerSegments)
                    .thenReturn(CORNER_SEGMENTS);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::getPoliticalMapBorderChamferAngleRadians)
                    .thenReturn(CHAMFER_ANGLE);

                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getPoliticalMapDesaturationDarkening)
                    .thenReturn(DESATURATION_DARKENING);
                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getPoliticalMapPresenceLightening)
                    .thenReturn(PRESENCE_LIGHTENING);

                var global = RenderStyleReader.readGlobalStyle();

                assertThat(global.hatch().spacing())
                    .isEqualTo(HATCH_SPACING);
                assertThat(global.hatch().angleRadians())
                    .isEqualTo(HATCH_ANGLE);

                assertThat(global.hatch().joinToleranceFraction())
                    .isEqualTo(HATCH_JOIN_TOLERANCE);

                // Both halves of the stroke at once, since the width the player set is only the
                // stroke they asked for if it arrives at the quality they set as well.
                assertThat(global.hatch().stroke())
                    .isEqualTo(new GlLineHatchStroke(GlLineQuality.ALIASED, HATCH_WIDTH));

                var cornerRounding = global.borderSmoothing().cornerRounding();

                assertThat(global.borderSmoothing().spikeSanding().shouldSandSpikes())
                    .isTrue();

                assertThat(cornerRounding.shouldRoundCorners())
                    .isFalse();
                assertThat(cornerRounding.cornerRadius())
                    .isEqualTo(CORNER_RADIUS);
                assertThat(cornerRounding.cornerSegments())
                    .isEqualTo(CORNER_SEGMENTS);
                assertThat(cornerRounding.chamferAngleRadians())
                    .isEqualTo(CHAMFER_ANGLE);

                assertThat(global.desaturationDarkening())
                    .isEqualTo(DESATURATION_DARKENING);

                // Distinct from the darkening beside it: the two are adjacent doubles on the
                // record, so equal stand-ins would let a transposed pair read as correct.
                assertThat(global.presenceLightening())
                    .isEqualTo(PRESENCE_LIGHTENING);
                assertThat(global.hoverHighlight().glow().opacity())
                    .isEqualTo(HOVER_GLOW_OPACITY);

                // Distinct from the cursor tier's for the reason the two strengths above are
                // distinct from each other: the two tiers are the same record in adjacent slots,
                // so equal stand-ins would let a transposed pair read as correct. Read off the
                // wash rather than the halo, the preview tier carrying no halo of its own.
                assertThat(global.previewHighlight().wash().fillOpacity())
                    .isEqualTo(PREVIEW_WASH_OPACITY);
            }
        }

        // The switched-on arm of the same crossing. Its own case rather than a second assertion
        // above, because the mapping is the whole of what this reads: a stroke that came back
        // aliased whatever the player set would pass every other assertion in this class.
        @Test
        void readGlobalStyleStrokesTheHatchSmoothedWhenTheSmoothingKnobIsOn() {
            // The tier gathers three sections, so the two this case says nothing about are still
            // opened: a settings read outside a mock has no LunaLib to answer it.
            try (var geometrySettingsMock = mockStatic(KmuPoliticalMapGeometrySettings.class);
                    var highlightSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class);
                    var territorySettingsMock = mockStatic(KmuPoliticalMapTerritorySettings.class)) {

                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::shouldSmoothHatchLines)
                    .thenReturn(true);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::getPoliticalMapHatchWidth)
                    .thenReturn(HATCH_WIDTH);

                var global = RenderStyleReader.readGlobalStyle();

                assertThat(global.hatch().stroke())
                    .isEqualTo(new GlLineHatchStroke(GlLineQuality.SMOOTHED, HATCH_WIDTH));
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
        void readBorderSmoothingStyleLandsEachKnobInTheSubRecordOfThePassThatReadsIt() {

            try (var geometrySettingsMock = mockStatic(KmuPoliticalMapGeometrySettings.class)) {

                // The two gates differ, so a gate threaded into the other pass's sub-record reads
                // as a swap rather than as two switches that happen to agree.
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::shouldSandBorderSpikes)
                    .thenReturn(true);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::shouldRoundBorderCorners)
                    .thenReturn(false);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::getPoliticalMapBorderSpikeHeight)
                    .thenReturn(SPIKE_HEIGHT);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::getPoliticalMapBorderSpikeAngleRadians)
                    .thenReturn(SPIKE_ANGLE);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::getPoliticalMapBorderCornerRadius)
                    .thenReturn(CORNER_RADIUS);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::getPoliticalMapBorderCornerSegments)
                    .thenReturn(CORNER_SEGMENTS);
                geometrySettingsMock
                    .when(KmuPoliticalMapGeometrySettings::getPoliticalMapBorderChamferAngleRadians)
                    .thenReturn(CHAMFER_ANGLE);

                var smoothing = RenderStyleReader.readBorderSmoothingStyle();

                // Both passes' shape belongs on one profile - that is what lets them work from a
                // single value - but each knob belongs to the half of it the pass that reads the
                // knob is handed. The split is where a field could now be threaded into the wrong
                // sub-record and still compile, so both halves are named here.
                assertThat(smoothing.spikeSanding().shouldSandSpikes())
                    .isTrue();
                assertThat(smoothing.spikeSanding().spikeHeight())
                    .isEqualTo(SPIKE_HEIGHT);
                assertThat(smoothing.spikeSanding().spikeAngleRadians())
                    .isEqualTo(SPIKE_ANGLE);

                assertThat(smoothing.cornerRounding().shouldRoundCorners())
                    .isFalse();
                assertThat(smoothing.cornerRounding().cornerRadius())
                    .isEqualTo(CORNER_RADIUS);
                assertThat(smoothing.cornerRounding().cornerSegments())
                    .isEqualTo(CORNER_SEGMENTS);
                assertThat(smoothing.cornerRounding().chamferAngleRadians())
                    .isEqualTo(CHAMFER_ANGLE);
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

            try (var highlightSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                highlightSettingsMock
                    .when(KmuPoliticalMapHighlightSettings::getPoliticalMapHoverHighlightColour)
                    .thenReturn(FactionPaletteChoice.SECONDARY);
                highlightSettingsMock
                    .when(KmuPoliticalMapHighlightSettings::getPoliticalMapHoverGlowOpacity)
                    .thenReturn(GLOW_OPACITY);
                highlightSettingsMock
                    .when(KmuPoliticalMapHighlightSettings::getPoliticalMapHoverGlowWidth)
                    .thenReturn(GLOW_WIDTH);
                highlightSettingsMock
                    .when(KmuPoliticalMapHighlightSettings::getPoliticalMapHoverGlowLayers)
                    .thenReturn(GLOW_LAYERS);
                highlightSettingsMock
                    .when(KmuPoliticalMapHighlightSettings::getPoliticalMapHoverGlowPulseStrength)
                    .thenReturn(GLOW_PULSE_STRENGTH);
                highlightSettingsMock
                    .when(KmuPoliticalMapHighlightSettings::getPoliticalMapHoverGlowPulsePeriod)
                    .thenReturn(GLOW_PULSE_PERIOD);
                highlightSettingsMock
                    .when(KmuPoliticalMapHighlightSettings::getPoliticalMapHoverWashOpacity)
                    .thenReturn(WASH_OPACITY);
                highlightSettingsMock
                    .when(KmuPoliticalMapHighlightSettings::getPoliticalMapHoverWashOutlineOpacity)
                    .thenReturn(WASH_OUTLINE_OPACITY);
                highlightSettingsMock
                    .when(KmuPoliticalMapHighlightSettings::getPoliticalMapHoverWashOutlineWidth)
                    .thenReturn(WASH_OUTLINE_WIDTH);

                var hover = RenderStyleReader.readHoverHighlightStyle();

                assertThat(hover.colour())
                    .isEqualTo(FactionPaletteSlot.SECONDARY);

                assertThat(hover.glow().opacity())
                    .isEqualTo(GLOW_OPACITY);
                assertThat(hover.glow().width())
                    .isEqualTo(GLOW_WIDTH);
                assertThat(hover.glow().layers())
                    .isEqualTo(GLOW_LAYERS);
                assertThat(hover.glow().pulseStrength())
                    .isEqualTo(GLOW_PULSE_STRENGTH);
                assertThat(hover.glow().pulsePeriodSeconds())
                    .isEqualTo(GLOW_PULSE_PERIOD);

                assertThat(hover.wash().fillOpacity())
                    .isEqualTo(WASH_OPACITY);
                assertThat(hover.wash().outlineOpacity())
                    .isEqualTo(WASH_OUTLINE_OPACITY);
                assertThat(hover.wash().outlineWidth())
                    .isEqualTo(WASH_OUTLINE_WIDTH);
            }
        }
    }

    @Nested
    class ReadPreviewHighlightStyle {

        // Distinct from the cursor tier's stand-in for the same knob, so a preview slot fed by its
        // cursor counterpart fails by value.
        private static final double WASH_OPACITY = 0.45;

        @Test
        void readPreviewHighlightStyleThreadsEachPreviewSettingIntoItsMatchingSlot() {

            try (var highlightSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                highlightSettingsMock
                    .when(KmuPoliticalMapHighlightSettings::getPoliticalMapPreviewHighlightColour)
                    .thenReturn(FactionPaletteChoice.PRIMARY);
                highlightSettingsMock
                    .when(KmuPoliticalMapHighlightSettings::getPoliticalMapPreviewWashOpacity)
                    .thenReturn(WASH_OPACITY);

                var preview = RenderStyleReader.readPreviewHighlightStyle();

                assertThat(preview.colour())
                    .isEqualTo(FactionPaletteSlot.PRIMARY);

                assertThat(preview.wash().fillOpacity())
                    .isEqualTo(WASH_OPACITY);
            }
        }

        // The tier's two fixed weights, asserted against literals rather than against the reader's
        // own constants: a value the player can no longer move is one a test has to pin, or a
        // future edit to it passes unnoticed.
        @Test
        void readPreviewHighlightStyleTracesLitRegionsAtItsOwnFixedWeights() {

            try (var highlightSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                var preview = RenderStyleReader.readPreviewHighlightStyle();

                assertThat(preview.wash().outlineOpacity())
                    .isEqualTo(0.9);
                assertThat(preview.wash().outlineWidth())
                    .isEqualTo(0.5);
            }
        }

        // The halo the tier declines. Its own case rather than an assertion beside the weights,
        // since "draws no halo" is a decision about the tier rather than a weight it carries.
        @Test
        void readPreviewHighlightStyleCarriesNoHalo() {

            try (var highlightSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                var preview = RenderStyleReader.readPreviewHighlightStyle();

                assertThat(preview.glow())
                    .isEqualTo(HoverGlowStyle.NO_GLOW);
            }
        }
    }

    @Nested
    class ReadRenderStyle {

        @Test
        void readRenderStyleCarriesTheGlobalTierAndAllFourCategories() {

            try (var territorySettingsMock = mockStatic(KmuPoliticalMapTerritorySettings.class);
                    var geometrySettingsMock = mockStatic(KmuPoliticalMapGeometrySettings.class);
                    var highlightSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                territorySettingsMock
                    .when(KmuPoliticalMapTerritorySettings::getPoliticalMapDesaturationDarkening)
                    .thenReturn(0.3);

                // Every category getter can default here: the assertions below only check that
                // each category slot is populated, not its values, so the uninhabited category's
                // outline pick is stated as off. The two mocks nothing stubs stand in for the
                // section classes the whole-theme read also reaches, a settings read outside them
                // having no LunaLib to answer it.
                var renderStyle = RenderStyleReader.readRenderStyle(false);

                assertThat(renderStyle.global())
                    .isNotNull();
                assertThat(renderStyle.categories())
                    .containsOnlyKeys(PoliticalMapCategory.values());

                for (var category : PoliticalMapCategory.values()) {
                    assertThat(renderStyle.categoryStyle(category))
                        .isNotNull();
                }
            }
        }
    }
}
