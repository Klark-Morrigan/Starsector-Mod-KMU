package kmu.maplayers.base.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.render.clusters.FillSplit.FillState;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildDrawnSystemKeys;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the pure partition behind a cluster's fill: which state each member system draws
 * in, how the members land in the three buckets at both the cell and system level, and which
 * systems one state names as its coincident neighbours. All of it decidable from plain sets,
 * which is why it lives apart from the tessellation in {@link SplitFillBuilder}.
 *
 * <p>The members are addressed by key and the two exception sets by id, as the layer's holding
 * still is, so the constants come in both spellings where a case reads both.
 */
final class FillSplitTest {

    private static final String SOLID_SYSTEM_ID = "solid-system";
    private static final String HATCHED_SYSTEM_ID = "hatched-system";
    private static final String UNFILLED_SYSTEM_ID = "unfilled-system";

    private static final SystemKey SOLID_SYSTEM = buildCellKey(SOLID_SYSTEM_ID);
    private static final SystemKey HATCHED_SYSTEM = buildCellKey(HATCHED_SYSTEM_ID);
    private static final SystemKey UNFILLED_SYSTEM = buildCellKey(UNFILLED_SYSTEM_ID);

    // A cell its cluster covers without a star of its own in it - present among the
    // members' cells, absent from their systems.
    private static final SystemKey STARLESS_CELL = buildCellKey("starless-cell");

    @Nested
    class ClassifyFillState {

        private static final String SYSTEM_ID = "some-system";
        private static final SystemKey SYSTEM = buildCellKey(SYSTEM_ID);

        @Test
        void classifyFillStateReturnsSolidWhenTheSystemIsNeitherHatchedNorUnfilled() {

            assertThat(FillSplit.classifyFillState(SYSTEM, Set.of(), Set.of()))
                .isEqualTo(FillState.SOLID);
        }

        @Test
        void classifyFillStateReturnsHatchedWhenTheSystemIsHatched() {

            assertThat(FillSplit.classifyFillState(SYSTEM, Set.of(SYSTEM_ID), Set.of()))
                .isEqualTo(FillState.HATCHED);
        }

        @Test
        void classifyFillStateReturnsUnfilledWhenTheSystemIsUnfilled() {

            assertThat(FillSplit.classifyFillState(SYSTEM, Set.of(), Set.of(SYSTEM_ID)))
                .isEqualTo(FillState.UNFILLED);
        }

        @Test
        void classifyFillStateFavoursUnfilledOverHatchedWhenTheSystemIsBoth() {
            // A system drawn empty is empty whatever else the layer says about it, so unfilled
            // wins the tie.
            assertThat(FillSplit.classifyFillState(SYSTEM, Set.of(SYSTEM_ID), Set.of(SYSTEM_ID)))
                .isEqualTo(FillState.UNFILLED);
        }

        @Test
        void classifyFillStateReturnsSolidForACellWithNoStarOfItsOwn() {
            // A null system has no per-system fill state, so it fills solid with the rest of the
            // cluster rather than probing either exception set with a null key.
            assertThat(FillSplit.classifyFillState(null, Set.of("other"), Set.of("other")))
                .isEqualTo(FillState.SOLID);
        }

        @Test
        void classifyFillStateNarrowsTheKeyToTheIdTheExceptionSetsAreKeyedBy() {
            // The join with the holding: the sets name the system by id, so a key whose other arms
            // are stated still meets the set through its id arm.
            var anchored = new SystemKey(SYSTEM_ID, "", "8b3");

            assertThat(FillSplit.classifyFillState(anchored, Set.of(SYSTEM_ID), Set.of()))
                .isEqualTo(FillState.HATCHED);
        }
    }

    @Nested
    class SplitMembersByFillState {

        @Test
        void splitMembersByFillStatePutsEachMemberSystemInItsOwnState() {

            var split = splitFootprint();

            assertThat(split.solid().systemKeys())
                .containsExactly(SOLID_SYSTEM);
            assertThat(split.hatched().systemKeys())
                .containsExactly(HATCHED_SYSTEM);
            assertThat(split.unfilled().systemKeys())
                .containsExactly(UNFILLED_SYSTEM);
        }

        @Test
        void splitMembersByFillStateKeepsAStarlessCellAmongTheCellsButNotTheSystems() {
            // A cell with no star of its own is still real area to trace a cluster from, so it joins
            // the solid state's cells - but it names no system, so nothing may key or mark it.
            var split = splitFootprint();

            assertThat(split.solid().cellKeys())
                .contains(STARLESS_CELL);
            assertThat(split.solid().systemKeys())
                .doesNotContain(STARLESS_CELL);
        }
    }

    @Nested
    class HasNonSolidMembers {

        @Test
        void hasNonSolidMembersIsTrueWhenTheFootprintHoldsAHatchedMember() {

            assertThat(splitFootprint().hasNonSolidMembers())
                .isTrue();
        }

        @Test
        void hasNonSolidMembersIsFalseWhenEveryMemberFillsSolid() {
            // The fast path a cluster takes to fill as one area: nothing to split apart.
            var split = FillSplit.splitMembersByFillState(
                buildGroupingOf(Map.of("cell-solid", SOLID_SYSTEM_ID)),
                buildCellKeys("cell-solid"),
                Set.of(),
                Set.of());

            assertThat(split.hasNonSolidMembers())
                .isFalse();
        }
    }

    @Nested
    class ResolveCoincidentSystemKeysOf {

        @Test
        void resolveCoincidentSystemKeysOfReturnsTheOtherTwoStatesSystems() {
            // The solid fill must stop flush against both its hatched and its unfilled
            // neighbours, so both appear - the unfilled state included, though it paints nothing.
            assertThat(splitFootprint().resolveCoincidentSystemKeysOf(FillState.SOLID))
                .containsExactlyInAnyOrder(HATCHED_SYSTEM, UNFILLED_SYSTEM);
        }

        @Test
        void resolveCoincidentSystemKeysOfExcludesTheStatesOwnSystems() {

            assertThat(splitFootprint().resolveCoincidentSystemKeysOf(FillState.HATCHED))
                .doesNotContain(HATCHED_SYSTEM);
        }
    }

    // One footprint holding a member in each of the three states plus a starless cell, so a
    // single fixture exercises every bucket and the cell/system split at once.
    private static FillSplit splitFootprint() {
        return FillSplit.splitMembersByFillState(
            buildGroupingOf(Map.of(
                "cell-solid", SOLID_SYSTEM_ID,
                "cell-hatched", HATCHED_SYSTEM_ID,
                "cell-unfilled", UNFILLED_SYSTEM_ID)),
            List.of(
                buildCellKey("cell-solid"),
                buildCellKey("cell-hatched"),
                buildCellKey("cell-unfilled"),
                STARLESS_CELL),
            Set.of(HATCHED_SYSTEM_ID),
            Set.of(UNFILLED_SYSTEM_ID));
    }

    // A grouping that only has to answer "which system does this cell draw as" - the split reads
    // nothing else off it, and a cell absent from the map resolves to no system at all.
    private static CellGrouping buildGroupingOf(Map<String, String> systemIdByCellId) {
        return new CellGrouping(buildDrawnSystemKeys(systemIdByCellId), Map.of());
    }
}
