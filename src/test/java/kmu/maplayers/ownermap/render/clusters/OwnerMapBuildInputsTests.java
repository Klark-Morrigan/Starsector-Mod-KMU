package kmu.maplayers.ownermap.render.clusters;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.style.MapStyling;
import kmu.maplayers.ownermap.render.style.OwnerCategoriesFake;
import kmu.maplayers.ownermap.render.style.StandInCategoryFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what the retained inputs answer for themselves: that the constructor threads each snapshot
 * into its matching accessor, and that the paints it holds are resolved over its own reading, picks
 * and scheme rather than any handed in beside them.
 *
 * <p>How a paint is resolved and remembered is
 * {@link kmu.maplayers.ownermap.render.style.MapPaintsTests}'s; what belongs here is that the
 * paints are resolved over these inputs' own snapshots, which is the whole reason they sit on the
 * type that holds them.
 */
final class OwnerMapBuildInputsTests {

    private static final String OWNER_ID = "hegemony";

    // The owner a paint is resolved for, and the shades the record sinks a desaturated one to.
    // Distinct from the owner's own pair, so a resolved palette names which of the two it took.
    private static final SystemOwner OWNER =
        new SystemOwner(OWNER_ID, new ColourPair(Color.RED, Color.BLUE));

    private static final ColourPair DESATURATION_PALETTE =
        new ColourPair(Color.GREEN, Color.YELLOW);

    @Nested
    class Accessors {

        // The overlay is handed in on, so the getter is told from a constant answering false by
        // the case below.
        @Test
        void returnEachConstructorInputInItsMatchingSlot() {

            // ARRANGE
            var styling = buildStyling();
            var viewReading = new ViewReading(
                mock(OwnerPaintedView.class),
                OwnerReadingFake.createAnsweringNothing(),
                new OwnerSourceFake());

            var contentInputs = ContentInputsFixtures.createInertInputs();

            // ACT
            var inputs = new OwnerMapBuildInputs(
                styling,
                viewReading,
                contentInputs,
                true);

            // ASSERT
            // By identity throughout: the scheme and the picks are read back through the paints
            // the inputs hold, and must be the very ones handed in.
            assertThat(inputs.getStyling())
                .isSameAs(styling);
            assertThat(inputs.getViewReading())
                .isSameAs(viewReading);
            assertThat(inputs.getContentInputs())
                .isSameAs(contentInputs);
            assertThat(inputs.isAnchorOverlayShown())
                .isTrue();
        }

        // The other half of the case above, so a getter answering a constant cannot pass both.
        @Test
        void returnTheAnchorOverlayHandedInOff() {

            // ARRANGE
            var inputs = buildInputsUnder(
                OwnerReadingFake.createAnsweringNothing(),
                ContentInputsFixtures.createInertInputs());

            // ACT
            var actualIsAnchorOverlayShown = inputs.isAnchorOverlayShown();

            // ASSERT
            assertThat(actualIsAnchorOverlayShown)
                .isFalse();
        }
    }

    @Nested
    class Paints {

        // The paints a build's cells and cluster groups draw in are resolved over the reading and
        // picks this build was baked under - so the reading is asked with exactly the inputs' other
        // snapshot, and the adjustment it answers is the one the owner draws under.
        @Test
        void resolvesOverTheInputsOwnReadingAndPicks() {

            // ARRANGE
            var expectedAdjustment = new ElementStyleAdjustment(0.5, false);
            var contentInputs = ContentInputsFixtures.createInertInputs();
            var readingMock = mock(OwnerReading.class);

            when(readingMock.resolveStyleAdjustment(any(), any()))
                .thenReturn(expectedAdjustment);
            when(readingMock.resolveCategory(any(), any()))
                .thenReturn(StandInCategoryFake.OWNED);

            var inputs = buildInputsUnder(readingMock, contentInputs);

            // ACT
            var actualPaint = inputs.getPaints().resolveOwnerPaintOf(OWNER);

            // ASSERT
            assertThat(actualPaint.adjustment())
                .isSameAs(expectedAdjustment);

            verify(readingMock)
                .resolveStyleAdjustment(OWNER_ID, contentInputs);
        }

        // The shades and the trace a stage reads off the paints are the inputs' own, so a cell
        // re-shaped by a later batch is styled by the scheme its neighbours were.
        @Test
        void resolvesAgainstTheInputsOwnStyling() {

            // ARRANGE
            var styling = buildStyling();
            var inputs = new OwnerMapBuildInputs(
                styling,
                new ViewReading(
                    mock(OwnerPaintedView.class),
                    OwnerReadingFake.createAnsweringNothing(),
                    new OwnerSourceFake()),
                ContentInputsFixtures.createInertInputs(),
                false);

            // ACT
            var actualStyling = inputs.getPaints().getStyling();

            // ASSERT
            assertThat(actualStyling)
                .isSameAs(styling);
        }
    }

    // Inputs over the scheme below, so a paint resolved through them names which of the record's
    // own shades it took.
    private static OwnerMapBuildInputs buildInputsUnder(
            OwnerReading reading,
            ContentInputs contentInputs) {

        return new OwnerMapBuildInputs(
            buildStyling(),
            new ViewReading(mock(OwnerPaintedView.class), reading, new OwnerSourceFake()),
            contentInputs,
            false);
    }

    // A scheme over a real theme, KMU's own layers' categories and a distinct desaturation palette.
    private static MapStyling buildStyling() {

        return new MapStyling(
            OwnerMapClusterFixtures.createRenderStyleForEveryCategory(
                OwnerMapClusterFixtures.createInertCategoryStyle()),
            OwnerMapClusterFixtures.BORDER_TRACE,
            OwnerCategoriesFake.SHARED_FAKE,
            OwnerMapClusterFixtures.NEUTRAL_PALETTE,
            DESATURATION_PALETTE,
            OwnerMapClusterFixtures.NEUTRAL_PALETTE);
    }
}
