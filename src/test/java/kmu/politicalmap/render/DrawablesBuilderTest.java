package kmu.politicalmap.render;

import kmu.politicalmap.domain.geometry.CellEdge;
import kmu.politicalmap.domain.politics.DominantOwner;
import kmu.settings.FactionPaletteChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the builder's off-engine, deterministic pieces: the palette-color pick that maps
 * a player's color choice to a palette shade, and the cluster-anchor fit that turns a
 * cluster's system positions, cell edges, and tuning into the label line - clipped
 * inside the national border, trimmed clear of system icons, pulled short of the border
 * at both ends, leaned horizontal, and collapsed to the dot when no room remains. The
 * rest shapes cells and reads settings that only resolve in-engine.
 */
final class DrawablesBuilderTest {

    // Two distinct shades so a pick can be told apart from its counterpart.
    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    // A faction whose bright shade the anchor marker should carry.
    private static final DominantOwner FACTION_F =
            new DominantOwner("F", PRIMARY, SECONDARY);

    @Nested
    class PickPaletteColor {

        @Test
        void pickPaletteColorReturnsThePrimaryShadeForAPrimaryChoice() {
            assertThat(DrawablesBuilder.pickPaletteColor(
                    FactionPaletteChoice.PRIMARY, PRIMARY, SECONDARY)).isEqualTo(PRIMARY);
        }

        @Test
        void pickPaletteColorReturnsTheSecondaryShadeForASecondaryChoice() {
            assertThat(DrawablesBuilder.pickPaletteColor(
                    FactionPaletteChoice.SECONDARY, PRIMARY, SECONDARY)).isEqualTo(SECONDARY);
        }

        @Test
        void pickPaletteColorReturnsNullForNoColor() {
            // NONE is the player's "No color" choice; a null color signals the render
            // layer to skip that element.
            assertThat(DrawablesBuilder.pickPaletteColor(
                    FactionPaletteChoice.NONE, PRIMARY, SECONDARY)).isNull();
        }
    }

    @Nested
    class ComputeClusterAnchors {

        // The cluster geometry the fits run against: 1000-unit square cells, well
        // clear of the fixed 150-unit border inset, welded and mitred with the same
        // kind of values the production border trace uses.
        private static final double CELL_SIDE = 1000.0;
        private static final double WELD_TOLERANCE = 1.0;
        private static final double MITER_LIMIT = 4.0;

        // Two cells side by side: the cluster spans x 0..2000, y 0..1000, sites at
        // the cell centres, so the border rings inset to x 150..1850, y 150..850 and
        // the principal axis runs horizontally through the centroid (1000, 500).
        private static final Map<String, List<CellEdge>> HORIZONTAL_PAIR_EDGES = Map.of(
                "A", squareCellEdges(0, 0, null, "B", null, null),
                "B", squareCellEdges(CELL_SIDE, 0, null, null, null, "A"));
        private static final Map<String, double[]> HORIZONTAL_PAIR_SITES = Map.of(
                "A", new double[] {500, 500}, "B", new double[] {1500, 500});
        private static final Map<String, DominantOwner> HORIZONTAL_PAIR_OWNERS = Map.of(
                "A", FACTION_F, "B", FACTION_F);

        // Two cells stacked: the cluster spans x 0..1000, y 0..2000, so the raw
        // principal axis is vertical - the shape the horizontal bias acts on.
        private static final Map<String, List<CellEdge>> VERTICAL_PAIR_EDGES = Map.of(
                "A", squareCellEdges(0, 0, null, null, "B", null),
                "B", squareCellEdges(0, CELL_SIDE, "A", null, null, null));
        private static final Map<String, double[]> VERTICAL_PAIR_SITES = Map.of(
                "A", new double[] {500, 500}, "B", new double[] {500, 1500});

        @Test
        void computeClusterAnchorsClipsTheAxisInsideTheNationalBorder() {
            // With no icon keep-out and no end margin the line is the full interior
            // span: it reaches the inset border rings (x 150..1850) - past the
            // sites, but never out of the border.
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(1.0, 0.0, 0.0));

            var accepted = anchors.get(0).acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                    .isCloseTo(150f, within(1e-3f));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                    .isCloseTo(1850f, within(1e-3f));
            assertThat(accepted.startY()).isCloseTo(500f, within(1e-3f));
            assertThat(accepted.endY()).isCloseTo(500f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsTrimsTheSpanClearOfSystemIcons() {
            // A 300-unit keep-out around the sites at x=500 and x=1500 blocks
            // x 200..800 and 1200..1800, leaving x 800..1200 as the longest clear
            // stretch - the line sits between the icons, not across them.
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(1.0, 0.0, 300.0));

            var accepted = anchors.get(0).acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                    .isCloseTo(800f, within(1e-3f));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                    .isCloseTo(1200f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsPullsEachEndInwardByTheEndInset() {
            // The 100-unit end inset pulls the full interior span (x 150..1850) in
            // from both ends, leaving the border gap a name needs on each side.
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(1.0, 100.0, 0.0));

            var accepted = anchors.get(0).acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                    .isCloseTo(250f, within(1e-3f));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                    .isCloseTo(1750f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsKeepsAVerticalAxisUnderNoBias() {
            // Bias 1 applies no lean, so the stacked cluster's line stays vertical:
            // x pinned to the centroid, y spanning the inset border (150..1850).
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), VERTICAL_PAIR_EDGES,
                    VERTICAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(1.0, 0.0, 0.0));

            var accepted = anchors.get(0).acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(accepted.startX()).isCloseTo(500f, within(1e-3f));
            assertThat(accepted.endX()).isCloseTo(500f, within(1e-3f));
            assertThat(Math.min(accepted.startY(), accepted.endY()))
                    .isCloseTo(150f, within(1e-3f));
            assertThat(Math.max(accepted.startY(), accepted.endY()))
                    .isCloseTo(1850f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsLeansTheAxisHorizontalUnderAFlatBias() {
            // Bias 0 flattens the vertical component entirely, so even the stacked
            // cluster's line runs horizontal through the centroid (500, 1000),
            // spanning the inset border's width (x 150..850).
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), VERTICAL_PAIR_EDGES,
                    VERTICAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(0.0, 0.0, 0.0));

            var accepted = anchors.get(0).acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(accepted.startY()).isCloseTo(1000f, within(1e-3f));
            assertThat(accepted.endY()).isCloseTo(1000f, within(1e-3f));
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                    .isCloseTo(150f, within(1e-3f));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                    .isCloseTo(850f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsFitsASingleSystemClusterALineAlongItsCellShape() {
            // One site has no spread of its own, so the direction falls back to the
            // cell's vertex cloud: a 2000x1000 cell reads horizontal, and the line
            // spans the inset border (x 150..1850) through the site - a single-system
            // cluster still carries a real line, not just the dot.
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A")),
                    Map.of("A", rectangleCellEdges(0, 0, 2 * CELL_SIDE, CELL_SIDE,
                            null, null, null, null)),
                    Map.of("A", new double[] {1000, 500}),
                    Map.of("A", FACTION_F),
                    tuning(1.0, 0.0, 0.0));

            assertThat(anchors).hasSize(1);
            var anchor = anchors.get(0);
            assertThat(anchor.centroidX()).isEqualTo(1000f);
            assertThat(anchor.centroidY()).isEqualTo(500f);
            var accepted = anchor.acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                    .isCloseTo(150f, within(1e-3f));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                    .isCloseTo(1850f, within(1e-3f));
            assertThat(accepted.startY()).isCloseTo(500f, within(1e-3f));
            assertThat(accepted.endY()).isCloseTo(500f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsCollapsesToTheDotWhenTheClearSpanLeavesNoRoom() {
            // A 1000-unit end inset asks for 2000 units of margin from a 1700-unit
            // interior span: the ends cross, so no line fits and only the dot shows.
            // With the diagnostic toggles off the collapse carries no extra lines.
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(1.0, 1000.0, 0.0));

            var anchor = anchors.get(0);
            assertThat(anchor.acceptedAxis()).isNull();
            assertThat(anchor.rejectedAxis()).isNull();
            assertThat(anchor.unbiasedAxis()).isNull();
        }

        @Test
        void computeClusterAnchorsReportsThePreInsetSpanAsRejectedWhenTheEndInsetCollapses() {
            // Same no-room collapse, but with the rejected-axis toggle on: the best
            // candidate the fit had - the full pre-inset clear span (x 150..1850) -
            // comes back for the red line, showing how close the cluster came.
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(1.0, 1000.0, 0.0, true, false));

            var anchor = anchors.get(0);
            assertThat(anchor.acceptedAxis()).isNull();
            var rejected = anchor.rejectedAxis();
            assertThat(rejected).isNotNull();
            assertThat(Math.min(rejected.startX(), rejected.endX()))
                    .isCloseTo(150f, within(1e-3f));
            assertThat(Math.max(rejected.startX(), rejected.endX()))
                    .isCloseTo(1850f, within(1e-3f));
            assertThat(rejected.startY()).isCloseTo(500f, within(1e-3f));
            assertThat(rejected.endY()).isCloseTo(500f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsReportsTheLongestInteriorSpanAsRejectedWhenIconsBlockAll() {
            // A 900-unit keep-out around both sites blankets the whole interior span,
            // so no clear interval survives; the rejected line falls back to the
            // longest interior span itself (x 150..1850) - the last candidate standing
            // before the icon trim consumed everything.
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(1.0, 0.0, 900.0, true, false));

            var anchor = anchors.get(0);
            assertThat(anchor.acceptedAxis()).isNull();
            var rejected = anchor.rejectedAxis();
            assertThat(rejected).isNotNull();
            assertThat(Math.min(rejected.startX(), rejected.endX()))
                    .isCloseTo(150f, within(1e-3f));
            assertThat(Math.max(rejected.startX(), rejected.endX()))
                    .isCloseTo(1850f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsReportsTheUnbiasedLineWhenTheBiasChangesTheDirection() {
            // A flat bias turns the stacked cluster's line horizontal; with the
            // unbiased toggle on, the vertical line the fit would accept without the
            // bias (x 500, y 150..1850) rides along for the yellow comparison.
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), VERTICAL_PAIR_EDGES,
                    VERTICAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(0.0, 0.0, 0.0, false, true));

            var anchor = anchors.get(0);
            // The accepted line stays the biased (horizontal) one.
            var accepted = anchor.acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(accepted.startY()).isCloseTo(1000f, within(1e-3f));
            assertThat(accepted.endY()).isCloseTo(1000f, within(1e-3f));
            var unbiased = anchor.unbiasedAxis();
            assertThat(unbiased).isNotNull();
            assertThat(unbiased.startX()).isCloseTo(500f, within(1e-3f));
            assertThat(unbiased.endX()).isCloseTo(500f, within(1e-3f));
            assertThat(Math.min(unbiased.startY(), unbiased.endY()))
                    .isCloseTo(150f, within(1e-3f));
            assertThat(Math.max(unbiased.startY(), unbiased.endY()))
                    .isCloseTo(1850f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsOmitsTheUnbiasedLineWhenTheBiasIsANoOp() {
            // With the bias at 1 the accepted line already is the unbiased one, so no
            // separate yellow line is built even with its toggle on - the green line
            // stands in for both.
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), VERTICAL_PAIR_EDGES,
                    VERTICAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(1.0, 0.0, 0.0, false, true));

            assertThat(anchors.get(0).unbiasedAxis()).isNull();
        }

        @Test
        void computeClusterAnchorsCollapsesToTheDotWhenNoBorderRingTraces() {
            // Members with no cell edges yield no border ring to clip against, so
            // there is nothing to prove the line interior - dot only.
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), Map.of(),
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(1.0, 0.0, 0.0));

            var anchor = anchors.get(0);
            assertThat(anchor.centroidX()).isCloseTo(1000f, within(1e-4f));
            assertThat(anchor.acceptedAxis()).isNull();
        }

        @Test
        void computeClusterAnchorsColorsTheMarkerInTheOwningFactionsBrightShade() {
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A")),
                    Map.of("A", squareCellEdges(0, 0, null, null, null, null)),
                    Map.of("A", new double[] {0, 0}),
                    Map.of("A", FACTION_F),
                    tuning(1.0, 0.0, 0.0));

            assertThat(anchors.get(0).color()).isEqualTo(PRIMARY);
        }

        @Test
        void computeClusterAnchorsSkipsAClusterWhoseSitesAreAllMissing() {
            // A cluster whose members have no site (none in the site map) has no point
            // cloud to fit, so it contributes no anchor rather than an empty fit.
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A")), Map.of(),
                    Map.of(),
                    Map.of("A", FACTION_F),
                    tuning(1.0, 0.0, 0.0));

            assertThat(anchors).isEmpty();
        }

        // A tuning with the fixture's border-trace pair baked in and both diagnostic
        // toggles off, so each test names only the three knobs it exercises: bias,
        // end inset, icon clearance.
        private static DrawablesBuilder.AnchorTuning tuning(double horizontalBias,
                double endInsetDistance, double iconClearance) {
            return tuning(horizontalBias, endInsetDistance, iconClearance, false, false);
        }

        // The full tuning, for the tests that also exercise the rejected- and
        // unbiased-axis diagnostics.
        private static DrawablesBuilder.AnchorTuning tuning(double horizontalBias,
                double endInsetDistance, double iconClearance, boolean showRejectedAxis,
                boolean showUnbiasedAxis) {
            return new DrawablesBuilder.AnchorTuning(
                    new DrawablesBuilder.BorderTrace(WELD_TOLERANCE, MITER_LIMIT),
                    horizontalBias, endInsetDistance, iconClearance, showRejectedAxis,
                    showUnbiasedAxis);
        }

        // One square cell's CCW edges (bottom, right, top, left), each tagged with the
        // neighbouring system across it or null for a frontier into empty space.
        private static List<CellEdge> squareCellEdges(double minX, double minY,
                String bottomNeighbour, String rightNeighbour, String topNeighbour,
                String leftNeighbour) {
            return rectangleCellEdges(minX, minY, CELL_SIDE, CELL_SIDE,
                    bottomNeighbour, rightNeighbour, topNeighbour, leftNeighbour);
        }

        // One axis-aligned rectangular cell's CCW edges (bottom, right, top, left) -
        // the general fixture behind squareCellEdges, and the shape whose long side
        // gives a single-system cluster's direction fallback something to fit.
        private static List<CellEdge> rectangleCellEdges(double minX, double minY,
                double width, double height, String bottomNeighbour, String rightNeighbour,
                String topNeighbour, String leftNeighbour) {
            var maxX = minX + width;
            var maxY = minY + height;
            return List.of(
                    new CellEdge(minX, minY, maxX, minY, bottomNeighbour),
                    new CellEdge(maxX, minY, maxX, maxY, rightNeighbour),
                    new CellEdge(maxX, maxY, minX, maxY, topNeighbour),
                    new CellEdge(minX, maxY, minX, minY, leftNeighbour));
        }
    }
}
