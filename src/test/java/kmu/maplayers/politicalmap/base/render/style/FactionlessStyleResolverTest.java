package kmu.maplayers.politicalmap.base.render.style;

import kmu.maplayers.base.theme.ElementStyleAdjustment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two rules an ownerless cell draws under: which factionless category it falls in, and
 * whether the pass's recede reaches it. Both are read by every path that paints factionless
 * cells, so what is pinned here is what keeps those paths agreeing.
 */
final class FactionlessStyleResolverTest {

    // An immutable, null-hostile set: Set.of throws on a null probe, so a clean answer for a null
    // system id proves the rule never reached the set with it. Both a dead colony and a living one
    // the pass found no holder for, since the rule must not tell the two apart.
    private static final Set<String> INHABITED_SYSTEM_IDS =
        Set.of("some-decivilised-system", "some-pirate-haven");

    @Nested
    class ResolveCategoryOf {

        @Test
        void resolveCategoryOfReturnsDecivilisedForASystemHoldingARevealedDeadColony() {
            assertThat(FactionlessStyleResolver.resolveCategoryOf(
                    INHABITED_SYSTEM_IDS,
                    "some-decivilised-system"))
                .isEqualTo(PoliticalMapCategory.DECIVILISED);
        }

        @Test
        void resolveCategoryOfReturnsDecivilisedForAnInhabitedSystemThePassFoundNoHolderFor() {
            // The claims layer's case: vanilla lets only a territorial faction claim, so a system
            // settled by pirates alone resolves no claimant and reaches this rule with no holder.
            // It is still inhabited, so it must not fall to the backdrop category the
            // uninhabited-systems checkbox switches off.
            assertThat(FactionlessStyleResolver.resolveCategoryOf(
                    INHABITED_SYSTEM_IDS,
                    "some-pirate-haven"))
                .isEqualTo(PoliticalMapCategory.DECIVILISED);
        }

        @Test
        void resolveCategoryOfReturnsUninhabitedForASystemOutsideTheInhabitedSet() {
            assertThat(FactionlessStyleResolver.resolveCategoryOf(
                    INHABITED_SYSTEM_IDS,
                    "never-settled-system"))
                .isEqualTo(PoliticalMapCategory.UNINHABITED);
        }

        @Test
        void resolveCategoryOfReturnsUninhabitedForACellWithNoStarOfItsOwn() {
            // A cell drawn as no system names nothing to look up, so it is uninhabited without the
            // null id ever probing the set.
            assertThat(FactionlessStyleResolver.resolveCategoryOf(INHABITED_SYSTEM_IDS, null))
                .isEqualTo(PoliticalMapCategory.UNINHABITED);
        }
    }

    @Nested
    class ResolveRecedeOf {

        private static final ElementStyleAdjustment PASS_RECEDE = new ElementStyleAdjustment(0.5, true);

        @Test
        void resolveRecedeOfGivesADecivilisedCellThePassRecede() {
            assertThat(FactionlessStyleResolver.resolveRecedeOf(
                    PoliticalMapCategory.DECIVILISED,
                    PASS_RECEDE))
                .isEqualTo(PASS_RECEDE);
        }

        @Test
        void resolveRecedeOfLeavesAnUninhabitedCellUnreceded() {
            // The empty backdrop keeps the sector's shape whatever the spotlight does to the blocs
            // drawn over it.
            assertThat(FactionlessStyleResolver.resolveRecedeOf(
                    PoliticalMapCategory.UNINHABITED,
                    PASS_RECEDE))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }

        @Test
        void resolveRecedeOfLeavesADecivilisedCellUntouchedWhenThePassRecedesNothing() {
            // Off filter the pass's recede is the identity, so the rule is a no-op rather than a
            // path that has to be gated on whether a filter is active.
            assertThat(FactionlessStyleResolver.resolveRecedeOf(
                    PoliticalMapCategory.DECIVILISED,
                    ElementStyleAdjustment.NONE))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }
    }
}
