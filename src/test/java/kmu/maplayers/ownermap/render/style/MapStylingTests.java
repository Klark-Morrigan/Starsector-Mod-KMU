package kmu.maplayers.ownermap.render.style;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.render.clusters.ClusterBorderTrace;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.settings.KmuMapLabelSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the paint scheme's answers: what one build's read of the theme, the border trace and the
 * reading carries, the shade its neutral pair reads as, and the style an unowned cell draws in.
 */
final class MapStylingTests {

    @Nested
    class ReadNeutralColour {

        // An unowned cell names no owner, so its pair holds one shade twice and the colour is
        // whichever slot you look at.
        @Test
        void answersTheShadeTheNeutralPairHolds() {

            // ARRANGE
            var styling = buildStylingOverDistinctPalettes();
            var expectedColour = Color.CYAN;

            // ACT
            var actualColour = styling.readNeutralColour();

            // ASSERT
            assertThat(actualColour)
                .isEqualTo(expectedColour);
        }

        // The three palettes sit in adjacent same-typed slots, so a colour read from the wrong one
        // would still compile and still answer a Color. Only distinct shades catch it.
        @Test
        void readsTheNeutralPairRatherThanEitherSpotlightPalette() {

            // ARRANGE
            var styling = buildStylingOverDistinctPalettes();

            // ACT
            var actualColour = styling.readNeutralColour();

            // ASSERT
            assertThat(actualColour)
                .isNotEqualTo(Color.MAGENTA)
                .isNotEqualTo(Color.PINK);
        }

        // A scheme with no theme or trace, whose three palettes each hold shades the others do not.
        private static MapStyling buildStylingOverDistinctPalettes() {

            return new MapStyling(
                null,
                null,
                OwnerCategoriesFake.SHARED_FAKE,
                new ColourPair(Color.CYAN, Color.CYAN),
                new ColourPair(Color.MAGENTA, Color.ORANGE),
                new ColourPair(Color.PINK, Color.WHITE));
        }
    }

    @Nested
    class ReadFromLunaSettings {

        // The global darkening the inert theme carries, applied to the reading's recede shades:
        // light grey and dark grey kept at 70%.
        private static final ColourPair DARKENED_RECEDE_PALETTE =
            new ColourPair(new Color(134, 134, 134), new Color(45, 45, 45));

        // Trace parameters no default lands on, so the read is seen to take them.
        private static final double WELD_TOLERANCE = 7.0;
        private static final double MITER_SPIKE_LIMIT = 3.0;

        // The trace reads its two parameters off the Dev tab, which no test JVM answers.
        private MockedStatic<KmuMapLabelSettings> labelSettingsMock;

        @BeforeEach
        void openTheBorderTraceSeam() {

            labelSettingsMock = mockStatic(KmuMapLabelSettings.class);
            labelSettingsMock
                .when(KmuMapLabelSettings::getMapBorderWeldTolerance)
                .thenReturn(WELD_TOLERANCE);
            labelSettingsMock
                .when(KmuMapLabelSettings::getMapBorderMiterLimit)
                .thenReturn(MITER_SPIKE_LIMIT);
        }

        @AfterEach
        void closeTheBorderTraceSeam() {
            labelSettingsMock.close();
        }

        // Read here once for the build, so every cluster group it draws - and every one the
        // incremental refresh redraws - is traced under the same parameters.
        @Test
        void carriesTheBorderTraceItReads() {

            // ARRANGE
            var expectedBorderTrace = new ClusterBorderTrace(7.0, 3.0);

            try (var styleReaderMock = mockStatic(RenderStyleReader.class)) {

                stubInertTheme(styleReaderMock);

                // ACT
                var actualStyling = readStylingOverAReadingAnsweringNothing();

                // ASSERT
                assertThat(actualStyling.borderTrace())
                    .isEqualTo(expectedBorderTrace);
            }
        }

        // The picks go to the theme read rather than being sampled again, since a category's style
        // may ride on one and the rebuild has already decided which picks it was owed by.
        @Test
        void carriesTheThemeReadForTheLayersCategoriesUnderThePicks() {

            // ARRANGE
            var contentInputs = ContentInputsFixtures.createInertInputs();
            var theme = buildInertTheme();

            try (var styleReaderMock = mockStatic(RenderStyleReader.class)) {

                styleReaderMock
                    .when(() -> RenderStyleReader.readRenderStyle(OwnerCategoriesFake.SHARED_FAKE, contentInputs))
                    .thenReturn(theme);

                // ACT
                var actualStyling = MapStyling.readFromLunaSettings(
                    OwnerCategoriesFake.SHARED_FAKE,
                    OwnerReadingFake.createAnsweringNothing(),
                    contentInputs);

                // ASSERT
                assertThat(actualStyling.renderStyle())
                    .isSameAs(theme);
                assertThat(actualStyling.categories())
                    .isSameAs(OwnerCategoriesFake.SHARED_FAKE);
            }
        }

        @Test
        void holdsTheReadingsUnownedShadeInBothNeutralSlots() {

            // ARRANGE
            var expectedNeutralPalette = new ColourPair(Color.GRAY, Color.GRAY);

            try (var styleReaderMock = mockStatic(RenderStyleReader.class)) {

                stubInertTheme(styleReaderMock);

                // ACT
                var actualStyling = readStylingOverAReadingAnsweringNothing();

                // ASSERT
                assertThat(actualStyling.neutralPalette())
                    .isEqualTo(expectedNeutralPalette);
            }
        }

        @Test
        void darkensTheReadingsRecedeShadesByTheThemesDarkening() {

            // ARRANGE
            try (var styleReaderMock = mockStatic(RenderStyleReader.class)) {

                stubInertTheme(styleReaderMock);

                // ACT
                var actualStyling = readStylingOverAReadingAnsweringNothing();

                // ASSERT
                assertThat(actualStyling.desaturationPalette())
                    .isEqualTo(DARKENED_RECEDE_PALETTE);
            }
        }

        private static MapStyling readStylingOverAReadingAnsweringNothing() {

            return MapStyling.readFromLunaSettings(
                OwnerCategoriesFake.SHARED_FAKE,
                OwnerReadingFake.createAnsweringNothing(),
                ContentInputsFixtures.createInertInputs());
        }

        private static void stubInertTheme(MockedStatic<RenderStyleReader> styleReaderMock) {

            styleReaderMock
                .when(() -> RenderStyleReader.readRenderStyle(any(), any()))
                .thenReturn(buildInertTheme());
        }

        private static RenderStyle buildInertTheme() {

            return OwnerMapClusterFixtures.createRenderStyleForEveryCategory(
                OwnerMapClusterFixtures.createInertCategoryStyle());
        }
    }

    @Nested
    class ResolveUnownedStyle {

        // Two bundles told apart by their outline width alone, so the answer names the category
        // it was looked up under.
        private static final CategoryStyle SETTLED_STYLE = createStyleOutlinedAt(2.0);
        private static final CategoryStyle EMPTY_STYLE = createStyleOutlinedAt(3.0);

        @Test
        void answersTheSettledCategorysStyleForASettledCell() {

            // ARRANGE
            var styling = buildStylingOverTheTwoBundles();

            // ACT
            var actualStyle = styling.resolveUnownedStyle(true);

            // ASSERT
            assertThat(actualStyle)
                .isEqualTo(SETTLED_STYLE);
        }

        @Test
        void answersTheEmptyCategorysStyleForAnEmptyCell() {

            // ARRANGE
            var styling = buildStylingOverTheTwoBundles();

            // ACT
            var actualStyle = styling.resolveUnownedStyle(false);

            // ASSERT
            assertThat(actualStyle)
                .isEqualTo(EMPTY_STYLE);
        }

        private static MapStyling buildStylingOverTheTwoBundles() {

            var renderStyle = new RenderStyle(
                ThemeFixtures.createInertGlobalStyle(),
                Map.of(
                    StandInCategoryFake.SETTLED, SETTLED_STYLE,
                    StandInCategoryFake.EMPTY, EMPTY_STYLE));

            return new MapStyling(
                renderStyle,
                null,
                OwnerCategoriesFake.SHARED_FAKE,
                new ColourPair(Color.CYAN, Color.CYAN),
                new ColourPair(Color.MAGENTA, Color.ORANGE),
                new ColourPair(Color.PINK, Color.WHITE));
        }

        private static CategoryStyle createStyleOutlinedAt(double outerWidth) {

            var element = new ElementStyle(OwnerPaletteSlot.PRIMARY, 1.0);

            return new CategoryStyle(element, element, 1.0, element, outerWidth);
        }
    }
}
