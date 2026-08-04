package kmu.maplayers.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the contract of {@link CellHitTest#resolveSystemIdAt}:
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
    class ResolveSystemIdAt {
        @Test
        void a_point_inside_a_cell_resolves_to_its_system() {
            var cells = Map.of("A", buildSquare(0, 0, 10));

            assertThat(CellHitTest.resolveSystemIdAt(5, 5, cells)).isEqualTo("A");
        }

        @Test
        void each_cell_claims_only_its_own_area_when_several_are_present() {
            // Two disjoint cells: the point sits in the second, so the search must not
            // stop at the first polygon it tests.
            var cells = new LinkedHashMap<String, List<double[]>>();
            cells.put("A", buildSquare(0, 0, 10));
            cells.put("B", buildSquare(20, 0, 10));

            assertThat(CellHitTest.resolveSystemIdAt(25, 5, cells)).isEqualTo("B");
        }

        @Test
        void a_point_in_the_gap_between_cells_resolves_to_nothing() {
            // The border channel the inset opens between two cells belongs to neither,
            // and reads as empty space just as it draws.
            var cells = new LinkedHashMap<String, List<double[]>>();
            cells.put("A", buildSquare(0, 0, 10));
            cells.put("B", buildSquare(20, 0, 10));

            assertThat(CellHitTest.resolveSystemIdAt(15, 5, cells)).isNull();
        }

        @Test
        void a_point_beyond_every_cell_resolves_to_nothing() {
            var cells = Map.of("A", buildSquare(0, 0, 10));

            assertThat(CellHitTest.resolveSystemIdAt(100, 100, cells)).isNull();
        }

        @Test
        void a_point_on_a_shared_edge_resolves_the_same_way_every_call() {
            // Two cells meeting flush along x = 10: whichever wins, the cursor must not
            // flicker between them while it rests there, so the verdict is stable.
            var cells = new LinkedHashMap<String, List<double[]>>();
            cells.put("A", buildSquare(0, 0, 10));
            cells.put("B", buildSquare(10, 0, 10));

            var first = CellHitTest.resolveSystemIdAt(10, 5, cells);

            assertThat(first).isEqualTo(CellHitTest.resolveSystemIdAt(10, 5, cells));
            assertThat(first).isIn("A", "B");
        }

        @Test
        void an_empty_map_resolves_to_nothing() {
            assertThat(CellHitTest.resolveSystemIdAt(5, 5, Map.of())).isNull();
        }

        @Test
        void a_cell_with_no_drawable_polygon_can_never_be_hit() {
            // A cell the inset consumed comes back with no vertices; it encloses no area,
            // so no point lands in it.
            var cells = Map.<String, List<double[]>>of("A", List.of());

            assertThat(CellHitTest.resolveSystemIdAt(0, 0, cells)).isNull();
        }
    }
}
