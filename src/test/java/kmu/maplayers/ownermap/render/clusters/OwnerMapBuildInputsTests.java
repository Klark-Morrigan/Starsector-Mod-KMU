package kmu.maplayers.ownermap.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.style.HolderCategories;
import kmu.maplayers.ownermap.render.style.OwnerMapCategory;

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
 * threads each snapshot and each derived set into its matching accessor, and that an owner's paint
 * cascades the record's own reading, picks and shades rather than any handed in beside them.
 *
 * <p>The cascade's rule is {@link kmu.maplayers.ownermap.render.style.OwnerStylingTests}'s
 * and what an element then paints is
 * {@link kmu.maplayers.ownermap.render.style.ResolvedBlocPaintTests}'s; what belongs here
 * is that both are asked over this record's own snapshots, which is the whole reason the
 * composition sits on the type that holds them.
 */
final class OwnerMapBuildInputsTests {

    private static final String BLOC_ID = "hegemony";

    // Two distinct sets so a swapped slot is caught by identity, held apart from each other.
    private static final Set<SystemKey> UNFILLED = Set.of(buildCellKey("unfilled-system"));
    private static final Set<SystemKey> CONTESTED = Set.of(buildCellKey("contested-system"));

    // The bloc a paint is resolved for, and the shades the record sinks a desaturated one to.
    // Distinct from the holder's own pair, so a resolved palette names which of the two it took.
    private static final SystemOwner HOLDER =
        new SystemOwner(BLOC_ID, new OwnerPalette(Color.RED, Color.BLUE));

    private static final OwnerPalette DESATURATION_PALETTE =
        new OwnerPalette(Color.GREEN, Color.YELLOW);

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

            // The empty fallback is never a filtered build, so it selects no bloc, recedes nothing
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
                HolderGrouping.identity());
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
    class ResolveBlocPaintOf {

        @Test
        void cascadesTheRecordsOwnReadingAndPicks() {
            // Off filter the decision is the reading's own call, asked with the picks this build was
            // baked under - so the reading sees exactly the record's other snapshot, and the
            // adjustment it answers is the one the owner draws under.
            var adjustment = new ElementStyleAdjustment(0.5, false);
            var contentInputs = ContentInputsFixtures.createInertInputs();
            var readingMock = buildReadingAdjustingBy(adjustment);

            var paint = buildInputsUnder(readingMock, contentInputs)
                .resolveBlocPaintOf(BLOC_ID, HOLDER);

            assertThat(paint.adjustment())
                .isSameAs(adjustment);
            assertThat(paint.style())
                .isNotNull();

            verify(readingMock).resolveStyleAdjustment(BLOC_ID, contentInputs);
        }

        @Test
        void sinksADesaturatedOwnerToTheRetainedDesaturationShades() {
            // The shades come off the record's own styling rather than from the caller, which is
            // what keeps an owner's fill, its border and its cells' seams sunk to one grey: a
            // builder reaching for the palette itself is a builder that could reach a different
            // build's.
            var paint = buildInputsUnder(
                    buildReadingAdjustingBy(new ElementStyleAdjustment(1.0, true)),
                    ContentInputsFixtures.createInertInputs())
                .resolveBlocPaintOf(BLOC_ID, HOLDER);

            assertThat(paint.palette())
                .isSameAs(DESATURATION_PALETTE);
        }

        // A reading that recedes every owner by the stated adjustment and keeps every one at full
        // strength, so what a case reads back can only have come from the record.
        private static OwnerReading buildReadingAdjustingBy(ElementStyleAdjustment adjustment) {

            var readingMock = mock(OwnerReading.class);

            when(readingMock.resolveStyleAdjustment(any(), any()))
                .thenReturn(adjustment);
            when(readingMock.resolveCategory(any(), any()))
                .thenReturn(OwnerMapCategory.FACTION);

            return readingMock;
        }
    }

    // Inputs over a real theme, the holder layers' categories and a distinct desaturation palette,
    // so a paint resolved through them names which of the record's own shades it took.
    private static OwnerMapBuildInputs buildInputsUnder(
            OwnerReading reading,
            ContentInputs contentInputs) {

        return new OwnerMapBuildInputs(
            new MapStyling(
                OwnerMapClusterFixtures.createRenderStyleForEveryCategory(
                    OwnerMapClusterFixtures.createInertCategoryStyle()),
                HolderCategories.INSTANCE,
                OwnerMapClusterFixtures.NEUTRAL_PALETTE,
                DESATURATION_PALETTE,
                OwnerMapClusterFixtures.NEUTRAL_PALETTE),
            new ViewReading(mock(OwnerPaintedView.class), reading, HolderGrouping.identity()),
            contentInputs,
            Set.of(),
            Set.of());
    }
}
