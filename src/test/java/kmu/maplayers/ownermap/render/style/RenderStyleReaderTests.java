package kmu.maplayers.ownermap.render.style;

import kmlib.opengl.GlLineQuality;
import kmlib.opengl.hatch.GlLineHatchStroke;
import kmlib.opengl.hatch.HatchPattern;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.base.theme.HoverGlowStyle;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;
import kmu.settings.KmuOwnerMapGeometrySettings;
import kmu.settings.KmuOwnerMapHighlightSettings;
import kmu.settings.KmuOwnerMapStyleSettings;
import kmu.settings.OwnerPaletteChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static kmu.maplayers.ownermap.ContentInputsFixtures.createInputsSpellingNames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins how the owner-map settings fold into one theme: each owned category threads its
 * eight settings into the matching {@link CategoryStyle} slots (a swapped fill/outer/inner or
 * opacity/width would show here), an unowned category collapses to neutral-colour elements
 * with no colour choice - a decivilised cell a fill plus an outline, an uninhabited cell an
 * outline whose on/off is the sidebar toggle - the {@link GlobalStyle} global tier gathers the
 * hatch, smoothing, desaturation and both highlight tiers, and {@code readRenderStyle} carries the
 * categories the painting layer declares plus the global tier as one snapshot.
 *
 * <p>The two highlight tiers are the same record read out of two sets of getters, so every
 * stand-in below is distinct across both: a slot reading its counterpart in the other tier then
 * arrives unstubbed and fails by value, which is the only thing that tells the two apart.
 */
final class RenderStyleReaderTests {

    // Distinct sentinels so a slot that reads the wrong setting is caught by value, not
    // just by type.
    private static final double FILL_OPACITY = 0.11;
    private static final double OUTER_WIDTH = 3.3;
    private static final double INNER_WIDTH = 5.5;

    @Nested
    class ReadGlobalStyle {

        private static final double HATCH_SPACING = 123.0;
        private static final double HATCH_ANGLE = 0.75;
        private static final double HATCH_WIDTH_FRACTION = 0.25;
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
        void gathersEverySectorWideKnobIntoOneTier() {

            // ARRANGE
            try (var geometrySettingsMock = mockStatic(KmuOwnerMapGeometrySettings.class);
                    var highlightSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class);
                    var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                highlightSettingsMock
                    .when(KmuOwnerMapHighlightSettings::getOwnerMapHoverGlowOpacity)
                    .thenReturn(HOVER_GLOW_OPACITY);
                highlightSettingsMock
                    .when(KmuOwnerMapHighlightSettings::getOwnerMapPreviewWashOpacity)
                    .thenReturn(PREVIEW_WASH_OPACITY);

                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::getOwnerMapHatchSpacing)
                    .thenReturn(HATCH_SPACING);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::getOwnerMapHatchAngleRadians)
                    .thenReturn(HATCH_ANGLE);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::getOwnerMapHatchWidthFraction)
                    .thenReturn(HATCH_WIDTH_FRACTION);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::getOwnerMapHatchJoinToleranceFraction)
                    .thenReturn(HATCH_JOIN_TOLERANCE);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::shouldSmoothHatchLines)
                    .thenReturn(false);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::shouldSandBorderSpikes)
                    .thenReturn(true);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::shouldRoundBorderCorners)
                    .thenReturn(false);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::getOwnerMapBorderCornerRadius)
                    .thenReturn(CORNER_RADIUS);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::getOwnerMapBorderCornerSegments)
                    .thenReturn(CORNER_SEGMENTS);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::getOwnerMapBorderChamferAngleRadians)
                    .thenReturn(CHAMFER_ANGLE);

                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getOwnerMapDesaturationDarkening)
                    .thenReturn(DESATURATION_DARKENING);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getOwnerMapPresenceLightening)
                    .thenReturn(PRESENCE_LIGHTENING);

                // ACT
                var actualGlobal = RenderStyleReader.readGlobalStyle();

                // ASSERT
                // The pattern whole, since the three are cut with together and a knob threaded
                // into a neighbouring slot is what asserting them one at a time would miss.
                assertThat(actualGlobal.hatch().pattern())
                    .isEqualTo(new HatchPattern(HATCH_SPACING, HATCH_ANGLE, HATCH_JOIN_TOLERANCE));

                // Both halves of the stroke at once, since the width the player set is only the
                // stroke they asked for if it arrives at the quality they set as well.
                assertThat(actualGlobal.hatch().stroke())
                    .isEqualTo(new GlLineHatchStroke(GlLineQuality.ALIASED, HATCH_WIDTH_FRACTION));

                var cornerRounding = actualGlobal.borderSmoothing().cornerRounding();

                assertThat(actualGlobal.borderSmoothing().spikeSanding().shouldSandSpikes())
                    .isTrue();

                assertThat(cornerRounding.shouldRoundCorners())
                    .isFalse();
                assertThat(cornerRounding.cornerRadius())
                    .isEqualTo(CORNER_RADIUS);
                assertThat(cornerRounding.cornerSegments())
                    .isEqualTo(CORNER_SEGMENTS);
                assertThat(cornerRounding.chamferAngleRadians())
                    .isEqualTo(CHAMFER_ANGLE);

                assertThat(actualGlobal.desaturationDarkening())
                    .isEqualTo(DESATURATION_DARKENING);

                // Distinct from the darkening beside it: the two are adjacent doubles on the
                // record, so equal stand-ins would let a transposed pair read as correct.
                assertThat(actualGlobal.presenceLightening())
                    .isEqualTo(PRESENCE_LIGHTENING);
                assertThat(actualGlobal.hoverHighlight().glow().opacity())
                    .isEqualTo(HOVER_GLOW_OPACITY);

                // Distinct from the cursor tier's for the reason the two strengths above are
                // distinct from each other: the two tiers are the same record in adjacent slots,
                // so equal stand-ins would let a transposed pair read as correct. Read off the
                // wash rather than the halo, the preview tier carrying no halo of its own.
                assertThat(actualGlobal.previewHighlight().wash().fillOpacity())
                    .isEqualTo(PREVIEW_WASH_OPACITY);
            }
        }

        // The switched-on arm of the same crossing. Its own case rather than a second assertion
        // above, because the mapping is the whole of what this reads: a stroke that came back
        // aliased whatever the player set would pass every other assertion in this class.
        @Test
        void strokesTheHatchSmoothedWhenTheSmoothingKnobIsOn() {

            // ARRANGE
            // The tier gathers three sections, so the two this case says nothing about are still
            // opened: a settings read outside a mock has no LunaLib to answer it.
            try (var geometrySettingsMock = mockStatic(KmuOwnerMapGeometrySettings.class);
                    var highlightSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class);
                    var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::shouldSmoothHatchLines)
                    .thenReturn(true);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::getOwnerMapHatchWidthFraction)
                    .thenReturn(HATCH_WIDTH_FRACTION);

                // ACT
                var actualGlobal = RenderStyleReader.readGlobalStyle();

                // ASSERT
                assertThat(actualGlobal.hatch().stroke())
                    .isEqualTo(new GlLineHatchStroke(GlLineQuality.SMOOTHED, HATCH_WIDTH_FRACTION));
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
        void landsEachKnobInTheSubRecordOfThePassThatReadsIt() {

            // ARRANGE
            try (var geometrySettingsMock = mockStatic(KmuOwnerMapGeometrySettings.class)) {

                // The two gates differ, so a gate threaded into the other pass's sub-record reads
                // as a swap rather than as two switches that happen to agree.
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::shouldSandBorderSpikes)
                    .thenReturn(true);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::shouldRoundBorderCorners)
                    .thenReturn(false);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::getOwnerMapBorderSpikeHeight)
                    .thenReturn(SPIKE_HEIGHT);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::getOwnerMapBorderSpikeAngleRadians)
                    .thenReturn(SPIKE_ANGLE);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::getOwnerMapBorderCornerRadius)
                    .thenReturn(CORNER_RADIUS);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::getOwnerMapBorderCornerSegments)
                    .thenReturn(CORNER_SEGMENTS);
                geometrySettingsMock
                    .when(KmuOwnerMapGeometrySettings::getOwnerMapBorderChamferAngleRadians)
                    .thenReturn(CHAMFER_ANGLE);

                // ACT
                var actualSmoothing = RenderStyleReader.readBorderSmoothingStyle();

                // ASSERT
                // Both passes' shape belongs on one profile - that is what lets them work from a
                // single value - but each knob belongs to the half of it the pass that reads the
                // knob is handed. The split is where a field could be threaded into the wrong
                // sub-record and still compile, so both halves are named here.
                assertThat(actualSmoothing.spikeSanding().shouldSandSpikes())
                    .isTrue();
                assertThat(actualSmoothing.spikeSanding().spikeHeight())
                    .isEqualTo(SPIKE_HEIGHT);
                assertThat(actualSmoothing.spikeSanding().spikeAngleRadians())
                    .isEqualTo(SPIKE_ANGLE);

                assertThat(actualSmoothing.cornerRounding().shouldRoundCorners())
                    .isFalse();
                assertThat(actualSmoothing.cornerRounding().cornerRadius())
                    .isEqualTo(CORNER_RADIUS);
                assertThat(actualSmoothing.cornerRounding().cornerSegments())
                    .isEqualTo(CORNER_SEGMENTS);
                assertThat(actualSmoothing.cornerRounding().chamferAngleRadians())
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
        void threadsEachHoverSettingIntoItsMatchingSlot() {

            // ARRANGE
            try (var highlightSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class)) {

                highlightSettingsMock
                    .when(KmuOwnerMapHighlightSettings::getOwnerMapHoverHighlightColour)
                    .thenReturn(OwnerPaletteChoice.SECONDARY);
                highlightSettingsMock
                    .when(KmuOwnerMapHighlightSettings::getOwnerMapHoverGlowOpacity)
                    .thenReturn(GLOW_OPACITY);
                highlightSettingsMock
                    .when(KmuOwnerMapHighlightSettings::getOwnerMapHoverGlowWidth)
                    .thenReturn(GLOW_WIDTH);
                highlightSettingsMock
                    .when(KmuOwnerMapHighlightSettings::getOwnerMapHoverGlowLayers)
                    .thenReturn(GLOW_LAYERS);
                highlightSettingsMock
                    .when(KmuOwnerMapHighlightSettings::getOwnerMapHoverGlowPulseStrength)
                    .thenReturn(GLOW_PULSE_STRENGTH);
                highlightSettingsMock
                    .when(KmuOwnerMapHighlightSettings::getOwnerMapHoverGlowPulsePeriod)
                    .thenReturn(GLOW_PULSE_PERIOD);
                highlightSettingsMock
                    .when(KmuOwnerMapHighlightSettings::getOwnerMapHoverWashOpacity)
                    .thenReturn(WASH_OPACITY);
                highlightSettingsMock
                    .when(KmuOwnerMapHighlightSettings::getOwnerMapHoverWashOutlineOpacity)
                    .thenReturn(WASH_OUTLINE_OPACITY);
                highlightSettingsMock
                    .when(KmuOwnerMapHighlightSettings::getOwnerMapHoverWashOutlineWidth)
                    .thenReturn(WASH_OUTLINE_WIDTH);

                // ACT
                var actualHover = RenderStyleReader.readHoverHighlightStyle();

                // ASSERT
                assertThat(actualHover.colour())
                    .isEqualTo(OwnerPaletteSlot.SECONDARY);

                assertThat(actualHover.glow().opacity())
                    .isEqualTo(GLOW_OPACITY);
                assertThat(actualHover.glow().width())
                    .isEqualTo(GLOW_WIDTH);
                assertThat(actualHover.glow().layers())
                    .isEqualTo(GLOW_LAYERS);
                assertThat(actualHover.glow().pulseStrength())
                    .isEqualTo(GLOW_PULSE_STRENGTH);
                assertThat(actualHover.glow().pulsePeriodSeconds())
                    .isEqualTo(GLOW_PULSE_PERIOD);

                assertThat(actualHover.wash().fillOpacity())
                    .isEqualTo(WASH_OPACITY);
                assertThat(actualHover.wash().outlineOpacity())
                    .isEqualTo(WASH_OUTLINE_OPACITY);
                assertThat(actualHover.wash().outlineWidth())
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
        void threadsEachPreviewSettingIntoItsMatchingSlot() {

            // ARRANGE
            try (var highlightSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class)) {

                highlightSettingsMock
                    .when(KmuOwnerMapHighlightSettings::getOwnerMapPreviewHighlightColour)
                    .thenReturn(OwnerPaletteChoice.PRIMARY);
                highlightSettingsMock
                    .when(KmuOwnerMapHighlightSettings::getOwnerMapPreviewWashOpacity)
                    .thenReturn(WASH_OPACITY);

                // ACT
                var actualPreview = RenderStyleReader.readPreviewHighlightStyle();

                // ASSERT
                assertThat(actualPreview.colour())
                    .isEqualTo(OwnerPaletteSlot.PRIMARY);

                assertThat(actualPreview.wash().fillOpacity())
                    .isEqualTo(WASH_OPACITY);
            }
        }

        // The tier's two fixed weights, asserted against literals rather than against the reader's
        // own constants: a value the player cannot move is one a test has to pin, or a
        // future edit to it passes unnoticed.
        @Test
        void tracesLitRegionsAtItsOwnFixedWeights() {

            // ARRANGE
            var expectedOutlineOpacity = 0.9;
            var expectedOutlineWidth = 0.5;

            try (var highlightSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class)) {

                // ACT
                var actualPreview = RenderStyleReader.readPreviewHighlightStyle();

                // ASSERT
                assertThat(actualPreview.wash().outlineOpacity())
                    .isEqualTo(expectedOutlineOpacity);
                assertThat(actualPreview.wash().outlineWidth())
                    .isEqualTo(expectedOutlineWidth);
            }
        }

        // The halo the tier declines. Its own case rather than an assertion beside the weights,
        // since "draws no halo" is a decision about the tier rather than a weight it carries.
        @Test
        void carriesNoHalo() {

            // ARRANGE
            try (var highlightSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class)) {

                // ACT
                var actualPreview = RenderStyleReader.readPreviewHighlightStyle();

                // ASSERT
                assertThat(actualPreview.glow())
                    .isEqualTo(HoverGlowStyle.NO_GLOW);
            }
        }
    }

    @Nested
    class ReadRenderStyle {

        @Test
        void carriesTheGlobalTierAndTheCategoriesTheLayerDeclares() {

            // ARRANGE
            var expectedDesaturationDarkening = 0.3;

            try (var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class);
                    var geometrySettingsMock = mockStatic(KmuOwnerMapGeometrySettings.class);
                    var highlightSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class)) {

                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getOwnerMapDesaturationDarkening)
                    .thenReturn(0.3);

                // One category the layer declares, so the theme can hold only what the declaration
                // read. The two mocks nothing stubs stand in for the section classes the global tier
                // also reaches, a settings read outside them having no LunaLib to answer it.
                var declaredStyle = new CategoryStyle(
                    new ElementStyle(OwnerPaletteSlot.PRIMARY, FILL_OPACITY),
                    ElementStyle.NOT_DRAWN,
                    OUTER_WIDTH,
                    ElementStyle.NOT_DRAWN,
                    INNER_WIDTH);

                var contentInputs = createInputsSpellingNames(OwnerNameFormatChoice.FULL);
                var categoriesMock = mock(OwnerCategories.class);

                when(categoriesMock.readCategoryStyles(contentInputs))
                    .thenReturn(Map.of(StandInCategoryFake.OWNED, declaredStyle));

                // ACT
                var actualRenderStyle = RenderStyleReader.readRenderStyle(categoriesMock, contentInputs);

                // ASSERT
                assertThat(actualRenderStyle.global().desaturationDarkening())
                    .isEqualTo(expectedDesaturationDarkening);
                assertThat(actualRenderStyle.categories())
                    .containsExactly(entry(StandInCategoryFake.OWNED, declaredStyle));
            }
        }
    }
}
