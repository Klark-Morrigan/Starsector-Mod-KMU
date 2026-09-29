package kmu.maplayers.ownermap.render.style;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.theme.ElementStyleAdjustment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two rules an ownerless cell draws under: whether it is settled, which is what the layer's
 * declaration turns into its category, and whether the pass's recede reaches it. Both are read by
 * every path that paints ownerless cells, so what is pinned here is what keeps those paths agreeing.
 */
final class FactionlessStyleResolverTests {

    // An immutable, null-hostile set: Set.of throws on a null probe, so a clean answer for a null
    // system key proves the rule never reached the set with it. Both a collapsed colony and a living
    // one the pass found no owner for, since the rule must not tell the two apart.
    private static final Set<SystemKey> INHABITED_SYSTEM_KEYS = Set.of(
        buildCellKey("some-decivilised-system"),
        buildCellKey("some-pirate-haven"));

    // Whether the cell is settled, named at each call so a case reads as the situation it is about.
    private static final boolean SETTLED = true;
    private static final boolean EMPTY = false;

    @Nested
    class IsSettledSystem {

        @Test
        void countsASystemHoldingARevealedDecivilisedColony() {

            assertThat(FactionlessStyleResolver.isSettledSystem(
                    INHABITED_SYSTEM_KEYS,
                    buildCellKey("some-decivilised-system")))
                .isTrue();
        }

        @Test
        void countsAnInhabitedSystemThePassFoundNoOwnerFor() {
            // A layer whose holding rule admits only some markets leaves a system settled solely
            // outside them with no owner, and it reaches this rule that way. It is still inhabited,
            // so it must not fall to the backdrop the uninhabited-systems checkbox switches off.
            assertThat(FactionlessStyleResolver.isSettledSystem(
                    INHABITED_SYSTEM_KEYS,
                    buildCellKey("some-pirate-haven")))
                .isTrue();
        }

        @Test
        void refusesASystemOutsideTheInhabitedSet() {

            assertThat(FactionlessStyleResolver.isSettledSystem(
                    INHABITED_SYSTEM_KEYS,
                    buildCellKey("never-settled-system")))
                .isFalse();
        }

        @Test
        void refusesACellWithNoStarOfItsOwn() {
            // A cell drawn as no system names nothing to look up, so it is empty without the null ID
            // ever probing the set.
            assertThat(FactionlessStyleResolver.isSettledSystem(INHABITED_SYSTEM_KEYS, null))
                .isFalse();
        }
    }

    @Nested
    class ResolveRecedeOf {

        private static final ElementStyleAdjustment PASS_RECEDE = new ElementStyleAdjustment(0.5, true);

        // Whether the spotlighted owner holds a colony in the cell's system. Named at each call so a
        // case reads as the situation it is about rather than as a bare true or false.
        private static final boolean SPOTLIT_BLOC_PRESENT = true;
        private static final boolean SPOTLIT_BLOC_ABSENT = false;

        @Test
        void givesASettledCellThePassRecede() {

            assertThat(FactionlessStyleResolver.resolveRecedeOf(
                    SETTLED,
                    PASS_RECEDE,
                    SPOTLIT_BLOC_ABSENT))
                .isEqualTo(PASS_RECEDE);
        }

        @Test
        void sparesASettledCellTheSpotlitBlocLivesIn() {
            // The pick's own colony in a system this layer's holding could not attribute to it.
            // Sinking it would hide the very
            // presence the spotlight was picked to find, so it keeps full strength.
            assertThat(FactionlessStyleResolver.resolveRecedeOf(
                    SETTLED,
                    PASS_RECEDE,
                    SPOTLIT_BLOC_PRESENT))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }

        @Test
        void leavesAnEmptyCellUnreceded() {
            // The empty backdrop keeps the sector's shape whatever the spotlight does to the blocs
            // drawn over it.
            assertThat(FactionlessStyleResolver.resolveRecedeOf(
                    EMPTY,
                    PASS_RECEDE,
                    SPOTLIT_BLOC_ABSENT))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }

        @Test
        void leavesASettledCellUntouchedWhenThePassRecedesNothing() {
            // Off filter the pass's recede is the identity, so the rule is a no-op rather than a
            // path that has to be gated on whether a filter is active.
            assertThat(FactionlessStyleResolver.resolveRecedeOf(
                    SETTLED,
                    ElementStyleAdjustment.NONE,
                    SPOTLIT_BLOC_ABSENT))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }
    }
}
