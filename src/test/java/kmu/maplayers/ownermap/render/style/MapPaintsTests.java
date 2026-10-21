package kmu.maplayers.ownermap.render.style;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what one build's paints answer: an owner's paint cascading the reading and the sampled picks
 * onto the scheme's theme and shades, a cell nobody owns taking its category, recede and neutral
 * shades by whether it is settled and whether the spotlit owner lives there, and both remembered
 * once resolved.
 *
 * <p>The paints are stood up on the three values they state and nothing else, which is what lets a
 * stage that paints take them without the build they came from.
 *
 * <p>The rules underneath are pinned where they live: the per-owner decision by
 * {@link OwnerStyleResolverTests}, the bundle by {@link OwnerStylingTests}, the unowned rules by
 * {@link UnownedStyleResolverTests} and the palette swap by {@link MapPalettesTests}. What belongs
 * here is that each is asked over these paints' own inputs.
 */
final class MapPaintsTests {

    private static final String OWNER_ID = "hegemony";

    // The owner a paint is resolved for, and the four palettes the scheme holds. Each distinct, so a
    // resolved palette names which of them it took.
    private static final ColourPair OWNER_PALETTE = new ColourPair(Color.RED, Color.BLUE);
    private static final ColourPair NEUTRAL_PALETTE = new ColourPair(Color.GRAY, Color.GRAY);
    private static final ColourPair DESATURATION_PALETTE = new ColourPair(Color.GREEN, Color.YELLOW);
    private static final ColourPair PRESENCE_PALETTE = new ColourPair(Color.WHITE, Color.WHITE);

    private static final SystemOwner OWNER = new SystemOwner(OWNER_ID, OWNER_PALETTE);

    // The two unowned bundles, at opacities no other bundle shares, so a resolved style names the
    // category it was read from.
    private static final CategoryStyle SETTLED_STYLE = buildStyleAt(0.75);
    private static final CategoryStyle EMPTY_STYLE = buildStyleAt(0.25);

    // The recede a spotlight sinks the rest of the sector under: both mute and desaturate, so a
    // receded paint shows in its adjustment and in its palette alike.
    private static final ElementStyleAdjustment SPOTLIGHT_RECEDE = new ElementStyleAdjustment(0.5, true);

    @Nested
    class ResolveOwnerPaintOf {

        @Test
        void cascadesTheReadingUnderTheSampledPicks() {
            // Off filter the decision is the reading's own call, asked with the picks the paints
            // were stated over - so the adjustment it answers is the one the owner draws under.
            var expectedAdjustment = new ElementStyleAdjustment(0.5, false);
            var contentInputs = ContentInputsFixtures.createInertInputs();
            var readingMock = buildReadingAdjustingBy(expectedAdjustment);
            var paints = buildPaintsOver(readingMock, contentInputs);
            var actualPaint = paints.resolveOwnerPaintOf(OWNER);

            assertThat(actualPaint.adjustment())
                .isSameAs(expectedAdjustment);
            assertThat(actualPaint.palette())
                .isEqualTo(OWNER_PALETTE);

            verify(readingMock)
                .resolveStyleAdjustment(OWNER_ID, contentInputs);
        }

        @Test
        void sinksADesaturatedOwnerToTheSchemesDesaturationShades() {
            // The shades come off the scheme the paints were stated over, which is what keeps an
            // owner's fill, its border and its cells' seams sunk to one grey.
            var paints = buildPaintsOver(
                buildReadingAdjustingBy(new ElementStyleAdjustment(1.0, true)),
                ContentInputsFixtures.createInertInputs());

            var actualPaint = paints.resolveOwnerPaintOf(OWNER);

            assertThat(actualPaint.palette())
                .isEqualTo(DESATURATION_PALETTE);
        }

        @Test
        void asksTheReadingOnceForAnOwnerAskedPerCell() {
            // Every cell of an owner and its cluster group ask for the same paint, so paints that
            // went back to the reading each time would decide one owner's style once per cell.
            var contentInputs = ContentInputsFixtures.createInertInputs();
            var readingMock = buildReadingAdjustingBy(new ElementStyleAdjustment(0.5, false));
            var paints = buildPaintsOver(readingMock, contentInputs);

            paints.resolveOwnerPaintOf(OWNER);
            paints.resolveOwnerPaintOf(OWNER);

            verify(readingMock, times(1))
                .resolveStyleAdjustment(OWNER_ID, contentInputs);
        }
    }

    @Nested
    class ResolveOwnerStyleDecisionOf {

        @Test
        void answersTheDecisionTheOwnersPaintWasResolvedFrom() {
            // The label path reads the decision; the fill path reads the paint. Both off one memo,
            // so the reading is asked once however many of either an owner's clusters make.
            var expectedAdjustment = new ElementStyleAdjustment(0.5, false);
            var contentInputs = ContentInputsFixtures.createInertInputs();
            var readingMock = buildReadingAdjustingBy(expectedAdjustment);
            var paints = buildPaintsOver(readingMock, contentInputs);

            paints.resolveOwnerPaintOf(OWNER);

            var actualDecision = paints.resolveOwnerStyleDecisionOf(OWNER_ID);

            assertThat(actualDecision.adjustment())
                .isSameAs(expectedAdjustment);
            assertThat(actualDecision.category())
                .isEqualTo(StandInCategoryFake.OWNED);

            verify(readingMock, times(1))
                .resolveStyleAdjustment(OWNER_ID, contentInputs);
        }
    }

    @Nested
    class ResolveUnownedPaintOf {

        @Test
        void paintsASettledCellInTheSettledBundle() {

            var paints = buildUnownedPaintsUnder(ContentInputsFixtures.createInertInputs());
            var actualPaint = paints.resolveUnownedPaintOf(true, false);

            assertThat(actualPaint.style())
                .isSameAs(SETTLED_STYLE);
            assertThat(actualPaint.adjustment())
                .isSameAs(ElementStyleAdjustment.NONE);
            assertThat(actualPaint.palette())
                .isEqualTo(NEUTRAL_PALETTE);
        }

        @Test
        void paintsAnEmptyCellInTheEmptyBundle() {

            var paints = buildUnownedPaintsUnder(ContentInputsFixtures.createInertInputs());
            var actualPaint = paints.resolveUnownedPaintOf(false, false);

            assertThat(actualPaint.style())
                .isSameAs(EMPTY_STYLE);
        }

        @Test
        void sinksASettledCellUnderTheSpotlightsRecede() {
            // A settled cell is part of the rest of the sector a spotlight recedes, so it takes the
            // recede and the shades it sinks to.
            var paints = buildUnownedPaintsUnder(ContentInputsFixtures.createInputsRecedingBehind(
                "selected-owner",
                SPOTLIGHT_RECEDE));

            var actualPaint = paints.resolveUnownedPaintOf(true, false);

            assertThat(actualPaint.adjustment())
                .isSameAs(SPOTLIGHT_RECEDE);
            assertThat(actualPaint.palette())
                .isEqualTo(DESATURATION_PALETTE);
        }

        @Test
        void sparesAndLiftsASettledCellTheSpotlitOwnerLivesIn() {
            // The pick's own settlement is spared the recede and lifted clear of the greys that sank,
            // without ever taking the pick's own shades.
            var paints = buildUnownedPaintsUnder(ContentInputsFixtures.createInputsRecedingBehind(
                "selected-owner",
                SPOTLIGHT_RECEDE));

            var actualPaint = paints.resolveUnownedPaintOf(true, true);

            assertThat(actualPaint.adjustment())
                .isSameAs(ElementStyleAdjustment.NONE);
            assertThat(actualPaint.palette())
                .isEqualTo(PRESENCE_PALETTE);
        }

        @Test
        void leavesAnEmptyCellUnrecededUnderTheSpotlight() {
            // The empty backdrop is what the sector's shape is drawn in, so no spotlight sinks it.
            var paints = buildUnownedPaintsUnder(ContentInputsFixtures.createInputsRecedingBehind(
                "selected-owner",
                SPOTLIGHT_RECEDE));

            var actualPaint = paints.resolveUnownedPaintOf(false, false);

            assertThat(actualPaint.adjustment())
                .isSameAs(ElementStyleAdjustment.NONE);
            assertThat(actualPaint.palette())
                .isEqualTo(NEUTRAL_PALETTE);
        }

        @Test
        void answersASecondAskWithThePaintTheFirstResolved() {
            // The uninhabited backdrop covers most of the sector, so its paint is resolved once
            // rather than once per cell.
            var paints = buildUnownedPaintsUnder(ContentInputsFixtures.createInertInputs());
            var firstPaint = paints.resolveUnownedPaintOf(false, false);
            var secondPaint = paints.resolveUnownedPaintOf(false, false);

            assertThat(secondPaint)
                .isSameAs(firstPaint);
        }

        @Test
        void keepsEachShapeOfUnownedPaintApart() {
            // Four shapes share one memo, so a settled cell must not be answered with the paint an
            // empty one resolved first.
            var paints = buildUnownedPaintsUnder(ContentInputsFixtures.createInertInputs());

            paints.resolveUnownedPaintOf(false, false);

            var actualPaint = paints.resolveUnownedPaintOf(true, false);

            assertThat(actualPaint.style())
                .isSameAs(SETTLED_STYLE);
        }

        // Paints whose reading answers nothing, over a theme carrying the two unowned bundles - an
        // unowned cell asks the reading nothing, so what it paints comes off the scheme and the
        // picks alone.
        private static MapPaints buildUnownedPaintsUnder(ContentInputs contentInputs) {
            return buildPaintsOver(OwnerReadingFake.createAnsweringNothing(), contentInputs);
        }
    }

    // A reading receding every owner by the stated adjustment and keeping every one at full
    // strength, so what a case reads back can only have come from the paints.
    private static OwnerReading buildReadingAdjustingBy(ElementStyleAdjustment adjustment) {

        var readingMock = mock(OwnerReading.class);

        when(readingMock.resolveStyleAdjustment(any(), any()))
            .thenReturn(adjustment);
        when(readingMock.resolveCategory(any(), any()))
            .thenReturn(StandInCategoryFake.OWNED);

        return readingMock;
    }

    // Paints over a theme naming every stand-in category, the four distinct palettes, the given
    // reading and the given picks.
    private static MapPaints buildPaintsOver(
            OwnerReading reading,
            ContentInputs contentInputs) {

        return new MapPaints(
            new MapStyling(
                buildTheme(),
                OwnerMapClusterFixtures.BORDER_TRACE,
                OwnerCategoriesFake.SHARED_FAKE,
                NEUTRAL_PALETTE,
                DESATURATION_PALETTE,
                PRESENCE_PALETTE),
            reading,
            contentInputs);
    }

    // The owned pair at the shared inert bundle and the two unowned ones at their own.
    private static RenderStyle buildTheme() {

        var categories = new LinkedHashMap<MapStyleCategory, CategoryStyle>();

        categories.put(StandInCategoryFake.OWNED, OwnerMapClusterFixtures.createInertCategoryStyle());
        categories.put(StandInCategoryFake.RECEDED, OwnerMapClusterFixtures.createInertCategoryStyle());
        categories.put(StandInCategoryFake.SETTLED, SETTLED_STYLE);
        categories.put(StandInCategoryFake.EMPTY, EMPTY_STYLE);

        return new RenderStyle(ThemeFixtures.createInertGlobalStyle(), categories);
    }

    // A bundle painting every element from the primary shade at the given opacity.
    private static CategoryStyle buildStyleAt(double opacity) {

        var element = new ElementStyle(OwnerPaletteSlot.PRIMARY, opacity);

        return new CategoryStyle(element, element, 1.0, element, 1.0);
    }
}
