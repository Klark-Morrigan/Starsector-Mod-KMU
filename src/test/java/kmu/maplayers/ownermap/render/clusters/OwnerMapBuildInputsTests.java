package kmu.maplayers.ownermap.render.clusters;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;

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
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what the retained inputs answer for themselves: that the empty placeholder carries the
 * view it was built for over inert stand-ins and says it was never built, that the constructor
 * threads each snapshot and each derived set into its matching accessor, and that the paints it
 * holds are resolved over its own reading, picks and scheme rather than any handed in beside them.
 *
 * <p>How a paint is resolved and remembered is
 * {@link kmu.maplayers.ownermap.render.style.MapPaintsTests}'s; what belongs here is that the
 * paints are resolved over these inputs' own snapshots, which is the whole reason they sit on the
 * type that holds them.
 */
final class OwnerMapBuildInputsTests {

    private static final String OWNER_ID = "hegemony";

    // Two distinct sets so a swapped slot is caught by identity, held apart from each other.
    private static final Set<SystemKey> UNFILLED = Set.of(buildCellKey("unfilled-system"));
    private static final Set<SystemKey> CONTESTED = Set.of(buildCellKey("contested-system"));

    // The owner a paint is resolved for, and the shades the record sinks a desaturated one to.
    // Distinct from the owner's own pair, so a resolved palette names which of the two it took.
    private static final SystemOwner OWNER =
        new SystemOwner(OWNER_ID, new ColourPair(Color.RED, Color.BLUE));

    private static final ColourPair DESATURATION_PALETTE =
        new ColourPair(Color.GREEN, Color.YELLOW);

    @Nested
    class CreateEmpty {

        @Test
        void carriesTheViewOverInertStandIns() {

            var viewMock = mock(OwnerPaintedView.class);
            var inputs = OwnerMapBuildInputs.createEmpty(viewMock);

            // The view is the one thing a later frame reads off the placeholder - which panel a
            // fallback was drawn for - so it is the view it was built for rather than a stand-in.
            assertThat(inputs.viewReading().view())
                .isSameAs(viewMock);

            // The empty fallback is never a filtered build, so it selects no owner, recedes nothing
            // and derives nothing about the fill; the scheme's own stand-ins are MapStyling's to
            // pin, read back here only to prove no slot is left null.
            assertThat(inputs.contentInputs().isFiltering())
                .isFalse();
            assertThat(inputs.styling().neutralPalette())
                .isNotNull();
            assertThat(inputs.unfilledSystemKeys())
                .isEmpty();
            assertThat(inputs.contestedSystemKeys())
                .isEmpty();
        }
    }

    @Nested
    class Accessors {

        @Test
        void returnEachConstructorInputInItsMatchingSlot() {

            var styling = MapStyling.createEmpty();
            var viewReading = new ViewReading(
                mock(OwnerPaintedView.class),
                OwnerReadingFake.createAnsweringNothing(),
                new OwnerSourceFake());

            var contentInputs = ContentInputsFixtures.createInertInputs();
            var inputs = new OwnerMapBuildInputs(
                styling,
                viewReading,
                contentInputs,
                UNFILLED,
                CONTESTED);

            // By identity throughout: the two sets are the same type in adjacent slots, so only
            // distinct instances catch a swap between them.
            assertThat(inputs.styling())
                .isSameAs(styling);
            assertThat(inputs.viewReading())
                .isSameAs(viewReading);
            assertThat(inputs.contentInputs())
                .isSameAs(contentInputs);
            assertThat(inputs.unfilledSystemKeys())
                .isSameAs(UNFILLED);
            assertThat(inputs.contestedSystemKeys())
                .isSameAs(CONTESTED);
        }
    }

    @Nested
    class WasBuilt {

        @Test
        void refusesThePlaceholderAFailedBuildStandsBehind() {

            assertThat(OwnerMapBuildInputs.createEmpty(mock(OwnerPaintedView.class)).wasBuilt())
                .isFalse();
        }

        @Test
        void acceptsInputsCarryingAReading() {

            var inputs = buildInputsUnder(
                OwnerReadingFake.createAnsweringNothing(),
                ContentInputsFixtures.createInertInputs());

            assertThat(inputs.wasBuilt())
                .isTrue();
        }
    }

    @Nested
    class Paints {

        @Test
        void resolvesOverTheInputsOwnReadingAndPicks() {
            // The paints a build's cells and cluster groups draw in are resolved over the reading
            // and picks this build was baked under - so the reading is asked with exactly the
            // inputs' other snapshot, and the adjustment it answers is the one the owner draws under.
            var expectedAdjustment = new ElementStyleAdjustment(0.5, false);
            var contentInputs = ContentInputsFixtures.createInertInputs();
            var readingMock = mock(OwnerReading.class);

            when(readingMock.resolveStyleAdjustment(any(), any()))
                .thenReturn(expectedAdjustment);
            when(readingMock.resolveCategory(any(), any()))
                .thenReturn(StandInCategoryFake.OWNED);

            var inputs = buildInputsUnder(readingMock, contentInputs);
            var actualPaint = inputs.paints().resolveOwnerPaintOf(OWNER);

            assertThat(actualPaint.adjustment())
                .isSameAs(expectedAdjustment);
            verify(readingMock)
                .resolveStyleAdjustment(OWNER_ID, contentInputs);
        }

        @Test
        void resolvesAgainstTheInputsOwnStyling() {
            // The shades and the trace a stage reads off the paints are the inputs' own, so a cell
            // re-shaped by a later batch is styled by the scheme its neighbours were.
            var inputs = buildInputsUnder(
                OwnerReadingFake.createAnsweringNothing(),
                ContentInputsFixtures.createInertInputs());

            var actualStyling = inputs.paints().getStyling();

            assertThat(actualStyling)
                .isSameAs(inputs.styling());
        }
    }

    // Inputs over a real theme, KMU's own layers' categories and a distinct desaturation palette,
    // so a paint resolved through them names which of the record's own shades it took.
    private static OwnerMapBuildInputs buildInputsUnder(
            OwnerReading reading,
            ContentInputs contentInputs) {

        return new OwnerMapBuildInputs(
            new MapStyling(
                OwnerMapClusterFixtures.createRenderStyleForEveryCategory(
                    OwnerMapClusterFixtures.createInertCategoryStyle()),
                OwnerMapClusterFixtures.BORDER_TRACE,
                OwnerCategoriesFake.SHARED_FAKE,
                OwnerMapClusterFixtures.NEUTRAL_PALETTE,
                DESATURATION_PALETTE,
                OwnerMapClusterFixtures.NEUTRAL_PALETTE),
            new ViewReading(mock(OwnerPaintedView.class), reading, new OwnerSourceFake()),
            contentInputs,
            Set.of(),
            Set.of());
    }
}
