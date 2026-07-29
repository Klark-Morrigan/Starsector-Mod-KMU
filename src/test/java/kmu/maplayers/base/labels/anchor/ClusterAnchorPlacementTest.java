package kmu.maplayers.base.labels.anchor;

import kmlib.starsector.ui.label.AspectLabelLengthEstimator;
import kmlib.starsector.ui.label.LabelLengthEstimator;
import kmlib.starsector.ui.label.NameFitSpecification;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.labels.anchor.specifications.AnchorDiagnostics;
import kmu.maplayers.base.labels.anchor.specifications.AnchorSearch;
import kmu.maplayers.base.labels.anchor.specifications.LabelAnchorSpecification;
import kmu.maplayers.base.labels.anchor.specifications.LeanScoring;
import kmu.maplayers.base.render.regions.PoliticalBorderTrace;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the cluster-anchor search: the deterministic geometry that turns a cluster's
 * system positions, cell edges, tuning, and name estimator into the accepted label box -
 * the highest-scoring of many candidate lines swept across the cluster, each clipped inside
 * the cluster's border, trimmed clear of system icons, and pulled short of the border at
 * both ends, with shallower lines favoured over steep ones by a font-height-versus-slope
 * score, and collapsed to the site-centroid dot when no candidate survives. The line-fit
 * tests run a slender single-line band that reduces the box to its centreline, so they
 * pin the underlying line geometry; the band-fit tests give the name real girth to pin
 * that a fat band stays inside the border, that a square cluster wraps the name into two
 * lines to spend spare girth, that the line cap forbids stacking, and that a band too
 * thick to fit collapses to the dot.
 *
 * <p>The colour and name a cluster draws in arrive injected as plain functions of its
 * grouping key, so what is pinned here is that the search asks with the right key and
 * carries the answers onto the anchor - what those answers should be belongs to whoever
 * resolves them.
 */
final class ClusterAnchorPlacementTest {

    // The shade the injected resolver hands every cluster, so a test can tell the carried
    // colour apart from any default.
    private static final Color LABEL_COLOR = Color.RED;

    // The colour seam the geometry tests run under: one shade for every grouping key, since
    // none of them assert on colour.
    private static final Function<String, Color> FIXED_LABEL_COLORS = groupKey -> LABEL_COLOR;

    // The grouping the anchor search traces under: each cell drawing as its own star (identity
    // draws-as over the group-key map's cells, all grouped cluster members here), keyed by the
    // given grouping keys.
    private static CellGrouping grouping(Map<String, String> groupKeyBySystemId) {
        var systemIdByCellId = new LinkedHashMap<String, String>();
        for (var cellId : groupKeyBySystemId.keySet()) {
            systemIdByCellId.put(cellId, cellId);
        }
        return new CellGrouping(systemIdByCellId, groupKeyBySystemId);
    }

    @Nested
    class ComputeClusterAnchors {

        // The cluster geometry the searches run against: cells sized well clear of the
        // fixed 150-unit border inset, welded and mitred with the same kind of values
        // the production border trace uses.
        private static final double CELL_SIDE = 1000.0;
        private static final double WELD_TOLERANCE = 1.0;
        private static final double MITER_LIMIT = 4.0;

        // The one grouping key every fixture cluster falls under, so a cluster's key is
        // known where a test asserts the search looked one up.
        private static final String GROUP_KEY = "F";

        // Two 1000-unit cells side by side: the cluster spans x 0..2000, y 0..1000, so
        // the border rings inset to x 150..1850, y 150..850 and a horizontal line is the
        // longest interior chord by far (the region is wide and short, so every slanted
        // line is height-limited and much shorter).
        private static final Map<String, List<CellEdge>> HORIZONTAL_PAIR_EDGES = Map.of(
                "A", squareCellEdges(0, 0, null, "B", null, null),
                "B", squareCellEdges(CELL_SIDE, 0, null, null, null, "A"));
        private static final Map<String, double[]> HORIZONTAL_PAIR_SITES = Map.of(
                "A", new double[] {500, 500}, "B", new double[] {1500, 500});
        private static final CellGrouping HORIZONTAL_PAIR_GROUPING = grouping(Map.of(
                "A", GROUP_KEY, "B", GROUP_KEY));

        // Two 500-wide, 1000-tall cells stacked: the cluster is genuinely tall and thin
        // (inset x 150..350, y 150..1850), so a slanted line is width-limited to a short
        // chord and only the vertical line runs the cluster's full length.
        private static final Map<String, List<CellEdge>> THIN_COLUMN_EDGES = Map.of(
                "A", rectangleCellEdges(0, 0, 500, CELL_SIDE, null, null, "B", null),
                "B", rectangleCellEdges(0, CELL_SIDE, 500, CELL_SIDE, "A", null, null, null));
        private static final Map<String, double[]> THIN_COLUMN_SITES = Map.of(
                "A", new double[] {250, 500}, "B", new double[] {250, 1500});
        private static final CellGrouping THIN_COLUMN_GROUPING = grouping(Map.of(
                "A", GROUP_KEY, "B", GROUP_KEY));

        // A 2x2 block of 1000-unit cells: a square cluster (inset x 150..1850,
        // y 150..1850) where horizontal, vertical, and diagonal chords are all comparably
        // long, so the vertical penalty - not raw length - decides the winner.
        private static final Map<String, List<CellEdge>> SQUARE_GRID_EDGES = Map.of(
                "A", squareCellEdges(0, 0, null, "B", "C", null),
                "B", squareCellEdges(CELL_SIDE, 0, null, null, "D", "A"),
                "C", squareCellEdges(0, CELL_SIDE, "A", "D", null, null),
                "D", squareCellEdges(CELL_SIDE, CELL_SIDE, "B", null, null, "C"));
        private static final CellGrouping SQUARE_GRID_GROUPING = grouping(Map.of(
                "A", GROUP_KEY, "B", GROUP_KEY, "C", GROUP_KEY, "D", GROUP_KEY));
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
        private static final CellGrouping BOOT_GROUPING = grouping(Map.of(
                "A", GROUP_KEY, "B", GROUP_KEY, "C", GROUP_KEY, "D", GROUP_KEY,
                "E", GROUP_KEY));

        // The lone-member grouping the single-system fits run under.
        private static final CellGrouping SINGLE_SYSTEM_GROUPING =
                grouping(Map.of("A", GROUP_KEY));

        @Test
        void computeClusterAnchorsClipsTheAcceptedLineInsideTheNationalBorder() {
            // No icon keep-out and no end margin, so the winning horizontal line is the
            // full interior span: it reaches the inset border rings (x 150..1850) - past
            // the sites, but never out of the border.
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_GROUPING,
                    spec(0.0, 0.0, 3, 1, 0.0, 2.0),
                    FIXED_LABEL_COLORS,
                    slenderNameEstimators());

            var accepted = anchors.get(0).acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                    .isCloseTo(150.0, within(1e-3));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                    .isCloseTo(1850.0, within(1e-3));
            assertThat(accepted.startY()).isCloseTo(500.0, within(1e-3));
            assertThat(accepted.endY()).isCloseTo(500.0, within(1e-3));
        }

        @Test
        void computeClusterAnchorsRelocatesToAParallelOffsetToClearAnIconOnTheCentreLine() {
            // A 150-unit keep-out around the sites at y=500 blocks the centre line but
            // clears the parallel offsets at y=325 and y=675 (175 away). Rather than
            // shrink the centred line to a stub between the icons, the search slides the
            // whole line off-centre to the first clear offset and keeps its full length.
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_GROUPING,
                    spec(0.0, 150.0, 3, 3, 0.0, 2.0),
                    FIXED_LABEL_COLORS,
                    slenderNameEstimators());

            var anchor = anchors.get(0);
            var accepted = anchor.acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                    .isCloseTo(150.0, within(1e-3));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                    .isCloseTo(1850.0, within(1e-3));
            assertThat(accepted.startY()).isCloseTo(325.0, within(1e-3));
            assertThat(accepted.endY()).isCloseTo(325.0, within(1e-3));
            // The anchor point follows the relocated line, not the sites.
            assertThat(anchor.anchorX()).isCloseTo(1000f, within(1e-3f));
            assertThat(anchor.anchorY()).isCloseTo(325f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsPullsEachEndInwardByTheEndInset() {
            // The 100-unit end inset pulls the winning horizontal span (x 150..1850) in
            // from both ends, leaving the border gap a name needs on each side.
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_GROUPING,
                    spec(100.0, 0.0, 3, 1, 0.0, 2.0),
                    FIXED_LABEL_COLORS,
                    slenderNameEstimators());

            var accepted = anchors.get(0).acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                    .isCloseTo(250.0, within(1e-3));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                    .isCloseTo(1750.0, within(1e-3));
        }

        @Test
        void computeClusterAnchorsKeepsAGenuinelyTallThinClusterVertical() {
            // The column is only 200 units wide once inset, so every slanted candidate is
            // width-limited to a short chord and only the vertical line runs the full
            // 1700-unit height. Even with the penalty docking the vertical line by half,
            // its length wins - a genuinely tall cluster stays vertical.
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B")), THIN_COLUMN_EDGES,
                    THIN_COLUMN_SITES, THIN_COLUMN_GROUPING,
                    spec(0.0, 0.0, 3, 3, 0.5, 2.0),
                    FIXED_LABEL_COLORS,
                    slenderNameEstimators());

            var accepted = anchors.get(0).acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(accepted.startX()).isCloseTo(accepted.endX(), within(1e-3));
            assertThat(accepted.startX()).isBetween(150.0, 350.0);
            assertThat(Math.min(accepted.startY(), accepted.endY()))
                    .isCloseTo(150.0, within(1e-3));
            assertThat(Math.max(accepted.startY(), accepted.endY()))
                    .isCloseTo(1850.0, within(1e-3));
        }

        @Test
        void computeClusterAnchorsFlipsAWideVerticalCloudHorizontalUnderThePenalty() {
            // The region is square, so its horizontal and vertical chords are the same
            // 1700 units even though the site cloud is strung vertically. The penalty
            // docks the vertical line by half and leaves the horizontal one whole, so the
            // accepted line runs horizontal against the cloud's own axis.
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B", "C", "D")), SQUARE_GRID_EDGES,
                    SQUARE_GRID_VERTICAL_SITES, SQUARE_GRID_GROUPING,
                    spec(0.0, 0.0, 3, 3, 0.5, 2.0),
                    FIXED_LABEL_COLORS,
                    slenderNameEstimators());

            var accepted = anchors.get(0).acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(accepted.startY()).isCloseTo(accepted.endY(), within(1e-3));
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                    .isCloseTo(150.0, within(1e-3));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                    .isCloseTo(1850.0, within(1e-3));
        }

        @Test
        void computeClusterAnchorsPicksTheLongestCandidateWhenThePenaltyIsZero() {
            // With the penalty off the search reduces to the pure longest line. In the
            // square cluster that is a diagonal through the centre (1700 / sin 60 ~ 1963),
            // longer than either axis-aligned 1700 chord, so a slanted line wins.
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B", "C", "D")), SQUARE_GRID_EDGES,
                    SQUARE_GRID_CENTERED_SITES, SQUARE_GRID_GROUPING,
                    spec(0.0, 0.0, 3, 3, 0.0, 2.0),
                    FIXED_LABEL_COLORS,
                    slenderNameEstimators());

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
            // the dot and the label's hang-point - is (1000, 500).
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_GROUPING,
                    spec(0.0, 0.0, 3, 1, 0.0, 2.0),
                    FIXED_LABEL_COLORS,
                    slenderNameEstimators());

            var anchor = anchors.get(0);
            var accepted = anchor.acceptedAxis();
            assertThat((double) anchor.anchorX())
                    .isCloseTo((accepted.startX() + accepted.endX()) / 2, within(1e-3));
            assertThat((double) anchor.anchorY())
                    .isCloseTo((accepted.startY() + accepted.endY()) / 2, within(1e-3));
            assertThat(anchor.anchorX()).isCloseTo(1000f, within(1e-3f));
            assertThat(anchor.anchorY()).isCloseTo(500f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsFindsALineDeepInAConcaveClusterWhoseCentroidIsOutside() {
            // The boot's site centroid falls in the concave notch, outside the border, so
            // a line through it would clip only the 700-unit left arm. The offset sweep
            // instead reaches the bottom arm and returns a chord nearly three cells long -
            // the payoff of freeing the line from the centroid.
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B", "C", "D", "E")), BOOT_EDGES,
                    BOOT_SITES, BOOT_GROUPING,
                    spec(0.0, 0.0, 3, 5, 0.5, 2.0),
                    FIXED_LABEL_COLORS,
                    slenderNameEstimators());

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
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A")),
                    Map.of("A", rectangleCellEdges(0, 0, 2 * CELL_SIDE, CELL_SIDE,
                            null, null, null, null)),
                    Map.of("A", new double[] {1000, 500}),
                    SINGLE_SYSTEM_GROUPING,
                    spec(0.0, 0.0, 3, 1, 0.0, 2.0),
                    FIXED_LABEL_COLORS,
                    slenderNameEstimators());

            assertThat(anchors).hasSize(1);
            var accepted = anchors.get(0).acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                    .isCloseTo(150.0, within(1e-3));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                    .isCloseTo(1850.0, within(1e-3));
            assertThat(accepted.startY()).isCloseTo(500.0, within(1e-3));
            assertThat(accepted.endY()).isCloseTo(500.0, within(1e-3));
        }

        @Test
        void computeClusterAnchorsCollapsesToTheSiteCentroidDotWhenNoLineFits() {
            // A 1000-unit end inset asks for 2000 units of margin from a 1700-unit longest
            // chord: no candidate survives, so the anchor is just the dot at the site
            // centroid (1000, 500) - no lines, no name, no font. With the diagnostic
            // toggles off it carries no lines.
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_GROUPING,
                    spec(1000.0, 0.0, 3, 3, 0.0, 2.0),
                    FIXED_LABEL_COLORS,
                    slenderNameEstimators());

            var anchor = anchors.get(0);
            assertThat(anchor.acceptedAxis()).isNull();
            assertThat(anchor.rejectedAxis()).isNull();
            assertThat(anchor.unbiasedAxis()).isNull();
            assertThat(anchor.nameLines()).isEmpty();
            assertThat(anchor.fontHeight()).isEqualTo(0f);
            assertThat(anchor.anchorX()).isCloseTo(1000f, within(1e-3f));
            assertThat(anchor.anchorY()).isCloseTo(500f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsReportsTheBestRejectedCandidateWhenTheSearchCollapses() {
            // Same no-room collapse, but with the rejected-axis toggle on: the best
            // candidate the search had - the full pre-inset clear span (x 150..1850) -
            // comes back for the red line, showing how close the cluster came.
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_GROUPING,
                    spec(1000.0, 0.0, 3, 1, 0.0, 2.0, true, false),
                    FIXED_LABEL_COLORS,
                    slenderNameEstimators());

            var anchor = anchors.get(0);
            assertThat(anchor.acceptedAxis()).isNull();
            var rejected = anchor.rejectedAxis();
            assertThat(rejected).isNotNull();
            assertThat(Math.min(rejected.startX(), rejected.endX()))
                    .isCloseTo(150.0, within(1e-3));
            assertThat(Math.max(rejected.startX(), rejected.endX()))
                    .isCloseTo(1850.0, within(1e-3));
            assertThat(rejected.startY()).isCloseTo(500.0, within(1e-3));
            assertThat(rejected.endY()).isCloseTo(500.0, within(1e-3));
        }

        @Test
        void computeClusterAnchorsReportsTheUnbiasedLineWhenThePenaltyMovesThePick() {
            // In the square cluster the penalty accepts the horizontal 1700 line while the
            // pure longest line is the ~1963 diagonal. With the unbiased toggle on, that
            // diagonal rides along for the yellow comparison.
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B", "C", "D")), SQUARE_GRID_EDGES,
                    SQUARE_GRID_VERTICAL_SITES, SQUARE_GRID_GROUPING,
                    spec(0.0, 0.0, 3, 3, 0.5, 2.0, false, true),
                    FIXED_LABEL_COLORS,
                    slenderNameEstimators());

            var anchor = anchors.get(0);
            var accepted = anchor.acceptedAxis();
            assertThat(accepted).isNotNull();
            assertThat(accepted.startY()).isCloseTo(accepted.endY(), within(1e-3));
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
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B")), THIN_COLUMN_EDGES,
                    THIN_COLUMN_SITES, THIN_COLUMN_GROUPING,
                    spec(0.0, 0.0, 3, 3, 0.5, 2.0, false, true),
                    FIXED_LABEL_COLORS,
                    slenderNameEstimators());

            assertThat(anchors.get(0).unbiasedAxis()).isNull();
        }

        @Test
        void computeClusterAnchorsCollapsesToTheDotWhenNoBorderRingTraces() {
            // Members with no cell edges yield no border ring to clip against, so there is
            // nothing to prove a line interior - the dot at the site centroid only.
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B")), Map.of(),
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_GROUPING,
                    spec(0.0, 0.0, 3, 3, 0.0, 2.0),
                    FIXED_LABEL_COLORS,
                    slenderNameEstimators());

            var anchor = anchors.get(0);
            assertThat(anchor.anchorX()).isCloseTo(1000f, within(1e-4f));
            assertThat(anchor.acceptedAxis()).isNull();
            assertThat(anchor.nameLines()).isEmpty();
        }

        @Test
        void computeClusterAnchorsResolvesTheNameEstimatorByTheClustersGroupingKey() {
            // The injected estimator is what the fit sizes against and what wraps the
            // label's lines, so the resolver must be asked with the key the cluster's
            // members carry - the search's only handle on which name this box is for.
            var askedGroupKeys = new ArrayList<String>();
            Function<String, LabelLengthEstimator> recordingResolver = groupKey -> {
                askedGroupKeys.add(groupKey);
                return new AspectLabelLengthEstimator(SLENDER_ASPECT);
            };
            ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_GROUPING,
                    spec(0.0, 0.0, 3, 1, 0.0, 2.0), FIXED_LABEL_COLORS, recordingResolver);

            assertThat(askedGroupKeys).containsExactly(GROUP_KEY);
        }

        @Test
        void computeClusterAnchorsCarriesTheInjectedColorForTheClustersGroupingKey() {
            // The shade a name and its dot draw in is resolved outside the search and looked
            // up by the same key the name is: the search only carries it onto the anchor, so
            // it never has to know what makes one cluster's colour differ from another's.
            var askedGroupKeys = new ArrayList<String>();
            Function<String, Color> recordingColors = groupKey -> {
                askedGroupKeys.add(groupKey);
                return Color.MAGENTA;
            };
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_GROUPING,
                    spec(0.0, 0.0, 3, 1, 0.0, 2.0), recordingColors,
                    slenderNameEstimators());

            assertThat(askedGroupKeys).containsExactly(GROUP_KEY);
            assertThat(anchors.get(0).color()).isEqualTo(Color.MAGENTA);
        }

        @Test
        void computeClusterAnchorsSkipsAClusterWhoseSitesAreAllMissing() {
            // A cluster whose members have no site (none in the site map) has no point
            // cloud to fit, so it contributes no anchor rather than an empty fit.
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A")), Map.of(),
                    Map.of(),
                    SINGLE_SYSTEM_GROUPING,
                    spec(0.0, 0.0, 3, 1, 0.0, 2.0),
                    FIXED_LABEL_COLORS,
                    slenderNameEstimators());

            assertThat(anchors).isEmpty();
        }

        @Test
        void computeClusterAnchorsCapsTheBandGirthSoTheWholeBandStaysInsideTheBorder() {
            // A slab region 700 tall once inset (y 150..850). A fat name (aspect 1) wants
            // all the girth it can get, but the band cannot exceed the 700 the border
            // allows, so the fit caps the girth at the region rather than overrun it - and
            // the whole band, centreline give or take half its girth, stays within
            // y 150..850. The thin centreline of the line fit hid this; the band makes the
            // "too close to the border" case explicit and keeps it inside.
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B")), HORIZONTAL_PAIR_EDGES,
                    HORIZONTAL_PAIR_SITES, HORIZONTAL_PAIR_GROUPING,
                    bandSpec(0.0, 0.0, 3, 3, 0.0, 2.0, false, false,
                            100.0, 2000.0, 1, 1.0),
                    FIXED_LABEL_COLORS,
                    aspectNameEstimators(1.0));

            var anchor = anchors.get(0);
            assertThat(anchor.acceptedAxis()).isNotNull();
            assertThat(anchor.acceptedAxis().startY()).isCloseTo(500.0, within(1.0));
            // Girth capped just under the 700-tall region, never crossing the border.
            assertThat(anchor.thickness()).isGreaterThan(600f);
            assertThat(anchor.thickness()).isLessThanOrEqualTo(700f);
            assertThat(anchor.anchorY() - anchor.thickness() / 2f).isGreaterThanOrEqualTo(149f);
            assertThat(anchor.anchorY() + anchor.thickness() / 2f).isLessThanOrEqualTo(851f);
        }

        @Test
        void computeClusterAnchorsStacksASquareClusterNameIntoTwoLines() {
            // In a square cluster (inset 1700 on a side) a name six times as long as it is
            // tall cannot run big on one line - the side caps a single line's font. Stacking
            // it into two lines halves the length each line needs and spends the square's
            // spare girth, so the two-line box carries a strictly taller font and the fit
            // chooses it over one line and over three (which the region's girth cannot make
            // taller).
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B", "C", "D")), SQUARE_GRID_EDGES,
                    SQUARE_GRID_CENTERED_SITES, SQUARE_GRID_GROUPING,
                    bandSpec(0.0, 0.0, 3, 3, 0.0, 2.0, false, false,
                            100.0, 1700.0, 3, 1.15),
                    FIXED_LABEL_COLORS,
                    aspectNameEstimators(6.0));

            assertThat(anchors.get(0).lineCount()).isEqualTo(2);
            assertThat(anchors.get(0).thickness()).isGreaterThan(0f);
        }

        @Test
        void computeClusterAnchorsCarriesTheWinningWrapAndItsFontHeight() {
            // The same two-line winner fitted against a fake with real lines: the anchor
            // carries the estimator's wrap at the winning line count and the per-line
            // font height behind the band - girth = fontHeight * (1 + spacing) for two
            // lines -
            // so the label draws exactly the block the fit sized.
            var nameEstimatorFake = new LabelLengthEstimatorFake(6.0);
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B", "C", "D")), SQUARE_GRID_EDGES,
                    SQUARE_GRID_CENTERED_SITES, SQUARE_GRID_GROUPING,
                    bandSpec(0.0, 0.0, 3, 3, 0.0, 2.0, false, false,
                            100.0, 1700.0, 3, 1.15),
                    FIXED_LABEL_COLORS,
                    factionId -> nameEstimatorFake);

            var anchor = anchors.get(0);
            assertThat(anchor.nameLines()).containsExactly("Line 1", "Line 2");
            assertThat(anchor.fontHeight())
                    .isCloseTo(anchor.thickness() / 2.15f, within(1e-2f));
        }

        @Test
        void computeClusterAnchorsKeepsANameOnOneLineWhenTheLineCapIsOne() {
            // The same square that would prefer two lines is held to one when the line cap
            // is one, so the name stays a single line at the smaller font the cap forces -
            // the knob that lets a caller forbid stacking.
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B", "C", "D")), SQUARE_GRID_EDGES,
                    SQUARE_GRID_CENTERED_SITES, SQUARE_GRID_GROUPING,
                    bandSpec(0.0, 0.0, 3, 3, 0.0, 2.0, false, false,
                            100.0, 1700.0, 1, 1.15),
                    FIXED_LABEL_COLORS,
                    aspectNameEstimators(6.0));

            assertThat(anchors.get(0).lineCount()).isEqualTo(1);
        }

        @Test
        void computeClusterAnchorsCollapsesToTheDotWhenTheMinimumBandCannotFit() {
            // A minimum font taller than the 1700 the square holds cannot sit anywhere
            // - no placement can prove even the thinnest required band interior - so the fit
            // collapses to the site-centroid dot, the same fallback a no-room line takes.
            var anchors = ClusterAnchorPlacement.computeClusterAnchors(
                    List.of(List.of("A", "B", "C", "D")), SQUARE_GRID_EDGES,
                    SQUARE_GRID_CENTERED_SITES, SQUARE_GRID_GROUPING,
                    bandSpec(0.0, 0.0, 3, 3, 0.0, 2.0, false, false,
                            3000.0, 4000.0, 1, 1.0),
                    FIXED_LABEL_COLORS,
                    aspectNameEstimators(6.0));

            var anchor = anchors.get(0);
            assertThat(anchor.acceptedAxis()).isNull();
            assertThat(anchor.thickness()).isEqualTo(0f);
            assertThat(anchor.lineCount()).isEqualTo(0);
            assertThat(anchor.nameLines()).isEmpty();
            assertThat(anchor.fontHeight()).isEqualTo(0f);
            assertThat(anchor.anchorX()).isCloseTo(1000f, within(1e-3f));
            assertThat(anchor.anchorY()).isCloseTo(1000f, within(1e-3f));
        }

        // A very slender name (length 500x its line height) so the box solver sizes a
        // band only a few world units thick before it runs out of length: the band then
        // hugs its centreline and the accepted line reproduces the pre-band line fit,
        // letting the line-fit tests above pin the same geometry they always did while
        // the band-fit tests exercise real girth. A single line with a wide font ceiling,
        // so the girth is capped by the region, never the knob, and never split.
        private static final double SLENDER_ASPECT = 500.0;
        private static final double NO_MIN_FONT_SIZE = 0.0;
        private static final double AMPLE_MAX_FONT_SIZE = 2000.0;
        private static final int ONE_LINE = 1;
        private static final double FLUSH_LINES = 1.0;

        // A per-key estimator resolver that hands every cluster the same aspect stand-in -
        // the name-estimator seam the line- and band-fit tests size against.
        private static Function<String, LabelLengthEstimator> aspectNameEstimators(double aspect) {
            return groupKey -> new AspectLabelLengthEstimator(aspect);
        }

        // The slender stand-in resolver behind the line-fit tests.
        private static Function<String, LabelLengthEstimator> slenderNameEstimators() {
            return aspectNameEstimators(SLENDER_ASPECT);
        }

        // A tuning with the fixture's border-trace pair baked in, sized for the slender
        // single-line band that reduces the fit to a line, and both diagnostic toggles
        // off, so each line-fit test names only the search knobs it exercises.
        private static LabelAnchorSpecification spec(double endInsetDistance,
                double iconClearance, int directionCount, int offsetCount,
                double verticalPenaltyStrength, double verticalPenaltyExponent) {
            return spec(endInsetDistance, iconClearance, directionCount, offsetCount,
                    verticalPenaltyStrength, verticalPenaltyExponent, false, false);
        }

        // The full line-fit tuning, for the tests that also exercise the rejected- and
        // unbiased-axis diagnostics; still the slender single-line band.
        private static LabelAnchorSpecification spec(double endInsetDistance,
                double iconClearance, int directionCount, int offsetCount,
                double verticalPenaltyStrength, double verticalPenaltyExponent,
                boolean showRejectedAxis, boolean showUnbiasedAxis) {
            return bandSpec(endInsetDistance, iconClearance, directionCount, offsetCount,
                    verticalPenaltyStrength, verticalPenaltyExponent, showRejectedAxis,
                    showUnbiasedAxis, NO_MIN_FONT_SIZE, AMPLE_MAX_FONT_SIZE,
                    ONE_LINE, FLUSH_LINES);
        }

        // The full tuning with the name-fit knobs exposed, for the tests that exercise real
        // girth: the per-line font clamp and how many lines a name may wrap into. The max
        // slant is 0 throughout, so the label preference collapses to dead-horizontal and
        // these fixtures pin the pre-slant line and band geometry; the slant math is pinned
        // on its own in LabelSlantPreferenceTest.
        private static LabelAnchorSpecification bandSpec(double endInsetDistance,
                double iconClearance, int directionCount, int offsetCount,
                double verticalPenaltyStrength, double verticalPenaltyExponent,
                boolean showRejectedAxis, boolean showUnbiasedAxis, double nameMinFontSize,
                double nameMaxFontSize, int nameMaxLines, double nameLineSpacing) {
            return new LabelAnchorSpecification(
                    new AnchorSearch(new PoliticalBorderTrace(WELD_TOLERANCE, MITER_LIMIT),
                            endInsetDistance, iconClearance, directionCount, offsetCount),
                    new LeanScoring(verticalPenaltyStrength, verticalPenaltyExponent, 0.0),
                    new AnchorDiagnostics(showRejectedAxis, showUnbiasedAxis),
                    new NameFitSpecification(nameMinFontSize, nameMaxFontSize, nameMaxLines,
                            nameLineSpacing));
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
                    edge(minX, minY, maxX, minY, bottomNeighbour),
                    edge(maxX, minY, maxX, maxY, rightNeighbour),
                    edge(maxX, maxY, minX, maxY, topNeighbour),
                    edge(minX, maxY, minX, minY, leftNeighbour));
        }

        // One cell edge facing the given neighbour system, or the reach bound when it is null.
        private static CellEdge edge(
                double x1, double y1, double x2, double y2, String neighbour) {
            return new CellEdge(x1, y1, x2, y2,
                    neighbour == null
                            ? EdgeTarget.REACH_BOUND
                            : new EdgeTarget.AcrossSystem(neighbour));
        }
    }

    /**
     * A name estimator with the aspect stand-in's arithmetic but real lines behind it,
     * so a test can pin that the anchor carries exactly the wrap the fit sized: line count
     * {@code n} wraps to {@code "Line 1".."Line n"}.
     */
    private record LabelLengthEstimatorFake(double aspect) implements LabelLengthEstimator {

        @Override
        public double requiredLengthFor(double lineHeight, int lineCount) {
            return aspect * lineHeight / lineCount;
        }

        @Override
        public List<String> wrapIntoLines(int lineCount) {
            var lines = new ArrayList<String>(lineCount);
            for (var lineNumber = 1; lineNumber <= lineCount; lineNumber++) {
                lines.add("Line " + lineNumber);
            }
            return lines;
        }
    }
}
