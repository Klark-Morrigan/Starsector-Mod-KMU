package kmu.maplayers.base.geometry;

import kmlib.starsector.systems.SystemKey;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the contract of {@link CellHitTest#resolveSystemKeyAt}:
 *  - a point within a cell resolves to that cell's system,
 *  - a point in the channel between cells, or beyond them all, resolves to nothing,
 *  - a point two abutting cells could both claim resolves the same way every call,
 *  - a map with no drawable cell resolves to nothing.
 *
 * <p>The cells here are hand-built squares: the hit test reads only the polygons it is
 * handed, so no shaping, styling, or GL is involved in what it answers.
 */
final class CellHitTestTest {

    // An axis-aligned square cell, counter-clockwise, spanning [minX, minX + side] x
    // [minY, minY + side]. Stands in for a shaped fill polygon; the test cares only
    // that it encloses a known area.
    private static List<double[]> buildSquare(double minX, double minY, double side) {
        return List.of(
                new double[] {minX, minY},
                new double[] {minX + side, minY},
                new double[] {minX + side, minY + side},
                new double[] {minX, minY + side});
    }

    @Nested
    class ResolveSystemKeyAt {
        @Test
        void aPointInsideACellResolvesToItsSystem() {
            var cells = buildKeyedValues(Map.of("A", buildSquare(0, 0, 10)));

            assertThat(CellHitTest.resolveSystemKeyAt(5, 5, cells)).isEqualTo(buildCellKey("A"));
        }

        @Test
        void eachCellClaimsOnlyItsOwnAreaWhenSeveralArePresent() {
            // Two disjoint cells: the point sits in the second, so the search must not
            // stop at the first polygon it tests.
            var cells = new LinkedHashMap<SystemKey, List<double[]>>();
            cells.put(buildCellKey("A"), buildSquare(0, 0, 10));
            cells.put(buildCellKey("B"), buildSquare(20, 0, 10));

            assertThat(CellHitTest.resolveSystemKeyAt(25, 5, cells)).isEqualTo(buildCellKey("B"));
        }

        @Test
        void twoCellsWhoseSystemsShareAnIdAreHitApart() {
            // The cut holds a cell for each of a colliding pair, so the cursor resolves to the
            // one it is actually over rather than to whichever the id names first.
            var second = new SystemKey("deep space", "", "38d53");
            var cells = new LinkedHashMap<SystemKey, List<double[]>>();
            cells.put(new SystemKey("deep space", "", "8b3"), buildSquare(0, 0, 10));
            cells.put(second, buildSquare(20, 0, 10));

            assertThat(CellHitTest.resolveSystemKeyAt(25, 5, cells)).isEqualTo(second);
        }

        @Test
        void aPointInTheGapBetweenCellsResolvesToNothing() {
            // The border channel the inset opens between two cells belongs to neither,
            // and reads as empty space just as it draws.
            var cells = new LinkedHashMap<SystemKey, List<double[]>>();
            cells.put(buildCellKey("A"), buildSquare(0, 0, 10));
            cells.put(buildCellKey("B"), buildSquare(20, 0, 10));

            assertThat(CellHitTest.resolveSystemKeyAt(15, 5, cells)).isNull();
        }

        @Test
        void aPointBeyondEveryCellResolvesToNothing() {
            var cells = buildKeyedValues(Map.of("A", buildSquare(0, 0, 10)));

            assertThat(CellHitTest.resolveSystemKeyAt(100, 100, cells)).isNull();
        }

        @Test
        void aPointOnASharedEdgeResolvesTheSameWayEveryCall() {
            // Two cells meeting flush along x = 10: whichever wins, the cursor must not
            // flicker between them while it rests there, so the verdict is stable.
            var cells = new LinkedHashMap<SystemKey, List<double[]>>();
            cells.put(buildCellKey("A"), buildSquare(0, 0, 10));
            cells.put(buildCellKey("B"), buildSquare(10, 0, 10));

            var first = CellHitTest.resolveSystemKeyAt(10, 5, cells);

            assertThat(first).isEqualTo(CellHitTest.resolveSystemKeyAt(10, 5, cells));
            assertThat(first).isIn(buildCellKey("A"), buildCellKey("B"));
        }

        @Test
        void anEmptyMapResolvesToNothing() {
            assertThat(CellHitTest.resolveSystemKeyAt(5, 5, Map.of())).isNull();
        }

        @Test
        void aCellWithNoDrawablePolygonCanNeverBeHit() {
            // A cell the inset consumed comes back with no vertices; it encloses no area,
            // so no point lands in it.
            var cells = buildKeyedValues(Map.<String, List<double[]>>of("A", List.of()));

            assertThat(CellHitTest.resolveSystemKeyAt(0, 0, cells)).isNull();
        }
    }
}
