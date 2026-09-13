package kmu.maplayers.base.labels.anchor;

import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.label.AspectLabelLengthEstimator;
import kmlib.starsector.ui.label.BandFitSpecification;
import kmlib.starsector.ui.label.LabelLengthEstimator;
import kmlib.starsector.ui.label.NameFitSpecification;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.labels.anchor.specifications.AnchorDiagnostics;
import kmu.maplayers.base.labels.anchor.specifications.AnchorSearch;
import kmu.maplayers.base.labels.anchor.specifications.LabelAnchorSpecification;
import kmu.maplayers.base.labels.anchor.specifications.LeanScoring;
import kmu.maplayers.base.render.clusters.ClusterBorderTrace;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildDrawnSystemKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;

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
 * owner, so what is pinned here is that the search asks with the right key and
 * carries the answers onto the anchor - what those answers should be belongs to whoever
 * resolves them.
 */
final class ClusterAnchorPlacementTest {

    // The shade the injected resolver hands every cluster, so a test can tell the carried
    // colour apart from any default.
    private static final Color LABEL_COLOUR = Color.RED;

    // The colour seam the geometry tests run under: one shade for every owner, since
    // none of them assert on colour.
    private static final Function<String, Color> FIXED_LABEL_COLOURS = owner -> LABEL_COLOUR;

    // The grouping the anchor search traces under: each cell drawing as its own star (identity
    // draws-as over the group-key map's cells, all grouped cluster members here), keyed by the
    // given owners.
    private static CellGrouping buildGrouping(Map<String, String> ownerBySystemId) {
        var systemIdByCellId = new LinkedHashMap<String, String>();
        for (var cellId : ownerBySystemId.keySet()) {
            systemIdByCellId.put(cellId, cellId);
        }
        return new CellGrouping(buildDrawnSystemKeys(systemIdByCellId), ownerBySystemId);
    }

    // The anchors alone, for the geometry tests: they assert on where a label landed, not
    // on what finding it cost, so they read past the sweep's counts here rather than each
    // unwrapping the fit. Offered nothing to carry over, so every case here fits for real -
    // the reuse cases hand in a previous sweep's anchors themselves.
    private static List<ClusterAnchor> computeAnchors(
            ClusterPartition partition,
            LabelAnchorSpecification spec,
            ClusterLabelResolvers labelResolvers) {

        return ClusterAnchorPlacement.computeClusterAnchors(
            partition,
            spec,
            labelResolvers,
            Map.of()).anchors();
    }

    // A previous sweep's anchors as the standing placements a later one is offered - filed
    // under the cluster each of them names, which is the form the rebuild hands them in. Built
    // from a real sweep rather than from hand-made anchors so the carried components are ones
    // the search actually produced.
    private static Map<ClusterIdentity, ClusterAnchor> indexByIdentity(
            List<ClusterAnchor> anchors) {

        var anchorByIdentity = new LinkedHashMap<ClusterIdentity, ClusterAnchor>();
        for (var anchor : anchors) {
            anchorByIdentity.put(anchor.identity(), anchor);
        }
        return anchorByIdentity;
    }

    @Nested
    class ComputeClusterAnchors {

        // The cluster geometry the searches run against: cells sized well clear of the
        // fixed 150-unit border inset, welded and mitred with the same kind of values
        // the production border trace uses.
        private static final double CELL_SIDE = 1000.0;
        private static final double WELD_TOLERANCE = 1.0;
        private static final double MITER_LIMIT = 4.0;

        // The one owner every fixture cluster falls under, so a cluster's key is
        // known where a test asserts the search looked one up.
        private static final String GROUP_KEY = "F";

        // Two 1000-unit cells side by side: the cluster spans x 0..2000, y 0..1000, so
        // the border rings inset to x 150..1850, y 150..850 and a horizontal line is the
        // longest interior chord by far (the cluster is wide and short, so every slanted
        // line is height-limited and much shorter).
        private static final Map<SystemKey, List<CellEdge>> HORIZONTAL_PAIR_EDGES = buildKeyedValues(Map.of(
            "A", listSquareCellEdges(0, 0, null, "B", null, null),
            "B", listSquareCellEdges(CELL_SIDE, 0, null, null, null, "A")));
        private static final Map<SystemKey, double[]> HORIZONTAL_PAIR_SITES = buildKeyedValues(Map.of(
            "A", new double[] {500, 500}, "B", new double[] {1500, 500}));
        private static final CellGrouping HORIZONTAL_PAIR_GROUPING = buildGrouping(Map.of(
            "A", GROUP_KEY, "B", GROUP_KEY));

        // The identity that pair's cluster must report, spelled out from the fixture's own
        // key and members rather than read back off the grouping the search was handed, so a
        // search that named the wrong cluster fails instead of agreeing with itself.
        private static final ClusterIdentity HORIZONTAL_PAIR_IDENTITY =
            new ClusterIdentity("F", Set.of(buildCellKey("A"), buildCellKey("B")));

        // A second owner, for the case that sweeps two clusters at once.
        private static final String RIVAL_GROUP_KEY = "G";

        // Two 500-wide, 1000-tall cells stacked: the cluster is genuinely tall and thin
        // (inset x 150..350, y 150..1850), so a slanted line is width-limited to a short
        // chord and only the vertical line runs the cluster's full length.
        private static final Map<SystemKey, List<CellEdge>> THIN_COLUMN_EDGES = buildKeyedValues(Map.of(
            "A", listRectangleCellEdges(0, 0, 500, CELL_SIDE, null, null, "B", null),
            "B", listRectangleCellEdges(0, CELL_SIDE, 500, CELL_SIDE, "A", null, null, null)));
        private static final Map<SystemKey, double[]> THIN_COLUMN_SITES = buildKeyedValues(Map.of(
            "A", new double[] {250, 500}, "B", new double[] {250, 1500}));
        private static final CellGrouping THIN_COLUMN_GROUPING = buildGrouping(Map.of(
            "A", GROUP_KEY, "B", GROUP_KEY));

        // A 2x2 block of 1000-unit cells: a square cluster (inset x 150..1850,
        // y 150..1850) where horizontal, vertical, and diagonal chords are all comparably
        // long, so the vertical penalty - not raw length - decides the winner.
        private static final Map<SystemKey, List<CellEdge>> SQUARE_GRID_EDGES = buildKeyedValues(Map.of(
            "A", listSquareCellEdges(0, 0, null, "B", "C", null),
            "B", listSquareCellEdges(CELL_SIDE, 0, null, null, "D", "A"),
            "C", listSquareCellEdges(0, CELL_SIDE, "A", "D", null, null),
            "D", listSquareCellEdges(CELL_SIDE, CELL_SIDE, "B", null, null, "C")));
        private static final CellGrouping SQUARE_GRID_GROUPING = buildGrouping(Map.of(
            "A", GROUP_KEY, "B", GROUP_KEY, "C", GROUP_KEY, "D", GROUP_KEY));

        // Sites strung vertically down the square's centre: the cluster's principal axis
        // reads vertical though the cluster is square, the setup that makes the penalty
        // flip the accepted line horizontal.
        private static final Map<SystemKey, double[]> SQUARE_GRID_VERTICAL_SITES = buildKeyedValues(Map.of(
            "A", new double[] {1000, 200}, "B", new double[] {1000, 700},
            "C", new double[] {1000, 1300}, "D", new double[] {1000, 1800}));

        // Sites at the cell centres: a square point cloud with no preferred axis, used
        // where the winner should be decided purely by length.
        private static final Map<SystemKey, double[]> SQUARE_GRID_CENTRED_SITES = buildKeyedValues(Map.of(
            "A", new double[] {500, 500}, "B", new double[] {1500, 500},
            "C", new double[] {500, 1500}, "D", new double[] {1500, 1500}));

        // A boot-shaped cluster: a bottom arm (x 0..3000) and a left arm (x 0..1000,
        // y 0..3000) meeting at the origin, with the top-right a deep concave notch. The
        // sites' mean lands in that notch - outside the cluster's border - so any line
        // through the centroid finds only the short left-arm chord, while the arms
        // themselves carry a chord nearly three cells long.
        private static final Map<SystemKey, List<CellEdge>> BOOT_EDGES = buildKeyedValues(Map.of(
            "A", listSquareCellEdges(0, 0, null, "B", "D", null),
            "B", listSquareCellEdges(CELL_SIDE, 0, null, "C", null, "A"),
            "C", listSquareCellEdges(2 * CELL_SIDE, 0, null, null, null, "B"),
            "D", listSquareCellEdges(0, CELL_SIDE, "A", null, "E", null),
            "E", listSquareCellEdges(0, 2 * CELL_SIDE, "D", null, null, null)));
        private static final Map<SystemKey, double[]> BOOT_SITES = buildKeyedValues(Map.of(
            "A", new double[] {500, 500}, "B", new double[] {1500, 500},
            "C", new double[] {2500, 500}, "D", new double[] {500, 1500},
            "E", new double[] {500, 2500}));
        private static final CellGrouping BOOT_GROUPING = buildGrouping(Map.of(
            "A", GROUP_KEY, "B", GROUP_KEY, "C", GROUP_KEY, "D", GROUP_KEY,
            "E", GROUP_KEY));

        // The lone-member grouping the single-system fits run under.
        private static final CellGrouping SINGLE_SYSTEM_GROUPING =
            buildGrouping(Map.of("A", GROUP_KEY));

        // The partitions the sweeps run over, each composing one fixture's clusters with the
        // cells they were cut from. Named here rather than assembled at every case, so a case
        // states which geometry it is about instead of restating four pieces that only mean
        // anything together - and two cases meaning to run over the same layout cannot drift
        // into running over a slightly different one.

        // The pair fused under one owner: the wide, short cluster most line-fit cases sweep.
        private static final ClusterPartition FUSED_PAIR_PARTITION = new ClusterPartition(
            List.of(buildCellKeys("A", "B")),
            HORIZONTAL_PAIR_EDGES,
            HORIZONTAL_PAIR_SITES,
            HORIZONTAL_PAIR_GROUPING);

        // The same two cells split between two owners, so one sweep fits two clusters. Read
        // against the fused pair above it is also the same layout before and after a split or a
        // merge, which is what the carry-over cases turn on.
        private static final ClusterPartition SPLIT_PAIR_PARTITION = new ClusterPartition(
            List.of(buildCellKeys("A"), buildCellKeys("B")),
            HORIZONTAL_PAIR_EDGES,
            HORIZONTAL_PAIR_SITES,
            buildGrouping(Map.of("A", GROUP_KEY, "B", RIVAL_GROUP_KEY)));

        // The fused pair with no cell edges: nothing traces a border ring, so no candidate can
        // be proven interior and only the dot can show.
        private static final ClusterPartition UNTRACEABLE_PAIR_PARTITION = new ClusterPartition(
            List.of(buildCellKeys("A", "B")),
            Map.of(),
            HORIZONTAL_PAIR_SITES,
            HORIZONTAL_PAIR_GROUPING);

        private static final ClusterPartition THIN_COLUMN_PARTITION = new ClusterPartition(
            List.of(buildCellKeys("A", "B")),
            THIN_COLUMN_EDGES,
            THIN_COLUMN_SITES,
            THIN_COLUMN_GROUPING);

        private static final ClusterPartition SQUARE_GRID_VERTICAL_PARTITION =
            new ClusterPartition(
                List.of(buildCellKeys("A", "B", "C", "D")),
                SQUARE_GRID_EDGES,
                SQUARE_GRID_VERTICAL_SITES,
                SQUARE_GRID_GROUPING);

        private static final ClusterPartition SQUARE_GRID_CENTRED_PARTITION =
            new ClusterPartition(
                List.of(buildCellKeys("A", "B", "C", "D")),
                SQUARE_GRID_EDGES,
                SQUARE_GRID_CENTRED_SITES,
                SQUARE_GRID_GROUPING);

        private static final ClusterPartition BOOT_PARTITION = new ClusterPartition(
            List.of(buildCellKeys("A", "B", "C", "D", "E")),
            BOOT_EDGES,
            BOOT_SITES,
            BOOT_GROUPING);

        // One system whose own 2000x1000 cell shape is the only thing carrying a direction to
        // fit, its site cloud having none.
        private static final ClusterPartition SINGLE_WIDE_CELL_PARTITION = new ClusterPartition(
            List.of(buildCellKeys("A")),
            buildKeyedValues(Map.of(
                "A",
                listRectangleCellEdges(0, 0, 2 * CELL_SIDE, CELL_SIDE, null, null, null, null))),
            buildKeyedValues(Map.of("A", new double[] {1000, 500})),
            SINGLE_SYSTEM_GROUPING);

        // A cluster whose members carry no site at all, so there is no point cloud to fit.
        private static final ClusterPartition SITELESS_PARTITION = new ClusterPartition(
            List.of(buildCellKeys("A")),
            Map.of(),
            Map.of(),
            SINGLE_SYSTEM_GROUPING);

        @Test
        void computeClusterAnchorsClipsTheAcceptedLineInsideTheClusterBorder() {
            // No icon keep-out and no end margin, so the winning horizontal line is the
            // full interior span: it reaches the inset border rings (x 150..1850) - past
            // the sites, but never out of the border.
            var anchors = computeAnchors(
                FUSED_PAIR_PARTITION,
                buildSpec(0.0, 0.0, 3, 1, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            var accepted = anchors.get(0).acceptedAxis();

            assertThat(accepted)
                .isNotNull();
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                .isCloseTo(150.0, within(1e-3));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                .isCloseTo(1850.0, within(1e-3));
            assertThat(accepted.startY())
                .isCloseTo(500.0, within(1e-3));
            assertThat(accepted.endY())
                .isCloseTo(500.0, within(1e-3));
        }

        @Test
        void computeClusterAnchorsRelocatesToAParallelOffsetToClearAnIconOnTheCentreLine() {
            // A 150-unit keep-out around the sites at y=500 blocks the centre line but
            // clears the parallel offsets at y=325 and y=675 (175 away). Rather than
            // shrink the centred line to a stub between the icons, the search slides the
            // whole line off-centre to the first clear offset and keeps its full length.
            var anchors = computeAnchors(
                FUSED_PAIR_PARTITION,
                buildSpec(0.0, 150.0, 3, 3, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            var anchor = anchors.get(0);
            var accepted = anchor.acceptedAxis();

            assertThat(accepted)
                .isNotNull();
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                .isCloseTo(150.0, within(1e-3));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                .isCloseTo(1850.0, within(1e-3));
            assertThat(accepted.startY())
                .isCloseTo(325.0, within(1e-3));
            assertThat(accepted.endY())
                .isCloseTo(325.0, within(1e-3));

            // The anchor point follows the relocated line, not the sites.
            assertThat(anchor.anchorX())
                .isCloseTo(1000f, within(1e-3f));
            assertThat(anchor.anchorY())
                .isCloseTo(325f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsPullsEachEndInwardByTheEndInset() {
            // The 100-unit end inset pulls the winning horizontal span (x 150..1850) in
            // from both ends, leaving the border gap a name needs on each side.
            var anchors = computeAnchors(
                FUSED_PAIR_PARTITION,
                buildSpec(100.0, 0.0, 3, 1, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            var accepted = anchors.get(0).acceptedAxis();

            assertThat(accepted)
                .isNotNull();
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
            var anchors = computeAnchors(
                THIN_COLUMN_PARTITION,
                buildSpec(0.0, 0.0, 3, 3, 0.5, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            var accepted = anchors.get(0).acceptedAxis();

            assertThat(accepted)
                .isNotNull();
            assertThat(accepted.startX())
                .isCloseTo(accepted.endX(), within(1e-3));
            assertThat(accepted.startX())
                .isBetween(150.0, 350.0);
            assertThat(Math.min(accepted.startY(), accepted.endY()))
                .isCloseTo(150.0, within(1e-3));
            assertThat(Math.max(accepted.startY(), accepted.endY()))
                .isCloseTo(1850.0, within(1e-3));
        }

        @Test
        void computeClusterAnchorsFlipsAWideVerticalCloudHorizontalUnderThePenalty() {
            // The cluster is square, so its horizontal and vertical chords are the same
            // 1700 units even though the site cloud is strung vertically. The penalty
            // docks the vertical line by half and leaves the horizontal one whole, so the
            // accepted line runs horizontal against the cloud's own axis.
            var anchors = computeAnchors(
                SQUARE_GRID_VERTICAL_PARTITION,
                buildSpec(0.0, 0.0, 3, 3, 0.5, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            var accepted = anchors.get(0).acceptedAxis();

            assertThat(accepted)
                .isNotNull();
            assertThat(accepted.startY())
                .isCloseTo(accepted.endY(), within(1e-3));
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
            var anchors = computeAnchors(
                SQUARE_GRID_CENTRED_PARTITION,
                buildSpec(0.0, 0.0, 3, 3, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            var accepted = anchors.get(0).acceptedAxis();

            assertThat(accepted)
                .isNotNull();

            var length = Math.hypot(
                accepted.endX() - accepted.startX(),
                accepted.endY() - accepted.startY());

            assertThat(length)
                .isGreaterThan(1900.0);

            // Genuinely diagonal: neither axis-aligned.
            assertThat(Math.abs(accepted.endX() - accepted.startX()))
                .isGreaterThan(1f);
            assertThat(Math.abs(accepted.endY() - accepted.startY()))
                .isGreaterThan(1f);
        }

        @Test
        void computeClusterAnchorsPlacesTheAnchorDotAtTheAcceptedLineMidpoint() {
            // The centred horizontal winner spans x 150..1850 at y 500, so its midpoint -
            // the dot and the label's hang-point - is (1000, 500).
            var anchors = computeAnchors(
                FUSED_PAIR_PARTITION,
                buildSpec(0.0, 0.0, 3, 1, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            var anchor = anchors.get(0);
            var accepted = anchor.acceptedAxis();

            assertThat((double) anchor.anchorX())
                .isCloseTo((accepted.startX() + accepted.endX()) / 2, within(1e-3));
            assertThat((double) anchor.anchorY())
                .isCloseTo((accepted.startY() + accepted.endY()) / 2, within(1e-3));

            assertThat(anchor.anchorX())
                .isCloseTo(1000f, within(1e-3f));
            assertThat(anchor.anchorY())
                .isCloseTo(500f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsFindsALineDeepInAConcaveClusterWhoseCentroidIsOutside() {
            // The boot's site centroid falls in the concave notch, outside the border, so
            // a line through it would clip only the 700-unit left arm. The offset sweep
            // instead reaches the bottom arm and returns a chord nearly three cells long -
            // the payoff of freeing the line from the centroid.
            var anchors = computeAnchors(
                BOOT_PARTITION,
                buildSpec(0.0, 0.0, 3, 5, 0.5, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            var accepted = anchors.get(0).acceptedAxis();

            assertThat(accepted)
                .isNotNull();

            var length = Math.hypot(
                accepted.endX() - accepted.startX(),
                accepted.endY() - accepted.startY());

            assertThat(length)
                .isGreaterThan(2200.0);
        }

        @Test
        void computeClusterAnchorsFitsASingleSystemClusterALineAlongItsCellShape() {
            // One site has no spread of its own, so its cell's own 2000x1000 shape feeds a
            // horizontal principal-axis candidate; the search lands the full interior span
            // (x 150..1850) - a single-system cluster still carries a real line.
            var anchors = computeAnchors(
                SINGLE_WIDE_CELL_PARTITION,
                buildSpec(0.0, 0.0, 3, 1, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            assertThat(anchors)
                .hasSize(1);

            var accepted = anchors.get(0).acceptedAxis();

            assertThat(accepted)
                .isNotNull();
            assertThat(Math.min(accepted.startX(), accepted.endX()))
                .isCloseTo(150.0, within(1e-3));
            assertThat(Math.max(accepted.startX(), accepted.endX()))
                .isCloseTo(1850.0, within(1e-3));
            assertThat(accepted.startY())
                .isCloseTo(500.0, within(1e-3));
            assertThat(accepted.endY())
                .isCloseTo(500.0, within(1e-3));
        }

        @Test
        void computeClusterAnchorsCollapsesToTheSiteCentroidDotWhenNoLineFits() {
            // A 1000-unit end inset asks for 2000 units of margin from a 1700-unit longest
            // chord: no candidate survives, so the anchor is just the dot at the site
            // centroid (1000, 500) - no lines, no name, no font. With the diagnostic
            // toggles off it carries no lines.
            var anchors = computeAnchors(
                FUSED_PAIR_PARTITION,
                buildSpec(1000.0, 0.0, 3, 3, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            var anchor = anchors.get(0);

            assertThat(anchor.acceptedAxis()).isNull();
            assertThat(anchor.rejectedAxis()).isNull();
            assertThat(anchor.unbiasedAxis()).isNull();
            assertThat(anchor.nameLines()).isEmpty();
            assertThat(anchor.fontHeight()).isEqualTo(0f);

            assertThat(anchor.anchorX())
                .isCloseTo(1000f, within(1e-3f));
            assertThat(anchor.anchorY())
                .isCloseTo(500f, within(1e-3f));
        }

        @Test
        void computeClusterAnchorsReportsTheBestRejectedCandidateWhenTheSearchCollapses() {
            // Same no-room collapse, but with the rejected-axis toggle on: the best
            // candidate the search had - the full pre-inset clear span (x 150..1850) -
            // comes back for the red line, showing how close the cluster came.
            var anchors = computeAnchors(
                FUSED_PAIR_PARTITION,
                buildSpec(
                    1000.0,
                    0.0,
                    3,
                    1,
                    0.0,
                    2.0,
                    true,
                    false),
                createLabelResolvers(buildSlenderNameEstimators()));

            var anchor = anchors.get(0);

            assertThat(anchor.acceptedAxis())
                .isNull();
            var rejected = anchor.rejectedAxis();

            assertThat(rejected)
                .isNotNull();
            assertThat(Math.min(rejected.startX(), rejected.endX()))
                .isCloseTo(150.0, within(1e-3));
            assertThat(Math.max(rejected.startX(), rejected.endX()))
                .isCloseTo(1850.0, within(1e-3));
            assertThat(rejected.startY())
                .isCloseTo(500.0, within(1e-3));
            assertThat(rejected.endY())
                .isCloseTo(500.0, within(1e-3));
        }

        @Test
        void computeClusterAnchorsReportsTheUnbiasedLineWhenThePenaltyMovesThePick() {
            // In the square cluster the penalty accepts the horizontal 1700 line while the
            // pure longest line is the ~1963 diagonal. With the unbiased toggle on, that
            // diagonal rides along for the yellow comparison.
            var anchors = computeAnchors(
                SQUARE_GRID_VERTICAL_PARTITION,
                buildSpec(
                    0.0,
                    0.0,
                    3,
                    3,
                    0.5,
                    2.0,
                    false,
                    true),
                createLabelResolvers(buildSlenderNameEstimators()));

            var anchor = anchors.get(0);
            var accepted = anchor.acceptedAxis();

            assertThat(accepted)
                .isNotNull();
            assertThat(accepted.startY())
                .isCloseTo(accepted.endY(), within(1e-3));

            var unbiased = anchor.unbiasedAxis();

            assertThat(unbiased)
                .isNotNull();

            var unbiasedLength = Math.hypot(
                unbiased.endX() - unbiased.startX(),
                unbiased.endY() - unbiased.startY());

            assertThat(unbiasedLength)
                .isGreaterThan(1900.0);
        }

        @Test
        void computeClusterAnchorsOmitsTheUnbiasedLineWhenThePenaltyDoesNotMoveThePick() {
            // In the tall thin column the longest line is also the accepted one (vertical
            // wins on length even penalised), so the pure-longest line coincides with the
            // accepted line and no separate yellow line is built.
            var anchors = computeAnchors(
                THIN_COLUMN_PARTITION,
                buildSpec(
                    0.0,
                    0.0,
                    3,
                    3,
                    0.5,
                    2.0,
                    false,
                    true),
                createLabelResolvers(buildSlenderNameEstimators()));

            assertThat(anchors.get(0).unbiasedAxis())
                .isNull();
        }

        @Test
        void computeClusterAnchorsCollapsesToTheDotWhenNoBorderRingTraces() {
            // Members with no cell edges yield no border ring to clip against, so there is
            // nothing to prove a line interior - the dot at the site centroid only.
            var anchors = computeAnchors(
                UNTRACEABLE_PAIR_PARTITION,
                buildSpec(0.0, 0.0, 3, 3, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            var anchor = anchors.get(0);

            assertThat(anchor.anchorX())
                .isCloseTo(1000f, within(1e-4f));
            assertThat(anchor.acceptedAxis())
                .isNull();
            assertThat(anchor.nameLines())
                .isEmpty();
        }

        @Test
        void computeClusterAnchorsResolvesTheNameEstimatorByTheClustersGroupingKey() {
            // The injected estimator is what the fit sizes against and what wraps the
            // label's lines, so the resolver must be asked with the key the cluster's
            // members carry - the search's only handle on which name this box is for.
            var askedGroupKeys = new ArrayList<String>();
            Function<String, LabelLengthEstimator> recordingResolver = owner -> {
                askedGroupKeys.add(owner);
                return new AspectLabelLengthEstimator(SLENDER_ASPECT);
            };
            computeAnchors(
                FUSED_PAIR_PARTITION,
                buildSpec(0.0, 0.0, 3, 1, 0.0, 2.0),
                createLabelResolvers(recordingResolver));

            assertThat(askedGroupKeys)
                .containsExactly(GROUP_KEY);
        }

        @Test
        void computeClusterAnchorsCarriesTheInjectedColourForTheClustersGroupingKey() {
            // The shade a name and its dot draw in is resolved outside the search and looked
            // up by the same key the name is: the search only carries it onto the anchor, so
            // it never has to know what makes one cluster's colour differ from another's.
            var askedGroupKeys = new ArrayList<String>();
            Function<String, Color> recordingColours = owner -> {
                askedGroupKeys.add(owner);
                return Color.MAGENTA;
            };
            var anchors = computeAnchors(
                FUSED_PAIR_PARTITION,
                buildSpec(0.0, 0.0, 3, 1, 0.0, 2.0),
                new ClusterLabelResolvers(recordingColours, buildSlenderNameEstimators()));

            assertThat(askedGroupKeys)
                .containsExactly(GROUP_KEY);
            assertThat(anchors.get(0).colour())
                .isEqualTo(Color.MAGENTA);
        }

        @Test
        void computeClusterAnchorsNamesTheClusterEachAnchorWasFittedTo() {
            // The anchors are all a rebuild inherits, so a placement that could not say
            // which cluster it was made for could never be matched against a later
            // rebuild's clusters - the owner its name and shade came from, and the members
            // whose cells bounded the search, are both part of that answer.
            var anchors = computeAnchors(
                FUSED_PAIR_PARTITION,
                buildSpec(0.0, 0.0, 3, 1, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            assertThat(anchors.get(0).identity())
                .isEqualTo(HORIZONTAL_PAIR_IDENTITY);
        }

        @Test
        void computeClusterAnchorsNamesEachClusterOfASweepAfterItsOwnMembers() {
            // The same two cells split between two owners, so the sweep fits two clusters in
            // one call. An identity resolved once for the whole call - or left over from the
            // cluster before - would have both anchors claiming the same owner or the same
            // members, which is exactly the mismatch that makes a placement reusable for a
            // cluster it was never fitted to.
            var anchors = computeAnchors(
                SPLIT_PAIR_PARTITION,
                buildSpec(0.0, 0.0, 3, 1, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            assertThat(anchors)
                .extracting(ClusterAnchor::identity)
                .containsExactly(
                    new ClusterIdentity("F", Set.of(buildCellKey("A"))),
                    new ClusterIdentity("G", Set.of(buildCellKey("B"))));
        }

        @Test
        void computeClusterAnchorsNamesTheClusterWhenNoLineFits() {
            // The no-room collapse mints its own anchor. An unnamed dot would have to be
            // re-fitted every rebuild, and re-proving that a cluster still has no room is
            // the one search there is least point repeating.
            var anchors = computeAnchors(
                FUSED_PAIR_PARTITION,
                buildSpec(1000.0, 0.0, 3, 3, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            assertThat(anchors.get(0).acceptedAxis())
                .isNull();
            assertThat(anchors.get(0).identity())
                .isEqualTo(HORIZONTAL_PAIR_IDENTITY);
        }

        @Test
        void computeClusterAnchorsNamesTheClusterWhenNoBorderRingTraces() {
            // The third and last way an anchor is minted: the dead end where no border
            // traces at all. It names its cluster like the other two, so no path out of
            // the search produces a placement that cannot be recognised.
            var anchors = computeAnchors(
                UNTRACEABLE_PAIR_PARTITION,
                buildSpec(0.0, 0.0, 3, 3, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            assertThat(anchors.get(0).acceptedAxis())
                .isNull();
            assertThat(anchors.get(0).identity())
                .isEqualTo(HORIZONTAL_PAIR_IDENTITY);
        }

        @Test
        void computeClusterAnchorsCarriesAStandingPlacementRatherThanSearchingItsClusterAgain() {
            // The saving the partial re-fit exists for. A cluster a standing placement already
            // names costs no candidates and no band fits at all - a search that happened to
            // land in the same place would still have spent both, so the counts are what says
            // the sweep skipped it rather than repeated it.
            var standing = fitTheHorizontalPair(createLabelResolvers(buildSlenderNameEstimators()));

            var fit = refitTheHorizontalPairOffering(
                standing,
                createLabelResolvers(buildSlenderNameEstimators()));

            assertThat(fit.candidateCount())
                .isEqualTo(0);
            assertThat(fit.bandFitCount())
                .isEqualTo(0);
            assertThat(fit.anchors().get(0).acceptedAxis())
                .isSameAs(standing.get(0).acceptedAxis());
        }

        @Test
        void computeClusterAnchorsTakesTheFreshShadeOntoACarriedPlacement() {
            // An owner that only changed appearance - the recede a filter switch applies to every
            // owner but the spotlit one - keeps its geometry and takes the shade this pass
            // resolved. Colour is no input to the fit, so it is the one component a carried
            // placement is allowed to differ in, and the one it must.
            var standing = fitTheHorizontalPair(createLabelResolvers(buildSlenderNameEstimators()));

            var fit = refitTheHorizontalPairOffering(
                standing,
                new ClusterLabelResolvers(owner -> Color.MAGENTA, buildSlenderNameEstimators()));

            assertThat(fit.anchors().get(0).colour())
                .isEqualTo(Color.MAGENTA);
            assertThat(fit.anchors().get(0).acceptedAxis())
                .isSameAs(standing.get(0).acceptedAxis());
            assertThat(fit.bandFitCount())
                .isEqualTo(0);
        }

        @Test
        void computeClusterAnchorsCarriesAPlacementWhoseNameStillWrapsTheSameWay() {
            // The positive half of the wrap guard, run against a name with real lines rather
            // than the stand-in: an unchanged name resolves to the lines the box was measured
            // for, so the guard lets the placement through rather than making every named
            // cluster re-fit.
            var standing = fitTheHorizontalPair(
                createLabelResolvers(buildNamedSingleLineEstimators("Line")));

            var fit = refitTheHorizontalPairOffering(
                standing,
                createLabelResolvers(buildNamedSingleLineEstimators("Line")));

            assertThat(fit.bandFitCount())
                .isEqualTo(0);
            assertThat(fit.anchors().get(0).nameLines())
                .containsExactly("Line 1");
        }

        @Test
        void computeClusterAnchorsRefitsARenamedClusterRatherThanCarryingItsOldBox() {
            // Same owner, same members, different name: the identity matches, so nothing but
            // the wrap can catch this. The box was sized against the name it was measured
            // with, so carrying it would draw the new name in a box cut for the old one.
            var standing = fitTheHorizontalPair(
                createLabelResolvers(buildNamedSingleLineEstimators("Line")));

            var fit = refitTheHorizontalPairOffering(
                standing,
                createLabelResolvers(buildNamedSingleLineEstimators("Renamed")));

            assertThat(fit.bandFitCount())
                .isGreaterThan(0);
            assertThat(fit.anchors().get(0).nameLines())
                .containsExactly("Renamed 1");
        }

        @Test
        void computeClusterAnchorsRefitsAClusterThatSplitInTwo() {
            // The fused pair's placement names both members, so neither half of the split
            // matches it and both are searched afresh. Nothing here had to notice the split:
            // the member set did, which is what makes the hazard structural rather than a
            // check somebody has to remember.
            var standing = fitTheHorizontalPair(createLabelResolvers(buildSlenderNameEstimators()));

            var fit = ClusterAnchorPlacement.computeClusterAnchors(
                SPLIT_PAIR_PARTITION,
                createPairTuning(),
                createLabelResolvers(buildSlenderNameEstimators()),
                indexByIdentity(standing));

            assertThat(fit.bandFitCount())
                .isGreaterThan(0);
            assertThat(fit.anchors())
                .extracting(ClusterAnchor::identity)
                .containsExactly(
                    new ClusterIdentity("F", Set.of(buildCellKey("A"))),
                    new ClusterIdentity("G", Set.of(buildCellKey("B"))));
        }

        @Test
        void computeClusterAnchorsRefitsAClusterThatMergedIntoOne() {
            // The other direction: two standing placements, each naming one cell, and a cluster
            // that now spans both. A merged cluster's member set matches neither, so the pair
            // it came from cannot be carried onto it - a box fitted inside one cell would sit
            // in a corner of the cluster it now names.
            var standing = computeAnchors(
                SPLIT_PAIR_PARTITION,
                createPairTuning(),
                createLabelResolvers(buildSlenderNameEstimators()));

            var fit = refitTheHorizontalPairOffering(
                standing,
                createLabelResolvers(buildSlenderNameEstimators()));

            assertThat(fit.bandFitCount())
                .isGreaterThan(0);
            assertThat(fit.anchors())
                .extracting(ClusterAnchor::identity)
                .containsExactly(HORIZONTAL_PAIR_IDENTITY);
        }

        @Test
        void computeClusterAnchorsRefitsWhenTheNameCanNoLongerFillTheCarriedLineCount() {
            // A stand-in has no text, so it wraps to nothing at every line count - and so does a
            // real name at a line count it has too few words to fill. Comparing the wraps alone
            // reads the two alike, which would carry a two-line stand-in box onto a name that
            // can only make one line and then draw no name in it. The box's line count still
            // being fillable is what tells them apart.
            var tuning = buildBandSpec(
                0.0,
                0.0,
                3,
                3,
                0.0,
                2.0,
                false,
                false,
                100.0,
                1700.0,
                2,
                1.15);

            var standing = computeAnchors(
                SQUARE_GRID_CENTRED_PARTITION,
                tuning,
                createLabelResolvers(buildAspectNameEstimators(6.0)));

            assertThat(standing.get(0).lineCount())
                .isEqualTo(2);
            assertThat(standing.get(0).nameLines())
                .isEmpty();

            var fit = ClusterAnchorPlacement.computeClusterAnchors(
                SQUARE_GRID_CENTRED_PARTITION,
                tuning,
                createLabelResolvers(owner -> new LabelLengthEstimatorFake(6.0, "Line", ONE_LINE)),
                indexByIdentity(standing));

            assertThat(fit.bandFitCount())
                .isGreaterThan(0);
            assertThat(fit.anchors().get(0).nameLines())
                .containsExactly("Line 1");
        }

        @Test
        void computeClusterAnchorsRefitsACollapsedPlacementRatherThanCarryingIt() {
            // A collapse fitted no box, so it recorded no measured name for the wrap guard to
            // read - and a name that has since grown shorter is exactly the case where a
            // cluster that had no room now has some. Re-proving it is cheap; assuming it is
            // unsound, so the dot is searched again like anything else that cannot be checked.
            var tuning = buildSpec(1000.0, 0.0, 3, 3, 0.0, 2.0);
            var standing = computeAnchors(
                FUSED_PAIR_PARTITION,
                tuning,
                createLabelResolvers(buildSlenderNameEstimators()));

            assertThat(standing.get(0).acceptedAxis())
                .isNull();

            var fit = ClusterAnchorPlacement.computeClusterAnchors(
                FUSED_PAIR_PARTITION,
                tuning,
                createLabelResolvers(buildSlenderNameEstimators()),
                indexByIdentity(standing));

            assertThat(fit.bandFitCount())
                .isGreaterThan(0);
        }

        @Test
        void computeClusterAnchorsSkipsAClusterWhoseSitesAreAllMissing() {
            // A cluster whose members have no site (none in the site map) has no point
            // cloud to fit, so it contributes no anchor rather than an empty fit.
            var anchors = computeAnchors(
                SITELESS_PARTITION,
                buildSpec(0.0, 0.0, 3, 1, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()));

            assertThat(anchors).isEmpty();
        }

        @Test
        void computeClusterAnchorsCountsTheCandidatesAsTheResolvedFanCrossedWithTheOffsets() {
            // The reported candidates must be what the sweep visited, not the fan width it
            // was tuned with: a 3-wide fan resolves to 5 directions once the cluster's own
            // axis and the preferred slant join it, and each is swept at all 4 offsets.
            var fit = ClusterAnchorPlacement.computeClusterAnchors(
                FUSED_PAIR_PARTITION,
                buildSpec(0.0, 0.0, 3, 4, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()),
                Map.of());

            assertThat(fit.candidateCount())
                .isEqualTo(20);
        }

        @Test
        void computeClusterAnchorsCountsNothingForAClusterItSkipped() {
            // A skipped cluster costs nothing, so it must not carry a share of the tuning's
            // product either - the counts are what the sweep spent, not what it was sized for.
            var fit = ClusterAnchorPlacement.computeClusterAnchors(
                SITELESS_PARTITION,
                buildSpec(0.0, 0.0, 3, 4, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()),
                Map.of());

            assertThat(fit.candidateCount()).isEqualTo(0);
            assertThat(fit.bandFitCount()).isEqualTo(0);
        }

        @Test
        void computeClusterAnchorsCountsManyBandFitsPerCandidate() {
            // Each candidate is sized by repeated band fits - a minimum-font probe, the
            // font-height search's steps, and the accepted span's read-back - so the band
            // fits outnumber the candidates. This is the level the fit's duration tracks,
            // and the reason the candidate count alone understates the cost.
            var fit = ClusterAnchorPlacement.computeClusterAnchors(
                FUSED_PAIR_PARTITION,
                buildSpec(0.0, 0.0, 3, 4, 0.0, 2.0),
                createLabelResolvers(buildSlenderNameEstimators()),
                Map.of());

            assertThat(fit.bandFitCount())
                .isGreaterThan(fit.candidateCount());
        }

        @Test
        void computeClusterAnchorsCapsTheBandGirthSoTheWholeBandStaysInsideTheBorder() {
            // A slab cluster 700 tall once inset (y 150..850). A fat name (aspect 1) wants
            // all the girth it can get, but the band cannot exceed the 700 the border
            // allows, so the fit caps the girth at the cluster rather than overrun it - and
            // the whole band, centreline give or take half its girth, stays within
            // y 150..850. The thin centreline of the line fit hid this; the band makes the
            // "too close to the border" case explicit and keeps it inside.
            var anchors = computeAnchors(
                FUSED_PAIR_PARTITION,
                buildBandSpec(
                    0.0,
                    0.0,
                    3,
                    3,
                    0.0,
                    2.0,
                    false,
                    false,
                    100.0,
                    2000.0,
                    1,
                    1.0),
                createLabelResolvers(buildAspectNameEstimators(1.0)));

            var anchor = anchors.get(0);

            assertThat(anchor.acceptedAxis())
                .isNotNull();
            assertThat(anchor.acceptedAxis().startY())
                .isCloseTo(500.0, within(1.0));

            // Girth capped just under the 700-tall cluster, never crossing the border.
            assertThat(anchor.thickness())
                .isGreaterThan(600f);
            assertThat(anchor.thickness())
                .isLessThanOrEqualTo(700f);

            assertThat(anchor.anchorY() - anchor.thickness() / 2f)
                .isGreaterThanOrEqualTo(149f);
            assertThat(anchor.anchorY() + anchor.thickness() / 2f)
                .isLessThanOrEqualTo(851f);
        }

        @Test
        void computeClusterAnchorsSizesTheBandToTheFontToleranceItsTuningCarries() {
            // The same girth-capped slab, fitted twice under tunings that differ in nothing
            // but how finely the font search runs. At the fine tolerance the fit resolves
            // the ~700 of girth the border allows; a tolerance of 1000 over the 100..2000
            // clamp buys a single halving, which fails at 1050 and leaves the search on the
            // 100-unit floor it started from - a label visibly under-filling its cluster,
            // which is what too coarse a knob costs.
            var fineSpec = buildBandSpec(
                0.0,
                0.0,
                3,
                3,
                0.0,
                2.0,
                false,
                false,
                100.0,
                2000.0,
                1,
                1.0);

            var coarseSpec = buildSpecWithFontTolerance(fineSpec, 1000.0);

            var fineAnchors = computeAnchors(
                FUSED_PAIR_PARTITION,
                fineSpec,
                createLabelResolvers(buildAspectNameEstimators(1.0)));

            var coarseAnchors = computeAnchors(
                FUSED_PAIR_PARTITION,
                coarseSpec,
                createLabelResolvers(buildAspectNameEstimators(1.0)));

            // The tolerance reaches the fitter only through this tuning, so a search that
            // dropped it on the way would size both runs alike.
            assertThat(fineAnchors.get(0).thickness())
                .isGreaterThan(600f);
            assertThat(coarseAnchors.get(0).thickness())
                .isCloseTo(100f, within(1f));
        }

        @Test
        void computeClusterAnchorsStacksASquareClusterNameIntoTwoLines() {
            // In a square cluster (inset 1700 on a side) a name six times as long as it is
            // tall cannot run big on one line - the side caps a single line's font. Stacking
            // it into two lines halves the length each line needs and spends the square's
            // spare girth, so the two-line box carries a strictly taller font and the fit
            // chooses it over one line and over three (which the cluster's girth cannot make
            // taller).
            var anchors = computeAnchors(
                SQUARE_GRID_CENTRED_PARTITION,
                buildBandSpec(
                    0.0,
                    0.0,
                    3,
                    3,
                    0.0,
                    2.0,
                    false,
                    false,
                    100.0,
                    1700.0,
                    3,
                    1.15),
                createLabelResolvers(buildAspectNameEstimators(6.0)));

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
            var nameEstimatorFake = new LabelLengthEstimatorFake(6.0, "Line", MAX_FILLABLE_LINES);
            var anchors = computeAnchors(
                SQUARE_GRID_CENTRED_PARTITION,
                buildBandSpec(
                    0.0,
                    0.0,
                    3,
                    3,
                    0.0,
                    2.0,
                    false,
                    false,
                    100.0,
                    1700.0,
                    3,
                    1.15),
                createLabelResolvers(owner -> nameEstimatorFake));

            var anchor = anchors.get(0);

            assertThat(anchor.nameLines())
                .containsExactly("Line 1", "Line 2");
            assertThat(anchor.fontHeight())
                .isCloseTo(anchor.thickness() / 2.15f, within(1e-2f));
        }

        @Test
        void computeClusterAnchorsKeepsANameOnOneLineWhenTheLineCapIsOne() {
            // The same square that would prefer two lines is held to one when the line cap
            // is one, so the name stays a single line at the smaller font the cap forces -
            // the knob that lets a caller forbid stacking.
            var anchors = computeAnchors(
                SQUARE_GRID_CENTRED_PARTITION,
                buildBandSpec(
                    0.0,
                    0.0,
                    3,
                    3,
                    0.0,
                    2.0,
                    false,
                    false,
                    100.0,
                    1700.0,
                    1,
                    1.15),
                createLabelResolvers(buildAspectNameEstimators(6.0)));

            assertThat(anchors.get(0).lineCount())
                .isEqualTo(1);
        }

        @Test
        void computeClusterAnchorsCollapsesToTheDotWhenTheMinimumBandCannotFit() {
            // A minimum font taller than the 1700 the square holds cannot sit anywhere
            // - no placement can prove even the thinnest required band interior - so the fit
            // collapses to the site-centroid dot, the same fallback a no-room line takes.
            var anchors = computeAnchors(
                SQUARE_GRID_CENTRED_PARTITION,
                buildBandSpec(
                    0.0,
                    0.0,
                    3,
                    3,
                    0.0,
                    2.0,
                    false,
                    false,
                    3000.0,
                    4000.0,
                    1,
                    1.0),
                createLabelResolvers(buildAspectNameEstimators(6.0)));

            var anchor = anchors.get(0);

            assertThat(anchor.acceptedAxis())
                .isNull();
            assertThat(anchor.thickness())
                .isEqualTo(0f);
            assertThat(anchor.lineCount())
                .isEqualTo(0);
            assertThat(anchor.nameLines())
                .isEmpty();
            assertThat(anchor.fontHeight())
                .isEqualTo(0f);
            assertThat(anchor.anchorX())
                .isCloseTo(1000f, within(1e-3f));
            assertThat(anchor.anchorY())
                .isCloseTo(1000f, within(1e-3f));
        }

        // A very slender name (length 500x its line height) so the box solver sizes a
        // band only a few world units thick before it runs out of length: the band then
        // hugs its centreline and the accepted line reproduces the pre-band line fit,
        // letting the line-fit tests above pin the same geometry they always did while
        // the band-fit tests exercise real girth. A single line with a wide font ceiling,
        // so the girth is capped by the cluster, never the knob, and never split.
        private static final double SLENDER_ASPECT = 500.0;
        private static final double NO_MIN_FONT_SIZE = 0.0;
        private static final double AMPLE_MAX_FONT_SIZE = 2000.0;
        private static final int ONE_LINE = 1;
        private static final double FLUSH_LINES = 1.0;

        // Enough words behind the named fake to fill every line count these fixtures allow, so
        // a case that is not about the unfillable answer never trips over it.
        private static final int MAX_FILLABLE_LINES = 3;

        // A font-height tolerance far below the world units these fixtures assert their
        // geometry in, so how finely the sizing searched is never what a failure is about.
        private static final double FINE_FONT_TOLERANCE = 0.01;

        // A per-key estimator resolver that hands every cluster the same aspect stand-in -
        // the name-estimator seam the line- and band-fit tests size against.
        private static Function<String, LabelLengthEstimator> buildAspectNameEstimators(double aspect) {
            return owner -> new AspectLabelLengthEstimator(aspect);
        }

        // The slender stand-in resolver behind the line-fit tests.
        private static Function<String, LabelLengthEstimator> buildSlenderNameEstimators() {
            return buildAspectNameEstimators(SLENDER_ASPECT);
        }

        // Binds the fixed shade every fit case shares, so a case names only the name
        // measurement it actually varies. The one case that records which key the shade is
        // asked for builds the value directly instead, since that half is its point.
        private static ClusterLabelResolvers createLabelResolvers(
                Function<String, LabelLengthEstimator> nameEstimators) {

            return new ClusterLabelResolvers(FIXED_LABEL_COLOURS, nameEstimators);
        }

        // A per-key resolver handing every cluster a one-line name under the given label, for
        // the cases that turn on the wrap rather than on the geometry: two of these differing
        // only in the label are two owners differing only in what they are called.
        private static Function<String, LabelLengthEstimator> buildNamedSingleLineEstimators(
                String lineLabel) {

            return owner -> new LabelLengthEstimatorFake(SLENDER_ASPECT, lineLabel, ONE_LINE);
        }

        // The tuning both passes of a reuse case run under - the same slender single-line band
        // the line-fit cases use, at one offset. A method rather than a constant because the
        // knobs it reads are declared below it, where a field initialiser would capture them
        // still unset.
        private static LabelAnchorSpecification createPairTuning() {
            return buildSpec(0.0, 0.0, 3, 1, 0.0, 2.0);
        }

        // The horizontal pair fitted from scratch - the standing placements a reuse case then
        // offers back. Named because every one of those cases runs the same fixture twice, and
        // spelling the geometry out on both passes buries the one thing each case varies.
        private static List<ClusterAnchor> fitTheHorizontalPair(
                ClusterLabelResolvers labelResolvers) {

            return computeAnchors(
                FUSED_PAIR_PARTITION,
                createPairTuning(),
                labelResolvers);
        }

        // The same pair swept again, offered what a previous pass left. The whole fit comes
        // back rather than the anchors alone, since what a reuse case reads first is the cost:
        // a carried placement is one the counts say was never searched.
        private static ClusterAnchorPlacement.ClusterAnchorFit refitTheHorizontalPairOffering(
                List<ClusterAnchor> standingAnchors,
                ClusterLabelResolvers labelResolvers) {

            return ClusterAnchorPlacement.computeClusterAnchors(
                FUSED_PAIR_PARTITION,
                createPairTuning(),
                labelResolvers,
                indexByIdentity(standingAnchors));
        }

        // A tuning with the fixture's border-trace pair baked in, sized for the slender
        // single-line band that reduces the fit to a line, and both diagnostic toggles
        // off, so each line-fit test names only the search knobs it exercises.
        private static LabelAnchorSpecification buildSpec(
                double endInsetDistance,
                double iconClearance,
                int directionCount,
                int offsetCount,
                double verticalPenaltyStrength,
                double verticalPenaltyExponent) {
            return buildSpec(
                endInsetDistance,
                iconClearance,
                directionCount,
                offsetCount,
                verticalPenaltyStrength,
                verticalPenaltyExponent,
                false,
                false);
        }

        // The full line-fit tuning, for the tests that also exercise the rejected- and
        // unbiased-axis diagnostics; still the slender single-line band.
        private static LabelAnchorSpecification buildSpec(
                double endInsetDistance,
                double iconClearance,
                int directionCount,
                int offsetCount,
                double verticalPenaltyStrength,
                double verticalPenaltyExponent,
                boolean showRejectedAxis,
                boolean showUnbiasedAxis) {
            return buildBandSpec(
                endInsetDistance,
                iconClearance,
                directionCount,
                offsetCount,
                verticalPenaltyStrength,
                verticalPenaltyExponent,
                showRejectedAxis,
                showUnbiasedAxis,
                NO_MIN_FONT_SIZE,
                AMPLE_MAX_FONT_SIZE,
                ONE_LINE,
                FLUSH_LINES);
        }

        // The full tuning with the name-fit knobs exposed, for the tests that exercise real
        // girth: the per-line font clamp and how many lines a name may wrap into. The max
        // slant is 0 throughout, so the label preference collapses to dead-horizontal and
        // these fixtures pin the pre-slant line and band geometry; the slant math is pinned
        // on its own in LabelSlantPreferenceTest.
        private static LabelAnchorSpecification buildBandSpec(
                double endInsetDistance,
                double iconClearance,
                int directionCount,
                int offsetCount,
                double verticalPenaltyStrength,
                double verticalPenaltyExponent,
                boolean showRejectedAxis,
                boolean showUnbiasedAxis,
                double nameMinFontSize,
                double nameMaxFontSize,
                int nameMaxLines,
                double nameLineSpacing) {
            return new LabelAnchorSpecification(
                new AnchorSearch(
                    new ClusterBorderTrace(WELD_TOLERANCE, MITER_LIMIT),
                    directionCount,
                    offsetCount),
                new LeanScoring(verticalPenaltyStrength, verticalPenaltyExponent, 0.0),
                new AnchorDiagnostics(showRejectedAxis, showUnbiasedAxis),
                new BandFitSpecification(
                    iconClearance,
                    endInsetDistance,
                    FINE_FONT_TOLERANCE),
                new NameFitSpecification(
                    nameMinFontSize,
                    nameMaxFontSize,
                    nameMaxLines,
                    nameLineSpacing));
        }

        // The same tuning at a different font-height tolerance, for the one test that reads
        // how finely the sizing searched. Copied from a built spec rather than threaded
        // through the builders above, so "everything else is identical" is structural
        // rather than a claim two argument lists have to keep agreeing on.
        private static LabelAnchorSpecification buildSpecWithFontTolerance(
                LabelAnchorSpecification spec,
                double fontHeightTolerance) {

            var bandFit = spec.bandFit();
            return new LabelAnchorSpecification(
                spec.search(),
                spec.scoring(),
                spec.diagnostics(),
                new BandFitSpecification(
                    bandFit.keepOutClearance(),
                    bandFit.endInsetDistance(),
                    fontHeightTolerance),
                spec.nameFit());
        }

        // One square cell's CCW edges (bottom, right, top, left), each tagged with the
        // neighbouring system across it or null for a frontier into empty space.
        private static List<CellEdge> listSquareCellEdges(
                double minX,
                double minY,
                String bottomNeighbour,
                String rightNeighbour,
                String topNeighbour,
                String leftNeighbour) {
            return listRectangleCellEdges(
                minX,
                minY,
                CELL_SIDE,
                CELL_SIDE,
                bottomNeighbour,
                rightNeighbour,
                topNeighbour,
                leftNeighbour);
        }

        // One axis-aligned rectangular cell's CCW edges (bottom, right, top, left) - the
        // general fixture behind squareCellEdges, and the shape whose long side gives a
        // single-system cluster's direction fallback something to fit.
        private static List<CellEdge> listRectangleCellEdges(
                double minX,
                double minY,
                double width,
                double height,
                String bottomNeighbour,
                String rightNeighbour,
                String topNeighbour,
                String leftNeighbour) {

            var maxX = minX + width;
            var maxY = minY + height;
            return List.of(
                buildEdge(minX, minY, maxX, minY, bottomNeighbour),
                buildEdge(maxX, minY, maxX, maxY, rightNeighbour),
                buildEdge(maxX, maxY, minX, maxY, topNeighbour),
                buildEdge(minX, maxY, minX, minY, leftNeighbour));
        }

        // One cell edge facing the given neighbour system, or the reach bound when it is null.
        private static CellEdge buildEdge(
                double x1,
                double y1,
                double x2,
                double y2,
                String neighbour) {
            return new CellEdge(
                x1,
                y1,
                x2,
                y2,
                neighbour == null
                    ? EdgeTarget.REACH_BOUND
                    : new EdgeTarget.AcrossSystem(buildCellKey(neighbour)));
        }
    }

    @Nested
    class CountCandidateDirections {

        @Test
        void countCandidateDirectionsAddsTheFansTwoFixedExtrasToTheConfiguredWidth() {
            // The configured width reads as the swept count and is not it: every fan also
            // carries the cluster's own principal axis and the preferred slant. Resolving
            // the two apart is what lets a report show them as swept-over-configured
            // instead of quietly printing the tuning as though it were the work.
            assertThat(ClusterAnchorPlacement.countCandidateDirections(8))
                .isEqualTo(10);
        }

        @Test
        void countCandidateDirectionsStillCountsTheExtrasForAFanOfNoWidth() {
            // A zero-width fan is not a zero-candidate sweep - the two extras are added
            // unconditionally, so the axis and the slant are still swept.
            assertThat(ClusterAnchorPlacement.countCandidateDirections(0))
                .isEqualTo(2);
        }
    }

    /**
     * A name estimator with the aspect stand-in's arithmetic but real lines behind it,
     * so a test can pin that the anchor carries exactly the wrap the fit sized: line count
     * {@code n} wraps to {@code "<lineLabel> 1".."<lineLabel> n"}.
     *
     * <p>The label is a parameter because the wrap is also what a carried placement is
     * re-checked against, so a case needs two of these that differ in nothing but the name
     * they resolve to - the aspect being shared is what makes the name the only variable.
     *
     * <p>{@code maxFillableLineCount} reproduces the real measurement's answer for a name with
     * fewer words than the lines asked for: an infinite required length and an empty wrap,
     * which is the one shape a stand-in's empty wrap can be mistaken for.
     */
    private record LabelLengthEstimatorFake(
        double aspect,
        String lineLabel,
        int maxFillableLineCount) implements LabelLengthEstimator {

        @Override
        public double requiredLengthFor(double lineHeight, int lineCount) {
            return lineCount > maxFillableLineCount
                ? Double.POSITIVE_INFINITY
                : aspect * lineHeight / lineCount;
        }

        @Override
        public List<String> wrapIntoLines(int lineCount) {
            if (lineCount > maxFillableLineCount) {
                return List.of();
            }
            var lines = new ArrayList<String>(lineCount);
            for (var lineNumber = 1; lineNumber <= lineCount; lineNumber++) {
                lines.add(lineLabel + " " + lineNumber);
            }
            return lines;
        }
    }
}
