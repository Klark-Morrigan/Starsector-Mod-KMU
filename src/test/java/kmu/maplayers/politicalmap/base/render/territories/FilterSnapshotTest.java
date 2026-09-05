package kmu.maplayers.politicalmap.base.render.territories;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.render.ContentInputsFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the spotlight state's two answers: what "off filter" is, and when a pass counts as
 * filtering. Both are read by every builder that recedes or names a bloc, so a wrong inert
 * default would recede the whole sector rather than failing.
 */
final class FilterSnapshotTest {

    @Nested
    class Unfiltered {

        @Test
        void unfilteredSpotlightsNothing() {
            assertThat(FilterSnapshot.unfiltered().contentInputs().selectedBlocId())
                .isNull();
        }

        @Test
        void unfilteredRecedesNothingAndContestsNothing() {
            // The identity adjustment is what leaves a bloc painting exactly as it would with no
            // filter at all, and an empty contested set means no cell hatches.
            var snapshot = FilterSnapshot.unfiltered();

            assertThat(snapshot.contentInputs().filterRecedeAdjustment())
                .isEqualTo(ElementStyleAdjustment.NONE);
            assertThat(snapshot.contestedSystemIds())
                .isEmpty();
        }
    }

    @Nested
    class IsFiltering {

        @Test
        void isFilteringIsFalseForTheUnfilteredDefault() {
            assertThat(FilterSnapshot.unfiltered().isFiltering())
                .isFalse();
        }

        @Test
        void isFilteringIsTrueWhenABlocIsSelected() {

            var snapshot = new FilterSnapshot(
                ContentInputsFixtures.createInputsSpotlighting("hegemony"),
                Set.of());

            assertThat(snapshot.isFiltering())
                .isTrue();
        }
    }
}
