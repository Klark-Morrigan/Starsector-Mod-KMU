package kmu.maplayers.politicalmap.base.render.style;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.render.style.theme.MapCategory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two rules ownerless ground draws under: which factionless category it falls in, and
 * whether the pass's recede reaches it. Both are read by every path that paints factionless
 * ground, so what is pinned here is what keeps those paths agreeing.
 */
final class FactionlessStyleResolverTest {

    // An immutable, null-hostile set: Set.of throws on a null probe, so a clean answer for a null
    // system id proves the rule never reached the set with it.
    private static final Set<String> DECIVILISED_SYSTEM_IDS = Set.of("some-decivilised-system");

    @Nested
    class ResolveCategoryOf {

        @Test
        void resolveCategoryOfReturnsDecivilisedForASystemHoldingARevealedDeadColony() {
            assertThat(FactionlessStyleResolver.resolveCategoryOf(
                    DECIVILISED_SYSTEM_IDS, "some-decivilised-system"))
                    .isEqualTo(MapCategory.DECIVILISED);
        }

        @Test
        void resolveCategoryOfReturnsUninhabitedForASystemOutsideTheDecivilisedSet() {
            assertThat(FactionlessStyleResolver.resolveCategoryOf(
                    DECIVILISED_SYSTEM_IDS, "never-settled-system"))
                    .isEqualTo(MapCategory.UNINHABITED);
        }

        @Test
        void resolveCategoryOfReturnsUninhabitedForGroundWithNoStarOfItsOwn() {
            // Ground drawn as no system names nothing to look up, so it is uninhabited without the
            // null id ever probing the set.
            assertThat(FactionlessStyleResolver.resolveCategoryOf(DECIVILISED_SYSTEM_IDS, null))
                    .isEqualTo(MapCategory.UNINHABITED);
        }
    }

    @Nested
    class ResolveRecedeOf {

        private static final BlocStyleAdjustment PASS_RECEDE = new BlocStyleAdjustment(0.5, true);

        @Test
        void resolveRecedeOfGivesDecivilisedGroundThePassRecede() {
            assertThat(FactionlessStyleResolver.resolveRecedeOf(
                    MapCategory.DECIVILISED, PASS_RECEDE)).isEqualTo(PASS_RECEDE);
        }

        @Test
        void resolveRecedeOfLeavesUninhabitedGroundUnreceded() {
            // The empty backdrop keeps the sector's shape whatever the spotlight does to the blocs
            // drawn over it.
            assertThat(FactionlessStyleResolver.resolveRecedeOf(
                    MapCategory.UNINHABITED, PASS_RECEDE)).isEqualTo(BlocStyleAdjustment.NONE);
        }

        @Test
        void resolveRecedeOfLeavesDecivilisedGroundUntouchedWhenThePassRecedesNothing() {
            // Off filter the pass's recede is the identity, so the rule is a no-op rather than a
            // path that has to be gated on whether a filter is active.
            assertThat(FactionlessStyleResolver.resolveRecedeOf(
                    MapCategory.DECIVILISED, BlocStyleAdjustment.NONE))
                    .isEqualTo(BlocStyleAdjustment.NONE);
        }
    }
}
