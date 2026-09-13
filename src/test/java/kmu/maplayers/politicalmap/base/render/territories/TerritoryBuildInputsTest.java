package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.render.ContentInputsFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what the retained inputs answer for themselves: that the empty placeholder carries the
 * view it was built for over inert stand-ins, that the constructor threads each snapshot and each
 * derived set into its matching accessor, and that a bloc's styling cascades the record's own
 * view, grouping and picks rather than any handed in beside them.
 *
 * <p>The cascade's rule is {@link kmu.maplayers.politicalmap.base.render.style.BlocStylingTest}'s;
 * what belongs here is that it is asked over this record's three snapshots, which is the whole
 * reason the composition sits on the type that holds all three.
 */
final class TerritoryBuildInputsTest {

    private static final String BLOC_ID = "hegemony";

    // Two distinct sets so a swapped slot is caught by identity, held apart from each other.
    private static final Set<SystemKey> UNFILLED = Set.of(buildCellKey("unfilled-system"));
    private static final Set<SystemKey> CONTESTED = Set.of(buildCellKey("contested-system"));

    @Nested
    class CreateEmpty {

        @Test
        void createEmptyCarriesTheViewOverInertStandIns() {

            var viewMock = mock(PoliticalMapView.class);
            var inputs = TerritoryBuildInputs.createEmpty(viewMock);

            // The view is the one thing a later frame reads off the placeholder - which panel a
            // fallback was drawn for - so it is the view it was built for rather than a stand-in.
            assertThat(inputs.viewGrouping().view())
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
        void accessorsReturnEachConstructorInputInItsMatchingSlot() {

            var styling = MapStyling.createEmpty();
            var viewGrouping = new ViewGrouping(mock(PoliticalMapView.class), HolderGrouping.identity());
            var contentInputs = ContentInputsFixtures.createInertInputs();

            var inputs = new TerritoryBuildInputs(
                styling,
                viewGrouping,
                contentInputs,
                UNFILLED,
                CONTESTED);

            // By identity throughout: the two sets are the same type in adjacent slots, so only
            // distinct instances catch a swap between them.
            assertThat(inputs.styling())
                .isSameAs(styling);
            assertThat(inputs.viewGrouping())
                .isSameAs(viewGrouping);
            assertThat(inputs.contentInputs())
                .isSameAs(contentInputs);
            assertThat(inputs.unfilledSystemKeys())
                .isSameAs(UNFILLED);
            assertThat(inputs.contestedSystemKeys())
                .isSameAs(CONTESTED);
        }
    }

    @Nested
    class ResolveBlocStyling {

        @Test
        void resolveBlocStylingCascadesTheRecordsOwnViewGroupingAndPicks() {
            // Off filter the decision is the view's own call, asked under the grouping and the
            // picks this build was baked under - so the view sees exactly the record's two other
            // snapshots, and the adjustment it answers is the one the bloc draws under.
            var adjustment = new ElementStyleAdjustment(0.5, true);
            var viewMock = mock(PoliticalMapView.class);

            when(viewMock.resolveBlocStyleAdjustment(any(), any(), any()))
                .thenReturn(adjustment);
            when(viewMock.shouldUseIndependentStyle(any(), any(), any()))
                .thenReturn(false);

            var grouping = HolderGrouping.identity();
            var contentInputs = ContentInputsFixtures.createInertInputs();

            var inputs = new TerritoryBuildInputs(
                new MapStyling(
                    PoliticalMapTerritoryFixtures.createRenderStyleForEveryCategory(
                        PoliticalMapTerritoryFixtures.createInertCategoryStyle()),
                    PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE,
                    PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE,
                    PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE),
                new ViewGrouping(viewMock, grouping),
                contentInputs,
                Set.of(),
                Set.of());

            var styling = inputs.resolveBlocStyling(BLOC_ID);

            assertThat(styling.adjustment())
                .isSameAs(adjustment);
            assertThat(styling.style())
                .isNotNull();

            verify(viewMock).resolveBlocStyleAdjustment(BLOC_ID, grouping, contentInputs);
        }
    }
}
