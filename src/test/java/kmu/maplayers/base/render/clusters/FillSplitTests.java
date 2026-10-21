package kmu.maplayers.base.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.render.clusters.FillSplit.FillState;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildDrawnSystemKeys;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the pure partition behind a cluster's fill: how the members land in the three buckets at
 * both the cell and system level, and which systems one state names as its coincident neighbours.
 * All of it decidable from plain values, which is why it lives apart from the tessellation in
 * {@link SplitFillBuilder}. Which state one system draws in is {@link SystemFillStatesTests}'s.
 *
 * <p>The members and the fill states share one address, so a case states its systems by key
 * throughout.
 */
final class FillSplitTests {

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
    class SplitMembersByFillState {

        @Test
        void putsEachMemberSystemInItsOwnState() {

            var split = splitFootprint();

            assertThat(split.solid().systemKeys())
                .containsExactly(SOLID_SYSTEM);
            assertThat(split.hatched().systemKeys())
                .containsExactly(HATCHED_SYSTEM);
            assertThat(split.unfilled().systemKeys())
                .containsExactly(UNFILLED_SYSTEM);
        }

        @Test
        void keepsAStarlessCellAmongTheCellsButNotTheSystems() {
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
        void isTrueWhenTheFootprintHoldsAHatchedMember() {

            assertThat(splitFootprint().hasNonSolidMembers())
                .isTrue();
        }

        @Test
        void isFalseWhenEveryMemberFillsSolid() {
            // The fast path a cluster takes to fill as one area: nothing to split apart.
            var split = FillSplit.splitMembersByFillState(
                buildGroupingOf(Map.of("cell-solid", SOLID_SYSTEM_ID)),
                buildCellKeys("cell-solid"),
                SystemFillStates.ALL_SOLID);

            assertThat(split.hasNonSolidMembers())
                .isFalse();
        }
    }

    @Nested
    class ResolveCoincidentSystemKeysOf {

        @Test
        void returnsTheOtherTwoStatesSystems() {
            // The solid fill must stop flush against both its hatched and its unfilled
            // neighbours, so both appear - the unfilled state included, though it paints nothing.
            assertThat(splitFootprint().resolveCoincidentSystemKeysOf(FillState.SOLID))
                .containsExactlyInAnyOrder(HATCHED_SYSTEM, UNFILLED_SYSTEM);
        }

        @Test
        void excludesTheStatesOwnSystems() {

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
            SystemFillStates.createOf(Map.of(
                HATCHED_SYSTEM,
                FillState.HATCHED,
                UNFILLED_SYSTEM,
                FillState.UNFILLED)));
    }

    // A grouping that only has to answer "which system does this cell draw as" - the split reads
    // nothing else off it, and a cell absent from the map resolves to no system at all.
    private static CellGrouping buildGroupingOf(Map<String, String> systemIdByCellId) {
        return new CellGrouping(buildDrawnSystemKeys(systemIdByCellId), Map.of());
    }
}
