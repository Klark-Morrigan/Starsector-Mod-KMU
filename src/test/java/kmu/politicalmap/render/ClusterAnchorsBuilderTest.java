package kmu.politicalmap.render;

import kmu.politicalmap.domain.geometry.CellEdge;
import kmu.politicalmap.domain.politics.DominantOwner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the cluster-anchor search: the deterministic geometry that turns a cluster's
 * system positions, cell edges, and tuning into the accepted label line - the
 * highest-scoring of many candidate lines swept across the cluster, each clipped inside
 * the national border, trimmed clear of system icons, and pulled short of the border at
 * both ends, with shallower lines favoured over steep ones by a length-versus-slope
 * score, and collapsed to the site-centroid dot when no candidate survives. The builder's
 * settings-fed rebuild entry points only resolve in-engine.
 */
final class ClusterAnchorsBuilderTest {

    // Two distinct shades so the anchor marker's bright pick can be told apart from the
    // dark counterpart.
    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    // A faction whose bright shade the anchor marker should carry.
    private static final DominantOwner FACTION_F =
            new DominantOwner("F", PRIMARY, SECONDARY);

    @Nested
    class ComputeClusterAnchors {

        // The cluster geometry the searches run against: cells sized well clear of the
        // fixed 150-unit border inset, welded and mitred with the same kind of values
        // the production border trace uses.
        private static final double CELL_SIDE = 1000.0;
        private static final double WELD_TOLERANCE = 1.0;
        private static final double MITER_LIMIT = 4.0;

        // Two 1000-unit cells side by side: the cluster spans x 0..2000, y 0..1000, so
        // the border rings inset to x 150..1850, y 150..850 and a horizontal line is the
        // longest interior chord by far (the region is wide and short, so every slanted
        // line is height-limited and much shorter).
        private static final Map<String, List<CellEdge>> HORIZONTAL_PAIR_EDGES = Map.of(
                "A", squareCellEdges(0, 0, null, "B", null, null),
                "B", squareCellEdges(CELL_SIDE, 0, null, null, null, "A"));
        private static final Map<String, double[]> HORIZONTAL_PAIR_SITES = Map.of(
                "A", new double[] {500, 500}, "B", new double[] {1500, 500});
        private static final Map<String, DominantOwner> HORIZONTAL_PAIR_OWNERS = Map.of(
                "A", FACTION_F, "B", FACTION_F);

        // Two 500-wide, 1000-tall cells stacked: the cluster is genuinely tall and thin
        // (inset x 150..350, y 150..1850), so a slanted line is width-limited to a short
        // chord and only the vertical line runs the cluster's full length.
        private static final Map<String, List<CellEdge>> THIN_COLUMN_EDGES = Map.of(
                "A", rectangleCellEdges(0, 0, 500, CELL_SIDE, null, null, "B", null),
                "B", rectangleCellEdges(0, CELL_SIDE, 500, CELL_SIDE, "A", null, null, null));
        private static final Map<String, double[]> THIN_COLUMN_SITES = Map.of(
                "A", new double[] {250, 500}, "B", new double[] {250, 1500});
        private static final Map<String, DominantOwner> THIN_COLUMN_OWNERS = Map.of(
                "A", FACTION_F, "B", FACTION_F);

        // A 2x2 block of 1000-unit cells: a square cluster (inset x 150..1850,
        // y 150..1850) where horizontal, vertical, and diagonal chords are all comparably
        // long, so the vertical penalty - not raw length - decides the winner.
        private static final Map<String, List<CellEdge>> SQUARE_GRID_EDGES = Map.of(
                "A", squareCellEdges(0, 0, null, "B", "C", null),
                "B", squareCellEdges(CELL_SIDE, 0, null, null, "D", "A"),
                "C", squareCellEdges(0, CELL_SIDE, "A", "D", null, null),
                "D", squareCellEdges(CELL_SIDE, CELL_SIDE, "B", null, null, "C"));
        private static final Map<String, DominantOwner> SQUARE_GRID_OWNERS = Map.of(
                "A", FACTION_F, "B", FACTION_F, "C", FACTION_F, "D", FACTION_F);
        // Sites strung vertically down the square's centre: the cluster's principal axis
        // reads vertical though the region is square, the setup that makes the penalty
        // flip the accepted line horizontal.
        private static final Map<String, double[]> SQUARE_GRID_VERTICAL_SITES = Map.of(
                "A", new double[] {1000, 200}, "B", new double[] {1000, 700},
                "C", new double[] {1000, 1300}, "D", new double[] {1000, 1800});
        // Sites at the cell centres: a square point cloud with no preferred axis, used
        // where the winner should be decided purely by length.
        private static final Map<String, double[]> SQUARE_GRID_CENTERED_SITES = Map.of(
                "A", new double[] {500, 500}, "B", new double[] {1500, 500},
                "C", new double[] {500, 1500}, "D", new double[] {1500, 1500});

        // A boot-shaped cluster: a bottom arm (x 0..3000) and a left arm (x 0..1000,
        // y 0..3000) meeting at the origin, with the top-right a deep concave notch. The
        // sites' mean lands in that notch - outside the cluster's border - so any line
        // through the centroid finds only the short left-arm chord, while the arms
        // themselves carry a chord nearly three cells long.
        private static final Map<String, List<CellEdge>> BOOT_EDGES = Map.of(
                "A", squareCellEdges(0, 0, null, "B", "D", null),
                "B", squareCellEdges(CELL_SIDE, 0, null, "C", null, "A"),
                "C", squareCellEdges(2 * CELL_SIDE, 0, null, null, null, "B"),
                "D", squareCellEdges(0, CELL_SIDE, "A", null, "E", null),
                "E", squareCellEdges(0, 2 * CELL_SIDE, "D", null, null, null));
        private static final Map<String, double[]> BOOT_SITES = Map.of(
                "A", new double[] {500, 500}, "B", new double[] {1500, 500},
                "C", new double[] {2500, 500}, "D", new double[] {500, 1500},
                "E", new double[] {500, 2500});
        private static final Map<String, DominantOwner> BOOT_OWNERS = Map.of(
                "A", FACTION_F, "B", FACTION_F, "C", FACTION_F, "D", FACTION_F,
                "E", FACTION_F);

        @Test
        void computeClusterAnchorsClipsTheAcceptedLineInsideTheNationalBorder() {
            // No icon keep-out and no end margin, so the winning horizontal line is the
            // full interior span: it reaches the inset border rings (x 150..1850) - past
            // the sites, but never out of the border.
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(0.0, 0.0, 3, 1, 0.0, 2.0));

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
        void computeClusterAnchorsRelocatesToAParallelOffsetToClearAnIconOnTheCentreLine() {
            // A 150-unit keep-out around the sites at y=500 blocks the centre line but
            // clears the parallel offsets at y=325 and y=675 (175 away). Rather than
            // shrink the centred line to a stub between the icons, the search slides the
            // whole line off-centre to the first clear offset and keeps its full length.
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(0.0, 150.0, 3, 3, 0.0, 2.0));

            var anchor = anchors.get(0);
            var accepted = anchor.acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                    .isCloseTo(150f, within(1e-3f));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                    .isCloseTo(1850f, within(1e-3f));
            assertThat(accepted.startY()).isCloseTo(325f, within(1e-3f));
            assertThat(accepted.endY()).isCloseTo(325f, within(1e-3f));
            // The anchor point follows the relocated line, not the sites.
            assertThat(anchor.anchorX()).isCloseTo(1000f, within(1e-3f));
            assertThat(anchor.anchorY()).isCloseTo(325f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsPullsEachEndInwardByTheEndInset() {
            // The 100-unit end inset pulls the winning horizontal span (x 150..1850) in
            // from both ends, leaving the border gap a name needs on each side.
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(100.0, 0.0, 3, 1, 0.0, 2.0));

            var accepted = anchors.get(0).acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                    .isCloseTo(250f, within(1e-3f));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                    .isCloseTo(1750f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsKeepsAGenuinelyTallThinClusterVertical() {
            // The column is only 200 units wide once inset, so every slanted candidate is
            // width-limited to a short chord and only the vertical line runs the full
            // 1700-unit height. Even with the penalty docking the vertical line by half,
            // its length wins - a genuinely tall cluster stays vertical.
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), THIN_COLUMN_EDGES,
                    THIN_COLUMN_SITES, THIN_COLUMN_OWNERS,
                    tuning(0.0, 0.0, 3, 3, 0.5, 2.0));

            var accepted = anchors.get(0).acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(accepted.startX()).isCloseTo(accepted.endX(), within(1e-3f));
            assertThat(accepted.startX()).isBetween(150f, 350f);
            assertThat(Math.min(accepted.startY(), accepted.endY()))
                    .isCloseTo(150f, within(1e-3f));
            assertThat(Math.max(accepted.startY(), accepted.endY()))
                    .isCloseTo(1850f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsFlipsAWideVerticalCloudHorizontalUnderThePenalty() {
            // The region is square, so its horizontal and vertical chords are the same
            // 1700 units even though the site cloud is strung vertically. The penalty
            // docks the vertical line by half and leaves the horizontal one whole, so the
            // accepted line runs horizontal against the cloud's own axis.
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B", "C", "D")), SQUARE_GRID_EDGES,
                    SQUARE_GRID_VERTICAL_SITES, SQUARE_GRID_OWNERS,
                    tuning(0.0, 0.0, 3, 3, 0.5, 2.0));

            var accepted = anchors.get(0).acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(accepted.startY()).isCloseTo(accepted.endY(), within(1e-3f));
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                    .isCloseTo(150f, within(1e-3f));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                    .isCloseTo(1850f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsPicksTheLongestCandidateWhenThePenaltyIsZero() {
            // With the penalty off the search reduces to the pure longest line. In the
            // square cluster that is a diagonal through the centre (1700 / sin 60 ~ 1963),
            // longer than either axis-aligned 1700 chord, so a slanted line wins.
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B", "C", "D")), SQUARE_GRID_EDGES,
                    SQUARE_GRID_CENTERED_SITES, SQUARE_GRID_OWNERS,
                    tuning(0.0, 0.0, 3, 3, 0.0, 2.0));

            var accepted = anchors.get(0).acceptedAxis();
            assertThat(accepted).isNotNull();
            var length = Math.hypot(accepted.endX() - accepted.startX(),
                    accepted.endY() - accepted.startY());
            assertThat(length).isGreaterThan(1900.0);
            // Genuinely diagonal: neither axis-aligned.
            assertThat(Math.abs(accepted.endX() - accepted.startX())).isGreaterThan(1f);
            assertThat(Math.abs(accepted.endY() - accepted.startY())).isGreaterThan(1f);
        }

        @Test
        void computeClusterAnchorsPlacesTheAnchorDotAtTheAcceptedLineMidpoint() {
            // The centred horizontal winner spans x 150..1850 at y 500, so its midpoint -
            // the dot and the coming label's hang-point - is (1000, 500).
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(0.0, 0.0, 3, 1, 0.0, 2.0));

            var anchor = anchors.get(0);
            var accepted = anchor.acceptedAxis();
            assertThat(anchor.anchorX())
                    .isCloseTo((accepted.startX() + accepted.endX()) / 2f, within(1e-3f));
            assertThat(anchor.anchorY())
                    .isCloseTo((accepted.startY() + accepted.endY()) / 2f, within(1e-3f));
            assertThat(anchor.anchorX()).isCloseTo(1000f, within(1e-3f));
            assertThat(anchor.anchorY()).isCloseTo(500f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsFindsALineDeepInAConcaveClusterWhoseCentroidIsOutside() {
            // The boot's site centroid falls in the concave notch, outside the border, so
            // a line through it would clip only the 700-unit left arm. The offset sweep
            // instead reaches the bottom arm and returns a chord nearly three cells long -
            // the payoff of freeing the line from the centroid.
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B", "C", "D", "E")), BOOT_EDGES,
                    BOOT_SITES, BOOT_OWNERS,
                    tuning(0.0, 0.0, 3, 5, 0.5, 2.0));

            var accepted = anchors.get(0).acceptedAxis();
            assertThat(accepted).isNotNull();
            var length = Math.hypot(accepted.endX() - accepted.startX(),
                    accepted.endY() - accepted.startY());
            assertThat(length).isGreaterThan(2200.0);
        }

        @Test
        void computeClusterAnchorsFitsASingleSystemClusterALineAlongItsCellShape() {
            // One site has no spread of its own, so its cell's own 2000x1000 shape feeds a
            // horizontal principal-axis candidate; the search lands the full interior span
            // (x 150..1850) - a single-system cluster still carries a real line.
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A")),
                    Map.of("A", rectangleCellEdges(0, 0, 2 * CELL_SIDE, CELL_SIDE,
                            null, null, null, null)),
                    Map.of("A", new double[] {1000, 500}),
                    Map.of("A", FACTION_F),
                    tuning(0.0, 0.0, 3, 1, 0.0, 2.0));

            assertThat(anchors).hasSize(1);
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
        void computeClusterAnchorsCollapsesToTheSiteCentroidDotWhenNoLineFits() {
            // A 1000-unit end inset asks for 2000 units of margin from a 1700-unit longest
            // chord: no candidate survives, so the anchor is just the dot at the site
            // centroid (1000, 500). With the diagnostic toggles off it carries no lines.
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(1000.0, 0.0, 3, 3, 0.0, 2.0));

            var anchor = anchors.get(0);
            assertThat(anchor.acceptedAxis()).isNull();
            assertThat(anchor.rejectedAxis()).isNull();
            assertThat(anchor.unbiasedAxis()).isNull();
            assertThat(anchor.anchorX()).isCloseTo(1000f, within(1e-3f));
            assertThat(anchor.anchorY()).isCloseTo(500f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsReportsTheBestRejectedCandidateWhenTheSearchCollapses() {
            // Same no-room collapse, but with the rejected-axis toggle on: the best
            // candidate the search had - the full pre-inset clear span (x 150..1850) -
            // comes back for the red line, showing how close the cluster came.
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(1000.0, 0.0, 3, 1, 0.0, 2.0, true, false));

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
        void computeClusterAnchorsReportsTheUnbiasedLineWhenThePenaltyMovesThePick() {
            // In the square cluster the penalty accepts the horizontal 1700 line while the
            // pure longest line is the ~1963 diagonal. With the unbiased toggle on, that
            // diagonal rides along for the yellow comparison.
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B", "C", "D")), SQUARE_GRID_EDGES,
                    SQUARE_GRID_VERTICAL_SITES, SQUARE_GRID_OWNERS,
                    tuning(0.0, 0.0, 3, 3, 0.5, 2.0, false, true));

            var anchor = anchors.get(0);
            var accepted = anchor.acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(accepted.startY()).isCloseTo(accepted.endY(), within(1e-3f));
            var unbiased = anchor.unbiasedAxis();
            assertThat(unbiased).isNotNull();
            var unbiasedLength = Math.hypot(unbiased.endX() - unbiased.startX(),
                    unbiased.endY() - unbiased.startY());
            assertThat(unbiasedLength).isGreaterThan(1900.0);
        }

        @Test
        void computeClusterAnchorsOmitsTheUnbiasedLineWhenThePenaltyDoesNotMoveThePick() {
            // In the tall thin column the longest line is also the accepted one (vertical
            // wins on length even penalised), so the pure-longest line coincides with the
            // accepted line and no separate yellow line is built.
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), THIN_COLUMN_EDGES,
                    THIN_COLUMN_SITES, THIN_COLUMN_OWNERS,
                    tuning(0.0, 0.0, 3, 3, 0.5, 2.0, false, true));

            assertThat(anchors.get(0).unbiasedAxis()).isNull();
        }

        @Test
        void computeClusterAnchorsCollapsesToTheDotWhenNoBorderRingTraces() {
            // Members with no cell edges yield no border ring to clip against, so there is
            // nothing to prove a line interior - the dot at the site centroid only.
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")), Map.of(),
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_OWNERS,
                    tuning(0.0, 0.0, 3, 3, 0.0, 2.0));

            var anchor = anchors.get(0);
            assertThat(anchor.anchorX()).isCloseTo(1000f, within(1e-4f));
            assertThat(anchor.acceptedAxis()).isNull();
        }

        @Test
        void computeClusterAnchorsColorsTheMarkerInTheOwningFactionsBrightShade() {
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A")),
                    Map.of("A", squareCellEdges(0, 0, null, null, null, null)),
                    Map.of("A", new double[] {500, 500}),
                    Map.of("A", FACTION_F),
                    tuning(0.0, 0.0, 3, 1, 0.0, 2.0));

            assertThat(anchors.get(0).color()).isEqualTo(PRIMARY);
        }

        @Test
        void computeClusterAnchorsSkipsAClusterWhoseSitesAreAllMissing() {
            // A cluster whose members have no site (none in the site map) has no point
            // cloud to fit, so it contributes no anchor rather than an empty fit.
            var anchors = ClusterAnchorsBuilder.computeClusterAnchors(
                    List.of(List.of("A")), Map.of(),
                    Map.of(),
                    Map.of("A", FACTION_F),
                    tuning(0.0, 0.0, 3, 1, 0.0, 2.0));

            assertThat(anchors).isEmpty();
        }

        // A tuning with the fixture's border-trace pair baked in and both diagnostic
        // toggles off, so each test names only the search knobs it exercises.
        private static ClusterAnchorsBuilder.AnchorTuning tuning(double endInsetDistance,
                double iconClearance, int directionCount, int offsetCount,
                double verticalPenaltyStrength, double verticalPenaltyExponent) {
            return tuning(endInsetDistance, iconClearance, directionCount, offsetCount,
                    verticalPenaltyStrength, verticalPenaltyExponent, false, false);
        }

        // The full tuning, for the tests that also exercise the rejected- and
        // unbiased-axis diagnostics.
        private static ClusterAnchorsBuilder.AnchorTuning tuning(double endInsetDistance,
                double iconClearance, int directionCount, int offsetCount,
                double verticalPenaltyStrength, double verticalPenaltyExponent,
                boolean showRejectedAxis, boolean showUnbiasedAxis) {
            return new ClusterAnchorsBuilder.AnchorTuning(
                    new BorderTrace(WELD_TOLERANCE, MITER_LIMIT), endInsetDistance,
                    iconClearance, directionCount, offsetCount, verticalPenaltyStrength,
                    verticalPenaltyExponent, showRejectedAxis, showUnbiasedAxis);
        }

        // One square cell's CCW edges (bottom, right, top, left), each tagged with the
        // neighbouring system across it or null for a frontier into empty space.
        private static List<CellEdge> squareCellEdges(double minX, double minY,
                String bottomNeighbour, String rightNeighbour, String topNeighbour,
                String leftNeighbour) {
            return rectangleCellEdges(minX, minY, CELL_SIDE, CELL_SIDE,
                    bottomNeighbour, rightNeighbour, topNeighbour, leftNeighbour);
        }

        // One axis-aligned rectangular cell's CCW edges (bottom, right, top, left) - the
        // general fixture behind squareCellEdges, and the shape whose long side gives a
        // single-system cluster's direction fallback something to fit.
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
