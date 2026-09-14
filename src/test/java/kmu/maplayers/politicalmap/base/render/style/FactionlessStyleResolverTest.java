package kmu.maplayers.politicalmap.base.render.style;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.theme.ElementStyleAdjustment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two rules an ownerless cell draws under: which factionless category it falls in, and
 * whether the pass's recede reaches it. Both are read by every path that paints factionless
 * cells, so what is pinned here is what keeps those paths agreeing.
 */
final class FactionlessStyleResolverTest {

    // An immutable, null-hostile set: Set.of throws on a null probe, so a clean answer for a null
    // system key proves the rule never reached the set with it. Both a dead colony and a living one
    // the pass found no holder for, since the rule must not tell the two apart.
    private static final Set<SystemKey> INHABITED_SYSTEM_KEYS = Set.of(
        buildCellKey("some-decivilised-system"),
        buildCellKey("some-pirate-haven"));

    @Nested
    class ResolveCategoryOf {

        @Test
        void resolveCategoryOfReturnsDecivilisedForASystemHoldingARevealedDeadColony() {

            assertThat(FactionlessStyleResolver.resolveCategoryOf(
                    INHABITED_SYSTEM_KEYS,
                    buildCellKey("some-decivilised-system")))
                .isEqualTo(PoliticalMapCategory.DECIVILISED);
        }

        @Test
        void resolveCategoryOfReturnsDecivilisedForAnInhabitedSystemThePassFoundNoHolderFor() {
            // The claims layer's case: vanilla's claim walk skips a hidden market, and
            // a pirate base is created hidden, so a system settled by pirates alone
            // resolves no claimant and reaches this rule with no holder.
            // It is still inhabited, so it must not fall to the backdrop category the
            // uninhabited-systems checkbox switches off.
            assertThat(FactionlessStyleResolver.resolveCategoryOf(
                    INHABITED_SYSTEM_KEYS,
                    buildCellKey("some-pirate-haven")))
                .isEqualTo(PoliticalMapCategory.DECIVILISED);
        }

        @Test
        void resolveCategoryOfReturnsUninhabitedForASystemOutsideTheInhabitedSet() {

            assertThat(FactionlessStyleResolver.resolveCategoryOf(
                    INHABITED_SYSTEM_KEYS,
                    buildCellKey("never-settled-system")))
                .isEqualTo(PoliticalMapCategory.UNINHABITED);
        }

        @Test
        void resolveCategoryOfReturnsUninhabitedForACellWithNoStarOfItsOwn() {
            // A cell drawn as no system names nothing to look up, so it is uninhabited without the
            // null ID ever probing the set.
            assertThat(FactionlessStyleResolver.resolveCategoryOf(INHABITED_SYSTEM_KEYS, null))
                .isEqualTo(PoliticalMapCategory.UNINHABITED);
        }
    }

    @Nested
    class ResolveRecedeOf {

        private static final ElementStyleAdjustment PASS_RECEDE = new ElementStyleAdjustment(0.5, true);

        // Whether the spotlighted bloc holds a colony in the cell's system. Named at each call so a
        // case reads as the situation it is about rather than as a bare true or false.
        private static final boolean SPOTLIT_BLOC_PRESENT = true;
        private static final boolean SPOTLIT_BLOC_ABSENT = false;

        @Test
        void resolveRecedeOfGivesADecivilisedCellThePassRecede() {

            assertThat(FactionlessStyleResolver.resolveRecedeOf(
                    PoliticalMapCategory.DECIVILISED,
                    PASS_RECEDE,
                    SPOTLIT_BLOC_ABSENT))
                .isEqualTo(PASS_RECEDE);
        }

        @Test
        void resolveRecedeOfSparesASettledCellTheSpotlitBlocLivesIn() {
            // The pick's own colony in a system this layer's holding could not attribute to it - an
            // unclaimed pirate haven under a pirate spotlight. Sinking it would hide the very
            // presence the spotlight was picked to find, so it keeps full strength.
            assertThat(FactionlessStyleResolver.resolveRecedeOf(
                    PoliticalMapCategory.DECIVILISED,
                    PASS_RECEDE,
                    SPOTLIT_BLOC_PRESENT))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }

        @Test
        void resolveRecedeOfLeavesAnUninhabitedCellUnreceded() {
            // The empty backdrop keeps the sector's shape whatever the spotlight does to the blocs
            // drawn over it.
            assertThat(FactionlessStyleResolver.resolveRecedeOf(
                    PoliticalMapCategory.UNINHABITED,
                    PASS_RECEDE,
                    SPOTLIT_BLOC_ABSENT))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }

        @Test
        void resolveRecedeOfLeavesADecivilisedCellUntouchedWhenThePassRecedesNothing() {
            // Off filter the pass's recede is the identity, so the rule is a no-op rather than a
            // path that has to be gated on whether a filter is active.
            assertThat(FactionlessStyleResolver.resolveRecedeOf(
                    PoliticalMapCategory.DECIVILISED,
                    ElementStyleAdjustment.NONE,
                    SPOTLIT_BLOC_ABSENT))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }
    }
}
