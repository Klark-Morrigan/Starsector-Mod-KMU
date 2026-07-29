package kmu.maplayers.base.render.regions;

import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.render.regions.FillSplit.FillState;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the pure partition behind a bloc footprint's fill: which state each member system draws
 * in, how the members land in the three buckets at both the cell and system level, and which
 * systems one state names as its coincident neighbours. All of it decidable from plain id sets,
 * which is why it lives apart from the tessellation in {@link SplitFillBuilder}.
 */
final class FillSplitTest {
    private static final String SOLID_SYSTEM = "solid-system";
    private static final String CONTESTED_SYSTEM = "contested-system";
    private static final String UNFILLED_SYSTEM = "unfilled-system";
    // A cell holding ground its bloc owns without a star of its own in it - present among the
    // members' cells, absent from their systems.
    private static final String STARLESS_CELL = "starless-cell";

    @Nested
    class ClassifyFillState {

        private static final String SYSTEM = "some-system";

        @Test
        void classifyFillStateReturnsSolidWhenTheSystemIsNeitherContestedNorUnfilled() {
            assertThat(FillSplit.classifyFillState(SYSTEM, Set.of(), Set.of()))
                    .isEqualTo(FillState.SOLID);
        }

        @Test
        void classifyFillStateReturnsHatchedWhenTheSystemIsContested() {
            assertThat(FillSplit.classifyFillState(SYSTEM, Set.of(SYSTEM), Set.of()))
                    .isEqualTo(FillState.HATCHED);
        }

        @Test
        void classifyFillStateReturnsUnfilledWhenTheSystemIsUnfilled() {
            assertThat(FillSplit.classifyFillState(SYSTEM, Set.of(), Set.of(SYSTEM)))
                    .isEqualTo(FillState.UNFILLED);
        }

        @Test
        void classifyFillStateFavoursUnfilledOverHatchedWhenTheSystemIsBoth() {
            // A system drawn empty is empty however dominance falls, so unfilled wins the tie.
            assertThat(FillSplit.classifyFillState(SYSTEM, Set.of(SYSTEM), Set.of(SYSTEM)))
                    .isEqualTo(FillState.UNFILLED);
        }

        @Test
        void classifyFillStateReturnsSolidForACellWithNoStarOfItsOwn() {
            // A null system id has no per-system fill state, so it fills solid with the bloc's held
            // ground rather than probing either exception set with a null key.
            assertThat(FillSplit.classifyFillState(null, Set.of("other"), Set.of("other")))
                    .isEqualTo(FillState.SOLID);
        }
    }

    @Nested
    class SplitMembersByFillState {

        @Test
        void splitMembersByFillStatePutsEachMemberSystemInItsOwnState() {
            var split = splitFootprint();

            assertThat(split.solid().systemIds()).containsExactly(SOLID_SYSTEM);
            assertThat(split.hatched().systemIds()).containsExactly(CONTESTED_SYSTEM);
            assertThat(split.unfilled().systemIds()).containsExactly(UNFILLED_SYSTEM);
        }

        @Test
        void splitMembersByFillStateKeepsAStarlessCellAmongTheCellsButNotTheSystems() {
            // A cell with no star of its own is real ground to trace a region from, so it joins
            // the solid state's cells - but it names no system, so nothing may key or mark it.
            var split = splitFootprint();

            assertThat(split.solid().cellIds()).contains(STARLESS_CELL);
            assertThat(split.solid().systemIds()).doesNotContain(STARLESS_CELL);
        }
    }

    @Nested
    class HasNonSolidMembers {

        @Test
        void hasNonSolidMembersIsTrueWhenTheFootprintHoldsContestedGround() {
            assertThat(splitFootprint().hasNonSolidMembers()).isTrue();
        }

        @Test
        void hasNonSolidMembersIsFalseWhenEveryMemberFillsSolid() {
            // The fast path a territory takes to fill as one region: nothing to split apart.
            var split = FillSplit.splitMembersByFillState(
                    groupingOf(Map.of("cell-solid", SOLID_SYSTEM)),
                    List.of("cell-solid"),
                    Set.of(),
                    Set.of());

            assertThat(split.hasNonSolidMembers()).isFalse();
        }
    }

    @Nested
    class ResolveCoincidentSystemIdsOf {

        @Test
        void resolveCoincidentSystemIdsOfReturnsTheOtherTwoStatesSystems() {
            // The solid fill must stop flush against both its contested and its held-but-empty
            // neighbours, so both appear - the unfilled state included, though it paints nothing.
            assertThat(splitFootprint().resolveCoincidentSystemIdsOf(FillState.SOLID))
                    .containsExactlyInAnyOrder(CONTESTED_SYSTEM, UNFILLED_SYSTEM);
        }

        @Test
        void resolveCoincidentSystemIdsOfExcludesTheStatesOwnSystems() {
            assertThat(splitFootprint().resolveCoincidentSystemIdsOf(FillState.HATCHED))
                    .doesNotContain(CONTESTED_SYSTEM);
        }
    }

    // One footprint holding a member in each of the three states plus a starless cell, so a
    // single fixture exercises every bucket and the cell/system split at once.
    private static FillSplit splitFootprint() {
        return FillSplit.splitMembersByFillState(
                groupingOf(Map.of(
                        "cell-solid", SOLID_SYSTEM,
                        "cell-contested", CONTESTED_SYSTEM,
                        "cell-unfilled", UNFILLED_SYSTEM)),
                List.of("cell-solid", "cell-contested", "cell-unfilled", STARLESS_CELL),
                Set.of(CONTESTED_SYSTEM),
                Set.of(UNFILLED_SYSTEM));
    }

    // A grouping that only has to answer "which system does this cell draw as" - the split reads
    // nothing else off it, and a cell absent from the map resolves to no system at all.
    private static CellGrouping groupingOf(Map<String, String> systemIdByCellId) {
        return new CellGrouping(systemIdByCellId, Map.of());
    }
}
