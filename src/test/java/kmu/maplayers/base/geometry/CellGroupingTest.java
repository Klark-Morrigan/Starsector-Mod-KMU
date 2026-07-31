package kmu.maplayers.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link CellGrouping}: a cell is resolved to the system it draws as and that system to
 * its grouping key, in two lookups rather than one - so an absorbed wedge keyed to a system
 * that is not its own star, and a shard keyed to no star at all, both resolve correctly.
 *
 * <p>The redistribution pass makes these two kinds of cell real; here they are hand-built,
 * since the resolution reads only the two maps, never any geometry.
 */
final class CellGroupingTest {

    @Nested
    class ResolveDrawnSystemIdOf {

        @Test
        void resolveDrawnSystemIdOfReturnsTheStarACellDrawsAs() {
            // An absorbed wedge draws as system A's star, not as its own id - which is exactly
            // what the two-map indirection exists to express.
            var grouping = new CellGrouping(Map.of("wedge", "A"), Map.of("A", "F"));

            assertThat(grouping.resolveDrawnSystemIdOf("wedge")).isEqualTo("A");
        }

        @Test
        void resolveDrawnSystemIdOfReturnsNullForACellWithNoSystem() {
            // A shard of a dead star's leftover space is absent from the draws-as map, so it
            // has no star of its own.
            var grouping = new CellGrouping(Map.of("A", "A"), Map.of("A", "F"));

            assertThat(grouping.resolveDrawnSystemIdOf("shard")).isNull();
        }
    }

    @Nested
    class ResolveGroupKeyOf {

        @Test
        void resolveGroupKeyOfReturnsTheKeyOfTheStarACellDrawsAs() {
            // The wedge takes A's key through the two lookups, so it paints as A's ground
            // rather than as neutral ground.
            var grouping = new CellGrouping(Map.of("wedge", "A"), Map.of("A", "F"));

            assertThat(grouping.resolveGroupKeyOf("wedge")).isEqualTo("F");
        }

        @Test
        void resolveGroupKeyOfReturnsNullWhenTheCellHasNoSystem() {
            // No star to look a key up through, so a shard is ungrouped whatever the key map says.
            var grouping = new CellGrouping(Map.of("A", "A"), Map.of("A", "F"));

            assertThat(grouping.resolveGroupKeyOf("shard")).isNull();
        }

        @Test
        void resolveGroupKeyOfReturnsNullWhenTheDrawnSystemIsUngrouped() {
            // The cell draws as a real star, but that star holds no market, so it is ungrouped.
            var grouping = new CellGrouping(Map.of("A", "A"), Map.of());

            assertThat(grouping.resolveGroupKeyOf("A")).isNull();
        }
    }

    @Nested
    class GroupCellIdsByKey {

        @Test
        void groupCellIdsByKeyBucketsEachCellUnderTheKeyOfTheStarItDrawsAs() {
            // Two cells drawing as the same system - a star and its absorbed wedge - both land
            // under that key, as two distinct cells a border is later traced from.
            var grouping = new CellGrouping(
                    Map.of("A", "A", "wedge", "A", "B", "B"),
                    Map.of("A", "F", "B", "G"));

            var cellsByKey = grouping.groupCellIdsByKey();

            assertThat(cellsByKey.get("F")).containsExactlyInAnyOrder("A", "wedge");
            assertThat(cellsByKey.get("G")).containsExactly("B");
        }

        @Test
        void groupCellIdsByKeyOmitsCellsWithNoResolvedKey() {
            // A shard (no star) and a cell drawing as an ungrouped star both resolve no key, so
            // neither joins any bucket - they stay neutral ground, grouped under nobody.
            var grouping = new CellGrouping(
                    Map.of("A", "A", "ungrouped", "U"),
                    Map.of("A", "F"));

            var cellsByKey = grouping.groupCellIdsByKey();

            assertThat(cellsByKey).containsOnlyKeys("F");
            assertThat(cellsByKey.get("F")).containsExactly("A");
        }

        @Test
        void groupCellIdsByKeyIsEmptyWhenNothingIsGrouped() {
            var grouping = new CellGrouping(Map.of("A", "A"), Map.of());

            assertThat(grouping.groupCellIdsByKey()).isEmpty();
        }
    }
}
