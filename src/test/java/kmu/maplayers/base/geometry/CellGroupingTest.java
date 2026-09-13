package kmu.maplayers.base.geometry;

import kmlib.starsector.systems.SystemKey;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildDrawnSystemKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedOwners;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link CellGrouping}: a cell is resolved to the system it draws as and that system to
 * its owner, in two lookups rather than one - so an absorbed wedge keyed to a system
 * that is not its own star, and a shard keyed to no star at all, both resolve correctly.
 *
 * <p>The redistribution pass makes these two kinds of cell real; here they are hand-built,
 * since the resolution reads only the two maps, never any geometry.
 */
final class CellGroupingTest {

    @Nested
    class ResolveDrawnSystemKeyOf {

        @Test
        void resolveDrawnSystemKeyOfReturnsTheStarACellDrawsAs() {
            // An absorbed wedge draws as system A's star, not as its own cell - which is exactly
            // what the two-map indirection exists to express.
            var grouping = new CellGrouping(
                buildDrawnSystemKeys(Map.of("wedge", "A")),
                buildKeyedOwners(Map.of("A", "F")));

            assertThat(grouping.resolveDrawnSystemKeyOf(buildCellKey("wedge")))
                .isEqualTo(buildCellKey("A"));
        }

        @Test
        void resolveDrawnSystemKeyOfReturnsNullForACellWithNoSystem() {
            // A shard of a dead star's leftover space is absent from the draws-as map, so it
            // has no star of its own.
            var grouping = new CellGrouping(
                buildDrawnSystemKeys(Map.of("A", "A")),
                buildKeyedOwners(Map.of("A", "F")));

            assertThat(grouping.resolveDrawnSystemKeyOf(buildCellKey("shard"))).isNull();
        }
    }

    @Nested
    class ResolveGroupKeyOf {

        @Test
        void resolveGroupKeyOfReturnsTheKeyOfTheStarACellDrawsAs() {
            // The wedge takes A's key through the two lookups, so it paints as A's
            // rather than as unowned.
            var grouping = new CellGrouping(
                buildDrawnSystemKeys(Map.of("wedge", "A")),
                buildKeyedOwners(Map.of("A", "F")));

            assertThat(grouping.resolveOwnerOf(buildCellKey("wedge"))).isEqualTo("F");
        }

        @Test
        void resolveGroupKeyOfReturnsNullWhenTheCellHasNoSystem() {
            // No star to look a key up through, so a shard is unowned whatever the key map says.
            var grouping = new CellGrouping(
                buildDrawnSystemKeys(Map.of("A", "A")),
                buildKeyedOwners(Map.of("A", "F")));

            assertThat(grouping.resolveOwnerOf(buildCellKey("shard"))).isNull();
        }

        @Test
        void resolveGroupKeyOfReturnsNullWhenTheDrawnSystemIsUnowned() {
            // The cell draws as a real star, but that star holds no market, so it is unowned.
            var grouping = new CellGrouping(buildDrawnSystemKeys(Map.of("A", "A")), Map.of());

            assertThat(grouping.resolveOwnerOf(buildCellKey("A"))).isNull();
        }
    }

    @Nested
    class GroupCellKeysByOwner {

        @Test
        void groupCellKeysByOwnerBucketsEachCellUnderTheKeyOfTheStarItDrawsAs() {
            // Two cells drawing as the same system - a star and its absorbed wedge - both land
            // under that key, as two distinct cells a border is later traced from.
            var grouping = new CellGrouping(
                    buildDrawnSystemKeys(Map.of("A", "A", "wedge", "A", "B", "B")),
                    buildKeyedOwners(Map.of("A", "F", "B", "G")));

            var cellsByKey = grouping.groupCellKeysByOwner();

            assertThat(cellsByKey.get("F"))
                .containsExactlyInAnyOrderElementsOf(buildCellKeys("A", "wedge"));
            assertThat(cellsByKey.get("G")).containsExactly(buildCellKey("B"));
        }

        @Test
        void groupCellKeysByOwnerOmitsCellsWithNoResolvedKey() {
            // A shard (no star) and a cell drawing as an unowned star both resolve no key, so
            // neither joins any bucket - they stay unowned, grouped under nobody.
            var grouping = new CellGrouping(
                buildDrawnSystemKeys(Map.of("A", "A", "unowned", "U")),
                buildKeyedOwners(Map.of("A", "F")));

            var cellsByKey = grouping.groupCellKeysByOwner();

            assertThat(cellsByKey).containsOnlyKeys("F");
            assertThat(cellsByKey.get("F")).containsExactly(buildCellKey("A"));
        }

        @Test
        void groupCellKeysByOwnerIsEmptyWhenNothingIsGrouped() {
            var grouping = new CellGrouping(buildDrawnSystemKeys(Map.of("A", "A")), Map.of());

            assertThat(grouping.groupCellKeysByOwner()).isEmpty();
        }
    }

    @Nested
    class TwoSystemsSharingAnId {

        @Test
        void twoCellsWhoseSystemsShareAnIdResolveTheirOwnHolders() {
            // The collision the one shared address is here to survive: both halves are keyed by
            // SystemKey, so a pair sharing a vanilla id can carry two different owners and each
            // cell groups under its own.
            var first = new SystemKey("deep space", "", "8b3");
            var second = new SystemKey("deep space", "", "38d53");
            var grouping = new CellGrouping(
                Map.of(first, first, second, second),
                Map.of(first, "F", second, "G"));

            var cellsByKey = grouping.groupCellKeysByOwner();

            assertThat(cellsByKey.get("F")).containsExactly(first);
            assertThat(cellsByKey.get("G")).containsExactly(second);
            assertThat(grouping.resolveOwnerOf(second)).isEqualTo("G");
        }
    }
}
