package kmu.maplayers.base.labels.anchor;

import kmlib.math.geometry.Segment;
import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.label.AspectLabelLengthEstimator;
import kmlib.starsector.ui.label.BandFitSpecification;
import kmlib.starsector.ui.label.NameFitSpecification;
import kmlib.testfixtures.profiling.RecordedCapture;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.anchor.specifications.AnchorDiagnostics;
import kmu.maplayers.base.labels.anchor.specifications.AnchorSearch;
import kmu.maplayers.base.labels.anchor.specifications.LabelAnchorSpecification;
import kmu.maplayers.base.labels.anchor.specifications.LeanScoring;
import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.base.render.clusters.ClusterBorderTrace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellEdgeFixture.buildEdgeFacing;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the cluster-name fit drive's jobs, which are the ones nothing downstream can recover: it
 * decides whether any name is fitted at all, it carries over the placements a change did not
 * disturb, and it labels and reports every pass it leaves behind.
 *
 * <p>The gate is the rule worth guarding hardest, because both of its halves are silent when
 * wrong. The placements feed the drawn names and the debug anchor overlay, so fitting whenever
 * either is switched on is what keeps them one shared computation - and skipping the fit when
 * neither is on is what keeps a rebuild off the sector's whole cluster geometry while nobody is
 * looking. The gate empties the standing list itself, so switching the names off drops the
 * placements already fitted rather than leaving the last pass's overlay frozen on the map.
 *
 * <p>What each path leaves labelling its list is pinned alongside, on every path rather than only
 * the fitting one: the drive is the only place the live tuning is read, so a list left behind
 * without the rules it was made under can never be labelled afterwards.
 *
 * <p>Every case is driven by plain keys and a fixture resolver pair, with no owner in sight: the
 * drive is the same for any layer that cuts the sector into clusters, and what a key means is the
 * layer's. What the search does with the pair is pinned by {@link ClusterAnchorPlacementTests}; here
 * the real search and the real cluster walk run under the drive, which is what makes this an
 * integration suite, and their work is only counted or read back off the placement. Cells are hand-built
 * 2000-unit squares, comfortably clear of the fixed border channel, so which cells fuse into one
 * cluster is plain to read. The tuning read is stubbed at its seam, since it answers nothing
 * outside the game.
 */
final class ClusterAnchorsBuilderIntegrationTests {

    // Three keys a layer might cut the sector by, meaning nothing here beyond which cells share one.
    private static final String NORTH = "north";
    private static final String SOUTH = "south";
    private static final String WEST = "west";

    // The row a fit lands on.
    private static final String FIT_ANCHORS_SECTION = "mapLayer.fitClusterAnchors";

    // Two cells of one key meeting along x = 2000, so the pair proves the drive fits them one shared
    // name; the lone cell shares no edge with anything, so it stays its own cluster.
    private static final String PAIRED_SYSTEM = "paired";
    private static final String PAIR_NEIGHBOUR_SYSTEM = "pairNeighbour";
    private static final String LONE_SYSTEM = "lone";

    // The revision the fixture cells stand at. Opaque to the drive - it only travels onto the
    // fingerprint - so one value serves every case, and a case that varies it says so.
    private static final int GEOMETRY_REVISION = 7;

    // Each key's shade, kept distinct so a name drawn in the wrong key's colour names which lookup
    // went astray.
    private static final Map<String, Color> COLOUR_BY_KEY = Map.of(
        NORTH, Color.RED,
        SOUTH, Color.CYAN,
        WEST, Color.ORANGE);

    // The stand-in name shape every key measures as: a plausible name proportion, so the fit sizes a
    // realistic box without a font, which nothing outside the game loads.
    private static final double NAME_ASPECT = 6.0;

    // The border trace the fitted cluster rings are welded and mitred with, loose enough to chain
    // the hand-built corners and at the miter limit the shipped border uses.
    private static final double WELD_TOLERANCE = 1.0;
    private static final double MITER_LIMIT = 4.0;

    // The search tuning every case runs under: no end inset or icon keep-out, a small direction fan
    // and a single centre offset (the fixtures are wide rectangles, so the winner is horizontal
    // whatever the fan's resolution), no slope penalty, no diagnostics, and a single line free to
    // grow to any font, sized to a tolerance finer than anything these cases read back. Sized so a
    // name fits every fixture cluster - a case that comes back empty did so because of the gate,
    // not because the geometry ran out of room.
    private static final double FINE_FONT_TOLERANCE = 0.01;
    private static final LabelAnchorSpecification ANCHOR_SPECIFICATION =
        new LabelAnchorSpecification(
            new AnchorSearch(
                new ClusterBorderTrace(WELD_TOLERANCE, MITER_LIMIT),
                3,
                1),
            new LeanScoring(0.0, 2.0, 0.0),
            new AnchorDiagnostics(false, false),
            new BandFitSpecification(0.0, 0.0, FINE_FONT_TOLERANCE),
            new NameFitSpecification(0.0, 2000.0, 1, 1.0));

    // The face the layer says the sector settled its labels on: a settled one rather than the face
    // labels ask for, so a fit recording the asked-for face cannot pass.
    private static final StarsectorFont SETTLED_LABEL_FACE = StarsectorFont.VANILLA_INSIGNIA_25;

    // What every path here has to report having produced its list under: the stubbed tuning, the
    // revision it was handed and the face the layer settled. Named once because the point is that
    // all paths answer the same thing.
    private static final AnchorFitFingerprint FITTED_UNDER =
        new AnchorFitFingerprint(ANCHOR_SPECIFICATION, GEOMETRY_REVISION, SETTLED_LABEL_FACE);

    // A rebuild standing at a different geometry revision, so the placements a pass left behind
    // were made under rules that no longer hold. One int apart from FITTED_UNDER, since the point is
    // that any difference at all drops the whole carry-over.
    private static final int MOVED_GEOMETRY_REVISION = GEOMETRY_REVISION + 1;

    // What a caller's pair says while the placements standing in it were fitted under rules this
    // rebuild no longer runs under. Seeded by the cases that ask what a rebuild leaves behind, so
    // what they read back is this pass's answer rather than a reading already there.
    private static final AnchorFitFingerprint FITTED_UNDER_MOVED_RULES =
        new AnchorFitFingerprint(ANCHOR_SPECIFICATION, MOVED_GEOMETRY_REVISION, SETTLED_LABEL_FACE);

    private static final Map<SystemKey, List<CellEdge>> EDGES = buildKeyedValues(listOrderedEdges());
    private static final Map<SystemKey, double[]> SITES = buildKeyedValues(Map.of(
        PAIRED_SYSTEM, new double[] {1000, 1000},
        PAIR_NEIGHBOUR_SYSTEM, new double[] {3000, 1000},
        LONE_SYSTEM, new double[] {21000, 1000}));

    // The pair cut by one key and the lone cell by another, which most cases fit over.
    private static final Map<String, String> PAIR_AND_LONE_KEYS = Map.of(
        PAIRED_SYSTEM, NORTH,
        PAIR_NEIGHBOUR_SYSTEM, NORTH,
        LONE_SYSTEM, SOUTH);

    private final CellGeometryCache geometryCacheMock = mock(CellGeometryCache.class);

    // The cells every case hands over, carrying the revision they stand at.
    private final RevisedCellGeometry cellGeometry =
        new RevisedCellGeometry(geometryCacheMock, GEOMETRY_REVISION);

    // The pair the caller owns across rebuilds, standing at its never-fitted reading: no placements
    // and no rules recorded for them, which is what a session's first rebuild starts from.
    private final StandingClusterAnchors standingAnchors = new StandingClusterAnchors();

    // How many times a fit asked for its resolver pair, so the gate's saving can be read.
    private int resolvedPairCount;

    private MockedStatic<LabelAnchorSpecification> specificationMock;

    @BeforeEach
    void openTheTuningSeam() {

        specificationMock = mockStatic(LabelAnchorSpecification.class);

        // The live tuning arrives as the data the drive reads it into, so a case states its gate
        // and nothing about the dozens of knobs behind it.
        specificationMock
            .when(LabelAnchorSpecification::readFromLunaSettings)
            .thenReturn(ANCHOR_SPECIFICATION);

        when(geometryCacheMock.getCellEdgesByCellKey())
            .thenReturn(EDGES);
        when(geometryCacheMock.getSiteBySystemKey())
            .thenReturn(SITES);
        when(geometryCacheMock.getSystemKeyByCellKey())
            .thenReturn(listIdentityCellsFor(EDGES.keySet()));
    }

    @AfterEach
    void closeTheTuningSeam() {
        specificationMock.close();
    }

    @Nested
    class RebuildClusterAnchors {

        @Test
        void fitsOneNamePerContiguousCluster() {

            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                buildInputsDrawingNames(PAIR_AND_LONE_KEYS));

            // Two names, not three: the two touching cells of one key are named once between them,
            // which is what makes a name read as naming a cluster rather than a system.
            assertThat(standingAnchors.getAnchors())
                .hasSize(2);
        }

        @Test
        void countsThePlacementsItFitted() {
            // The number the fit's duration is read against - it is the rebuild's largest cost, and
            // what it cost per placement is the reading a sweep is tuned by.
            var expectedLabelCount = 2L;
            var fitRow = RecordedCapture
                .recordWhile(() -> ClusterAnchorsBuilder.rebuildClusterAnchors(
                    standingAnchors,
                    cellGeometry,
                    buildInputsDrawingNames(PAIR_AND_LONE_KEYS)))
                .findNode(FIT_ANCHORS_SECTION);

            var actualLabelCount = fitRow.findCount(MapBuildCounters.LABELS).getTotals().getTotal();

            assertThat(actualLabelCount)
                .isEqualTo(expectedLabelCount);
        }

        @Test
        void tagsTheCallWithEveryMultiplicandOfItsCost() {
            // A slow fit is read off which of its inputs grew, so every one of them rides on the
            // call: two clusters, the three swept directions plus the two the fan always adds, one
            // offset, the ten candidates that fan crosses over both clusters, the band fits those
            // candidates actually spent (twenty-one apiece at this tolerance), and the three sites
            // every box keeps clear of.
            var expectedTag = "clusters=2 directions=5/3 offsets=1 candidates=10 bandFits=210 keepOuts=3";
            var actualTag = RecordedCapture
                .recordWhile(() -> ClusterAnchorsBuilder.rebuildClusterAnchors(
                    standingAnchors,
                    cellGeometry,
                    buildInputsDrawingNames(PAIR_AND_LONE_KEYS)))
                .findNode(FIT_ANCHORS_SECTION)
                .getWorstCall()
                .getTag();

            assertThat(actualTag)
                .isEqualTo(expectedTag);
        }

        @Test
        void opensNoFitRowWhenNamesAndTheOverlayAreBothOff() {
            // The row times the sweep, and a gated-off pass runs none: a row opened anyway would
            // read as a fit that cost nothing, hiding that no fit happened.
            var actualHasFitRow = RecordedCapture
                .recordWhile(() -> ClusterAnchorsBuilder.rebuildClusterAnchors(
                    standingAnchors,
                    cellGeometry,
                    buildInputsFittingNothing(Map.of(PAIRED_SYSTEM, NORTH))))
                .hasNode(FIT_ANCHORS_SECTION);

            assertThat(actualHasFitRow)
                .isFalse();
        }

        @Test
        void drawsEachClusterInTheShadeItsKeyResolvesTo() {

            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                buildInputsDrawingNames(PAIR_AND_LONE_KEYS));

            // The clusters come back in first-seen cell order, so the fused pair leads and the lone
            // cell follows - each carrying the shade the layer's resolver gave its own key.
            assertThat(standingAnchors.getAnchors().get(0).colour())
                .isEqualTo(Color.RED);
            assertThat(standingAnchors.getAnchors().get(1).colour())
                .isEqualTo(Color.CYAN);
        }

        @Test
        void fitsNamesForTheOverlayWhenNamesAreOff() {
            // The debug anchor overlay draws the same placements the names hang off, so it has to
            // be able to hold the search open on its own - otherwise the diagnostic shows nothing
            // exactly when the player turned the names off to look at it.
            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                buildInputs(Map.of(PAIRED_SYSTEM, NORTH), false, true));

            assertThat(standingAnchors.getAnchors())
                .hasSize(1);
        }

        @Test
        void fitsNothingWhenNamesAndTheOverlayAreBothOff() {
            // Neither consumer is looking, so the sector's whole cluster search is skipped rather
            // than run for placements nothing will draw.
            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                buildInputsFittingNothing(Map.of(PAIRED_SYSTEM, NORTH)));

            assertThat(standingAnchors.getAnchors())
                .isEmpty();
        }

        @Test
        void resolvesNoNamesWhenNamesAndTheOverlayAreBothOff() {
            // Resolving the pair loads the label font and opens a memo per key, so a pass that fits
            // nothing must not ask for it at all.
            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                buildInputsFittingNothing(Map.of(PAIRED_SYSTEM, NORTH)));

            assertThat(resolvedPairCount)
                .isEqualTo(0);
        }

        @Test
        void dropsStandingNamesEvenWhenItFitsNone() {
            // The list is the overlay's own, so the gate has to empty it rather than leave it
            // alone: switching the names off has to take the fitted names off the map, not freeze
            // the last pass's on it.
            standingAnchors.replaceAnchors(List.of(buildStaleAnchor()), FITTED_UNDER_MOVED_RULES);

            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                buildInputsFittingNothing(Map.of(PAIRED_SYSTEM, NORTH)));

            assertThat(standingAnchors.getAnchors())
                .isEmpty();
        }

        @Test
        void recordsTheTuningGeometryAndFaceItFittedUnder() {
            // The placements it leaves behind are only reusable if something states the rules they
            // were sized under, and only this rebuild read them.
            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                buildInputsDrawingNames(Map.of(PAIRED_SYSTEM, NORTH)));

            assertThat(standingAnchors.getFitFingerprint())
                .isEqualTo(FITTED_UNDER);
        }

        @Test
        void carriesStandingPlacementsItFittedUnderTheSameRules() {
            // Run twice over an unchanged sector, with its own previous answer standing in the
            // pair, the drive must hand the placements themselves on rather than searching the same
            // clusters again - the accepted line being the very object the first pass produced is
            // what says so.
            var fitInputs = buildInputsDrawingNames(PAIR_AND_LONE_KEYS);

            ClusterAnchorsBuilder.rebuildClusterAnchors(standingAnchors, cellGeometry, fitInputs);

            var firstPassAxis = standingAnchors.getAnchors().get(0).acceptedAxis();

            // Guarded, because two collapsed passes would both carry a null line and the identity
            // assertion below would hold without anything having been carried.
            assertThat(firstPassAxis)
                .isNotNull();

            ClusterAnchorsBuilder.rebuildClusterAnchors(standingAnchors, cellGeometry, fitInputs);

            assertThat(standingAnchors.getAnchors().get(0).acceptedAxis())
                .isSameAs(firstPassAxis);
        }

        @Test
        void carriesOverANameItsNeighboursChangeLeftAlone() {
            // A change that re-keys one cluster leaves every other cluster naming the same members,
            // so their names stand: only the changed cluster is searched again, and the report says
            // something moved.
            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                buildInputsDrawingNames(PAIR_AND_LONE_KEYS));

            var pairAxis = standingAnchors.getAnchors().get(0).acceptedAxis();
            var loneAxis = standingAnchors.getAnchors().get(1).acceptedAxis();

            // Guarded, for the reason the case above guards its line.
            assertThat(pairAxis)
                .isNotNull();

            var disturbance = ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                buildInputsDrawingNames(Map.of(
                    PAIRED_SYSTEM, NORTH,
                    PAIR_NEIGHBOUR_SYSTEM, NORTH,
                    LONE_SYSTEM, WEST)));

            assertThat(standingAnchors.getAnchors().get(0).acceptedAxis())
                .isSameAs(pairAxis);
            assertThat(standingAnchors.getAnchors().get(1).acceptedAxis())
                .isNotSameAs(loneAxis);
            assertThat(standingAnchors.getAnchors().get(1).colour())
                .isEqualTo(Color.ORANGE);
            assertThat(disturbance.isDisturbingNothing())
                .isFalse();
        }

        @Test
        void refitsEveryClusterWhenTheRulesItFittedUnderMoved() {
            // The keep-out sites every box is trimmed clear of are the whole sector's, so a recut of
            // the cells moves fits no membership change would touch - and no cluster's identity can
            // see that. The fingerprint catches it and drops the whole carry-over.
            var fitInputs = buildInputsDrawingNames(PAIR_AND_LONE_KEYS);

            ClusterAnchorsBuilder.rebuildClusterAnchors(standingAnchors, cellGeometry, fitInputs);

            var firstPassAxis = standingAnchors.getAnchors().get(0).acceptedAxis();

            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                new RevisedCellGeometry(geometryCacheMock, MOVED_GEOMETRY_REVISION),
                fitInputs);

            assertThat(standingAnchors.getAnchors().get(0).acceptedAxis())
                .isNotSameAs(firstPassAxis);
        }

        @Test
        void labelsTheListTheSkippedFitEmpties() {
            // The gate empties the list rather than leaving it alone, so the list it leaves needs
            // labelling exactly as a fitted one does: an unlabelled list is indistinguishable from
            // one made under rules that still hold.
            standingAnchors.replaceAnchors(List.of(buildStaleAnchor()), FITTED_UNDER_MOVED_RULES);

            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                buildInputsFittingNothing(Map.of(PAIRED_SYSTEM, NORTH)));

            assertThat(standingAnchors.getAnchors())
                .isEmpty();
            assertThat(standingAnchors.getFitFingerprint())
                .isEqualTo(FITTED_UNDER);
        }

        @Test
        void reportsNothingDisturbedWhenEveryPlacementCarriedOver() {
            // The rebuild that changes nothing, which is most of them: every cluster still names the
            // same members and keeps the placement it had, so anything laid around those names may
            // stand.
            var fitInputs = buildInputsDrawingNames(PAIR_AND_LONE_KEYS);

            ClusterAnchorsBuilder.rebuildClusterAnchors(standingAnchors, cellGeometry, fitInputs);

            // Guarded, because a pass that fitted nothing would leave two empty lists to compare and
            // report nothing disturbed without having carried anything over.
            assertThat(standingAnchors.getAnchors())
                .isNotEmpty();

            assertThat(ClusterAnchorsBuilder
                    .rebuildClusterAnchors(standingAnchors, cellGeometry, fitInputs)
                    .isDisturbingNothing())
                .isTrue();
        }

        @Test
        void reportsTheRoomGivenUpByTheFitItSkipped() {
            // Switching the names off empties the standing list, which is as much a change to what
            // a map has room for as a re-fit is: the ring those words covered is free now, and
            // whatever kept clear of them has to be told.
            standingAnchors.replaceAnchors(
                List.of(buildStandingAnchorOccupyingRoom()),
                FITTED_UNDER_MOVED_RULES);

            assertThat(ClusterAnchorsBuilder
                    .rebuildClusterAnchors(
                        standingAnchors,
                        cellGeometry,
                        buildInputsFittingNothing(Map.of(PAIRED_SYSTEM, NORTH)))
                    .isDisturbingNothing())
                .isFalse();
        }
    }

    @Nested
    class RebuildDiagnosticClusterAnchors {

        @Test
        void fitsNamesToTheHandedKeys() {
            // A debug view builds no draw lists, so this path is handed the keys that view traced,
            // and the names key off the same answer its borders were drawn from.
            ClusterAnchorsBuilder.rebuildDiagnosticClusterAnchors(
                standingAnchors,
                cellGeometry,
                buildInputs(PAIR_AND_LONE_KEYS, true, true));

            assertThat(standingAnchors.getAnchors())
                .hasSize(2);
            assertThat(standingAnchors.getAnchors().get(0).colour())
                .isEqualTo(Color.RED);
        }

        @Test
        void fitsNamesThoughTheNamesAreNotDrawn() {
            // This path exists for the overlay, so whether the names draw has no say over it: the
            // diagnostic still draws its dots for a player reading the map by colour alone.
            ClusterAnchorsBuilder.rebuildDiagnosticClusterAnchors(
                standingAnchors,
                cellGeometry,
                buildInputs(Map.of(PAIRED_SYSTEM, NORTH), false, true));

            assertThat(standingAnchors.getAnchors())
                .hasSize(1);
        }

        @Test
        void recordsWhatItFittedUnder() {
            // This path delegates the fit, so it must hand its own caller's pair down rather than
            // fitting into something of its own.
            ClusterAnchorsBuilder.rebuildDiagnosticClusterAnchors(
                standingAnchors,
                cellGeometry,
                buildInputs(Map.of(PAIRED_SYSTEM, NORTH), false, true));

            assertThat(standingAnchors.getFitFingerprint())
                .isEqualTo(FITTED_UNDER);
        }

        @Test
        void carriesStandingPlacementsThroughToTheSharedFit() {
            // Dropping the standing half on the way down would leave the diagnostic view
            // re-searching every cluster on every rebuild while the production view reuses - a
            // difference nothing but the frame time would show.
            var fitInputs = buildInputs(PAIR_AND_LONE_KEYS, false, true);

            ClusterAnchorsBuilder.rebuildDiagnosticClusterAnchors(standingAnchors, cellGeometry, fitInputs);

            var firstPassAxis = standingAnchors.getAnchors().get(0).acceptedAxis();

            // Guarded, because two collapsed passes would both carry a null line and the assertion
            // below would hold without anything having been carried.
            assertThat(firstPassAxis)
                .isNotNull();

            ClusterAnchorsBuilder.rebuildDiagnosticClusterAnchors(standingAnchors, cellGeometry, fitInputs);

            assertThat(standingAnchors.getAnchors().get(0).acceptedAxis())
                .isSameAs(firstPassAxis);
        }

        @Test
        void skipsTheFitWhileTheOverlayIsOff() {
            // The fit is the cost here, so the overlay's toggle gates it and not just the drawing -
            // even with the names on. The standing names still go, as on every other path, and the
            // list is still labelled.
            standingAnchors.replaceAnchors(List.of(buildStaleAnchor()), FITTED_UNDER_MOVED_RULES);

            ClusterAnchorsBuilder.rebuildDiagnosticClusterAnchors(
                standingAnchors,
                cellGeometry,
                buildInputsDrawingNames(Map.of(PAIRED_SYSTEM, NORTH)));

            assertThat(standingAnchors.getAnchors())
                .isEmpty();
            assertThat(standingAnchors.getFitFingerprint())
                .isEqualTo(FITTED_UNDER);
            assertThat(resolvedPairCount)
                .isEqualTo(0);
        }
    }

    // The layer's half of a fit with the names drawn and the overlay off, the reading most cases
    // are about.
    private ClusterAnchorFitInputs buildInputsDrawingNames(Map<String, String> keyBySystemId) {
        return buildInputs(keyBySystemId, true, false);
    }

    // The layer's half of a fit with neither consumer asking for one.
    private ClusterAnchorFitInputs buildInputsFittingNothing(Map<String, String> keyBySystemId) {
        return buildInputs(keyBySystemId, false, false);
    }

    // The layer's half of a fit over the given keys, with a resolver pair shading each key by its
    // own colour and measuring every name at one plausible proportion. The pair counts how often it
    // is asked for, so the gate's saving can be read.
    private ClusterAnchorFitInputs buildInputs(
            Map<String, String> keyBySystemId,
            boolean areNamesDrawn,
            boolean isAnchorOverlayShown) {

        return new ClusterAnchorFitInputs(
            buildKeyedValues(keyBySystemId),
            SETTLED_LABEL_FACE,
            areNamesDrawn,
            isAnchorOverlayShown,
            () -> {
                resolvedPairCount++;
                return new ClusterLabelResolvers(
                    COLOUR_BY_KEY::get,
                    key -> new AspectLabelLengthEstimator(NAME_ASPECT));
            });
    }

    // A placement left over from an earlier pass, for the cases that ask whether the standing list
    // is cleared. Only its presence is read, so every field is inert.
    private static ClusterAnchor buildStaleAnchor() {

        return new ClusterAnchor(
            new ClusterIdentity("stale", Set.of(buildCellKey("stale"))),
            0f,
            0f,
            Color.WHITE,
            List.of(),
            0f,
            null,
            null,
            null,
            0f,
            0);
    }

    // A placement left over from an earlier pass that does occupy room on the map, for the cases
    // asking what a rebuild reports having disturbed - the stale placement above accepted no line,
    // so it takes up nothing and its going disturbs nobody.
    private static ClusterAnchor buildStandingAnchorOccupyingRoom() {

        return new ClusterAnchor(
            new ClusterIdentity("stale", Set.of(buildCellKey("stale"))),
            0f,
            0f,
            Color.WHITE,
            List.of(),
            0f,
            new Segment(0, 1000, 2000, 1000),
            null,
            null,
            200f,
            1);
    }

    // The three fixture cells in first-seen order, so which cluster a case reads back at index 0 is
    // fixed by the map rather than by a hash.
    private static Map<String, List<CellEdge>> listOrderedEdges() {

        var edgesByCellId = new LinkedHashMap<String, List<CellEdge>>();

        edgesByCellId.put(PAIRED_SYSTEM, List.of(
            buildEdgeFacing(0, 0, 2000, 0, null),
            buildEdgeFacing(2000, 0, 2000, 2000, PAIR_NEIGHBOUR_SYSTEM),
            buildEdgeFacing(2000, 2000, 0, 2000, null),
            buildEdgeFacing(0, 2000, 0, 0, null)));

        edgesByCellId.put(PAIR_NEIGHBOUR_SYSTEM, List.of(
            buildEdgeFacing(2000, 0, 4000, 0, null),
            buildEdgeFacing(4000, 0, 4000, 2000, null),
            buildEdgeFacing(4000, 2000, 2000, 2000, null),
            buildEdgeFacing(2000, 2000, 2000, 0, PAIRED_SYSTEM)));

        edgesByCellId.put(LONE_SYSTEM, List.of(
            buildEdgeFacing(20000, 0, 22000, 0, null),
            buildEdgeFacing(22000, 0, 22000, 2000, null),
            buildEdgeFacing(22000, 2000, 20000, 2000, null),
            buildEdgeFacing(20000, 2000, 20000, 0, null)));

        return edgesByCellId;
    }

    // Every cell drawing as its own star - the partition the cluster walk runs over when no system
    // holds cells beyond its own.
    private static Map<SystemKey, SystemKey> listIdentityCellsFor(Iterable<SystemKey> cellKeys) {

        var systemKeyByCellKey = new LinkedHashMap<SystemKey, SystemKey>();

        for (var cellKey : cellKeys) {
            systemKeyByCellKey.put(cellKey, cellKey);
        }

        return systemKeyByCellKey;
    }
}
