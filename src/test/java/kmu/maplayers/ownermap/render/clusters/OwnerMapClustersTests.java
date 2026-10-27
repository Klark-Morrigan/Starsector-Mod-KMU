package kmu.maplayers.ownermap.render.clusters;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.render.clusters.ClusterDrawLists;
import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.base.render.clusters.PaintedCell;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.style.MapStyling;
import kmu.maplayers.ownermap.render.style.OwnerCategoriesFake;
import kmu.maplayers.ownermap.render.style.OwnerPaletteSlot;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellEdgeFixture.buildEdgeTo;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildDrawnSystemKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the built map state the renderer paints and the incremental refresh edits:
 * that the empty fallback is a harmless no-op the render path can lean on after a failed
 * first build, that {@link OwnerMapClusters#isEmpty} tracks either draw list, that an owner
 * baking no group is dropped rather than left painting what it lost, and that the two snapshots
 * handed in - the live occupancy and the inputs the build was baked under - are the two handed
 * back, with the global tier the emission reads coming off the retained theme.
 *
 * <p>Also pins the invariant the cursor read leans on that this model owns: that the cluster index
 * tracks holding through the splits and merges a single flip can cause, a break in which is
 * invisible until a hover resolves the wrong cluster group. What one cell carries, and what is dropped
 * when its ring is replaced, is {@link PaintedCellStoreTests}'s; what the retained inputs answer
 * for themselves is {@link OwnerMapBuildInputsTests}'s.
 */
final class OwnerMapClustersTests {

    // The cell the draw lists are exercised over, keyed as the cut keys a cell.
    private static final SystemKey SYSTEM_CELL = buildCellKey("system");

    // A second cell, for the case that needs one of each form.
    private static final SystemKey LONE_CELL = buildCellKey("lone");

    @Nested
    class CreateEmpty {

        // Both draw lists empty, so the render is a no-op and isEmpty short-circuits the GL state
        // push; the retained inputs are neutral placeholders the next frame's real build replaces
        // before any incremental pass reads them.
        @Test
        void yieldsAnEmptyNoOpFallback() {

            // ARRANGE
            // A stand-in view so this model test names no concrete view: the clusters only carry the
            // view for the incremental re-shape to read back, so any OwnerPaintedView serves.
            var viewMock = mock(OwnerPaintedView.class);

            // ACT
            var actualClusters = OwnerMapClusters.createEmpty(viewMock);

            // ASSERT
            assertThat(actualClusters.isEmpty())
                .isTrue();

            assertThat(actualClusters.getStyledCellByCellKey())
                .isEmpty();
            assertThat(actualClusters.getStyledClusterGroupByOwnerId())
                .isEmpty();
            assertThat(actualClusters.getOccupancy().getOwnerBySystemKey())
                .isEmpty();
            assertThat(actualClusters.getOccupancy().getInhabitedSystemKeys())
                .isEmpty();
            assertThat(actualClusters.getOccupancy().getSpotlitPresenceSystemKeys())
                .isEmpty();

            // The placeholder inputs' own stand-ins are OwnerMapBuildInputs' to pin; read back here
            // only to prove the fallback carries the view it was built for, which is the one thing
            // about them a later frame reads.
            assertThat(actualClusters.getBuildInputs().getViewReading().view())
                .isSameAs(viewMock);
        }
    }

    @Nested
    class IsEmpty {

        @Test
        void isTrueWhenBothDrawListsAreEmpty() {

            // ARRANGE
            var clusters = buildDrawablesWith(Map.of(), Map.of());

            // ACT
            var actualIsEmpty = clusters.isEmpty();

            // ASSERT
            assertThat(actualIsEmpty)
                .isTrue();
        }

        @Test
        void isFalseWhenAStyledCellIsPresent() {

            // ARRANGE
            var clusters = buildDrawablesWith(Map.of(SYSTEM_CELL, buildAnyStyledCell()), Map.of());

            // ACT
            var actualIsEmpty = clusters.isEmpty();

            // ASSERT
            assertThat(actualIsEmpty)
                .isFalse();
        }

        @Test
        void isFalseWhenAClusterGroupIsPresent() {

            // ARRANGE
            var clusters = buildDrawablesWith(Map.of(), Map.of("owner", buildAnyStyledClusterGroup()));

            // ACT
            var actualIsEmpty = clusters.isEmpty();

            // ASSERT
            assertThat(actualIsEmpty)
                .isFalse();
        }
    }

    @Nested
    class SatisfiesClusterDrawLists {

        // Read through the seam rather than off the class, which is what the framework emission sees.
        // Satisfying the interface with anything other than the build's own cells - a copy, or a
        // second set left empty - would paint a frame that no assertion over the layer's accessors
        // could tell apart from a correct one.
        @Test
        void withTheDrawListsAndTierTheBuildItselfHolds() {

            // ARRANGE
            var fusedCell = new StyledCell.FusedCell(new float[0], null, 0f);
            var loneCell = new StyledCell.LoneCell(new float[0], new float[0], null, null, 0f);
            var styledClusterGroup = buildAnyStyledClusterGroup();

            // A real theme rather than the shared fixture's inert one, since the global tier is one
            // of the four reads and the seam has to hand over the build's own.
            var renderStyle = OwnerMapClusterFixtures
                .createRenderStyleForEveryCategory(buildStyleMarked(1));
            var expectedGlobalStyle = renderStyle.global();

            var clusters = new OwnerMapClusters(
                SystemOccupancy.createEmpty(),
                new OwnerMapBuildInputs(
                    new MapStyling(
                        renderStyle,
                        OwnerMapClusterFixtures.BORDER_TRACE,
                        OwnerCategoriesFake.SHARED_FAKE,
                        OwnerMapClusterFixtures.NEUTRAL_PALETTE,
                        OwnerMapClusterFixtures.NEUTRAL_PALETTE,
                        OwnerMapClusterFixtures.NEUTRAL_PALETTE),
                    new ViewReading(
                        mock(OwnerPaintedView.class),
                        OwnerReadingFake.createAnsweringNothing(),
                        new OwnerSourceFake()),
                    ContentInputs.createEmpty(),
                    false));

            clusters.getPaintedCells().putPaintedCell(
                SYSTEM_CELL,
                new PaintedCell(fusedCell, List.of()));

            clusters.getPaintedCells().putPaintedCell(
                LONE_CELL,
                new PaintedCell(loneCell, List.of()));

            clusters.getStyledClusterGroupByOwnerId().put("owner", styledClusterGroup);

            ClusterDrawLists drawLists = clusters;

            // ACT
            var actualIsEmpty = drawLists.isEmpty();
            var actualFusedCells = drawLists.getFusedCells();
            var actualLoneCells = drawLists.getLoneCells();
            var actualClusterGroups = drawLists.getStyledClusterGroupByOwnerId();
            var actualGlobalStyle = drawLists.getGlobalStyle();

            // ASSERT
            assertThat(actualIsEmpty)
                .isFalse();
            assertThat(actualFusedCells)
                .containsExactly(fusedCell);
            assertThat(actualLoneCells)
                .containsExactly(loneCell);
            assertThat(actualClusterGroups)
                .containsExactlyEntriesOf(Map.of("owner", styledClusterGroup));
            assertThat(actualGlobalStyle)
                .isSameAs(expectedGlobalStyle);
        }
    }

    @Nested
    class RebuildClusterGroupOf {

        // The owner is read off the occupancy through the members, as the full build and a batch
        // both trace it, and what the builder bakes is recorded under the owner's ID.
        @Test
        void tracesTheMembersUnderTheirOwnerAndRecordsTheGroup() {

            // ARRANGE
            var clusters = buildOwnedBy(Map.of("A", "hegemony"));
            var cellGrouping = clusters.resolveCellGroupingOver(buildDrawnSystemKeys(Map.of("A", "A")));
            var memberCellKeys = List.of(buildCellKey("A"));
            var clusterGroup = buildAnyStyledClusterGroup();
            var groupBuilderMock = mock(ClusterGroupBuilder.class);

            when(groupBuilderMock.buildClusterGroup(readOwnerOf("hegemony"), memberCellKeys))
                .thenReturn(clusterGroup);

            // ACT
            clusters.rebuildClusterGroupOf(groupBuilderMock, cellGrouping, "hegemony", memberCellKeys);

            // ASSERT
            assertThat(clusters.getStyledClusterGroupByOwnerId())
                .containsExactlyEntriesOf(Map.of("hegemony", clusterGroup));
        }

        // An owner that lost its last member draws nothing, so the group it had goes and nothing is
        // traced for it.
        @Test
        void dropsAnOwnerLeftWithNoMembersWithoutTracingIt() {

            // ARRANGE
            var clusters = buildDrawablesWith(Map.of(), Map.of("hegemony", buildAnyStyledClusterGroup()));
            var cellGrouping = clusters.resolveCellGroupingOver(Map.of());
            var groupBuilderMock = mock(ClusterGroupBuilder.class);

            // ACT
            clusters.rebuildClusterGroupOf(groupBuilderMock, cellGrouping, "hegemony", List.of());

            // ASSERT
            assertThat(clusters.getStyledClusterGroupByOwnerId())
                .isEmpty();

            verifyNoInteractions(groupBuilderMock);
        }

        // A batch asks after every owner it touched, and the grouping names no members at all for an
        // owner that holds nothing any more.
        @Test
        void dropsAnOwnerTheGroupingNamesNoMembersFor() {

            // ARRANGE
            var clusters = buildDrawablesWith(Map.of(), Map.of("hegemony", buildAnyStyledClusterGroup()));
            var cellGrouping = clusters.resolveCellGroupingOver(Map.of());
            var groupBuilderMock = mock(ClusterGroupBuilder.class);

            // ACT
            clusters.rebuildClusterGroupOf(groupBuilderMock, cellGrouping, "hegemony", null);

            // ASSERT
            assertThat(clusters.getStyledClusterGroupByOwnerId())
                .isEmpty();

            verifyNoInteractions(groupBuilderMock);
        }
    }

    @Nested
    class PutStyledClusterGroup {

        @Test
        void recordsTheOwnersGroupUnderItsOwner() {

            // ARRANGE
            var clusterGroup = buildAnyStyledClusterGroup();
            var clusters = buildDrawablesWith(Map.of(), Map.of());

            // ACT
            clusters.putStyledClusterGroup("hegemony", clusterGroup);

            // ASSERT
            assertThat(clusters.getStyledClusterGroupByOwnerId())
                .containsExactlyEntriesOf(Map.of("hegemony", clusterGroup));
        }

        // An owner that lost its last cell hands over no group; an entry left standing for it would
        // go on painting the fill and border it no longer has.
        @Test
        void dropsAnOwnerWhoseGroupBakesNothing() {

            // ARRANGE
            var clusters = buildDrawablesWith(Map.of(), Map.of("hegemony", buildAnyStyledClusterGroup()));

            // ACT
            clusters.putStyledClusterGroup("hegemony", null);

            // ASSERT
            assertThat(clusters.getStyledClusterGroupByOwnerId())
                .isEmpty();
        }
    }

    @Nested
    class ListCandidateBorderLoopsOf {

        // Flattened across bodies and in cluster order: the cursor read cannot say which body it is
        // in, so every loop the owner strokes anywhere is a candidate.
        @Test
        void gathersEveryLoopOfEveryBodyOuterRingsAndEnclavesAlike() {

            // ARRANGE
            var homeOuter = new float[] {0, 0};
            var homeEnclave = new float[] {1, 1};
            var exclaveOuter = new float[] {2, 2};
            var clusters = buildDrawablesWith(Map.of(), Map.of(
                "hegemony",
                OwnerMapClusterFixtures.createClusterGroupWithLoops(List.of(
                    List.of(homeOuter, homeEnclave),
                    List.of(exclaveOuter)))));

            // ACT
            var actualLoops = clusters.listCandidateBorderLoopsOf("hegemony");

            // ASSERT
            assertThat(actualLoops)
                .containsExactly(homeOuter, homeEnclave, exclaveOuter);
        }

        @Test
        void returnsNothingForAnOwnerThatIsNotOnTheMap() {

            // ARRANGE
            var clusters = buildDrawablesWith(Map.of(), Map.of());

            // ACT
            var actualLoops = clusters.listCandidateBorderLoopsOf("hegemony");

            // ASSERT
            assertThat(actualLoops)
                .isEmpty();
        }

        // Identity, not equality. The highlight memoises its resolved halo against the instance it
        // was handed, so a fresh list per ask would silently defeat that memo and re-clip the wash
        // every frame the cursor rests on one cell - a change no assertion about the loops themselves
        // would notice.
        @Test
        void keepsTheSameListWhileTheOwnersBodiesStand() {

            // ARRANGE
            var clusters = buildDrawablesWith(Map.of(), Map.of(
                "hegemony",
                OwnerMapClusterFixtures.createClusterGroupWithLoops(
                    List.of(List.of(new float[] {0, 0})))));

            // ACT
            var actualFirstLoops = clusters.listCandidateBorderLoopsOf("hegemony");
            var actualSecondLoops = clusters.listCandidateBorderLoopsOf("hegemony");

            // ASSERT
            assertThat(actualSecondLoops)
                .isSameAs(actualFirstLoops);
        }

        // The other half of retaining an answer: an incremental refresh replaces an owner's group
        // wholesale when a neighbour flips, and a retained list that outlived it would halo a frontier
        // the map is no longer drawing.
        @Test
        void recomputesWhenARefreshReplacesTheOwnersBodies() {

            // ARRANGE
            var rebuiltLoop = new float[] {9, 9};
            var clusters = buildDrawablesWith(Map.of(), Map.of(
                "hegemony",
                OwnerMapClusterFixtures.createClusterGroupWithLoops(
                    List.of(List.of(new float[] {0, 0})))));

            clusters.listCandidateBorderLoopsOf("hegemony");
            clusters.getStyledClusterGroupByOwnerId().put(
                "hegemony",
                OwnerMapClusterFixtures.createClusterGroupWithLoops(
                    List.of(List.of(rebuiltLoop))));

            // ACT
            var actualLoops = clusters.listCandidateBorderLoopsOf("hegemony");

            // ASSERT
            assertThat(actualLoops)
                .containsExactly(rebuiltLoop);
        }
    }

    @Nested
    class Getters {

        // The two draw lists are created internally, not passed, so the build can fill them; they
        // start empty and stay mutable for the incremental refresh to edit in place. Both snapshots
        // are handed over whole and handed back the same, by identity: the occupancy because the
        // refresh folds into the very instance the build resolved, and the inputs because a reader
        // takes the record and names the snapshot it reads off it.
        @Test
        void handBackTheTwoSnapshotsHandedIn() {

            // ARRANGE
            var occupancy = SystemOccupancy.createEmpty();
            var buildInputs = OwnerMapBuildInputs.createEmpty(mock(OwnerPaintedView.class));

            // ACT
            var actualClusters = new OwnerMapClusters(occupancy, buildInputs);

            // ASSERT
            assertThat(actualClusters.getStyledCellByCellKey())
                .isEmpty();
            assertThat(actualClusters.getStyledClusterGroupByOwnerId())
                .isEmpty();
            assertThat(actualClusters.getOccupancy())
                .isSameAs(occupancy);
            assertThat(actualClusters.getBuildInputs())
                .isSameAs(buildInputs);
        }
    }

    @Nested
    class CreatePaintedCellBuilder {

        // The one place the pipeline states the builder's inputs, so the full build and every batch
        // after it bake a cell from the same paints and the same occupancy the refresh folds into -
        // by identity, since a copy of either would miss the next fold or memo.
        @Test
        void standsTheBuilderUpOnTheBuildsPaintsAndLiveOccupancy() {

            // ARRANGE
            var occupancy = SystemOccupancy.createEmpty();
            var buildInputs = OwnerMapBuildInputs.createEmpty(mock(OwnerPaintedView.class));
            var clusters = new OwnerMapClusters(occupancy, buildInputs);
            var actualArguments = new ArrayList<List<?>>();

            // ACT
            try (var builderConstructionMock = mockConstruction(
                    PaintedCellBuilder.class,
                    (builderMock, context) -> actualArguments.add(context.arguments()))) {

                clusters.createPaintedCellBuilder();
            }

            // ASSERT
            assertThat(actualArguments)
                .singleElement()
                .satisfies(arguments -> {

                    assertThat(arguments.get(0))
                        .isSameAs(buildInputs.getPaints());
                    assertThat(arguments.get(1))
                        .isSameAs(occupancy);
                });
        }
    }

    @Nested
    class CreateClusterGroupBuilder {

        // The paints are the build's own; the fill states are the occupancy's as they stand now, a
        // batch's folds included; the grouping and edges are the caller's, since a batch groups the
        // cells afresh once its flips have folded.
        @Test
        void standsTheBuilderUpOnTheBuildsPaintsAndCurrentFillStatesOverTheGivenCells() {

            // ARRANGE
            var buildInputs = OwnerMapBuildInputs.createEmpty(mock(OwnerPaintedView.class));
            var clusters = new OwnerMapClusters(SystemOccupancy.createEmpty(), buildInputs);

            clusters
                .getOccupancy()
                .recordOwnerOf(buildCellKey("A"), new ResolvedSystemOwner(readOwnerOf("F"), FillState.UNFILLED));

            var cellGrouping = clusters.resolveCellGroupingOver(Map.of());
            var cellEdgesByCellKey = Map.<SystemKey, List<CellEdge>>of();
            var actualArguments = new ArrayList<List<?>>();

            // ACT
            try (var builderConstructionMock = mockConstruction(
                    ClusterGroupBuilder.class,
                    (builderMock, context) -> actualArguments.add(context.arguments()))) {

                clusters.createClusterGroupBuilder(cellGrouping, cellEdgesByCellKey);
            }

            // ASSERT
            assertThat(actualArguments)
                .singleElement()
                .satisfies(arguments -> {

                    assertThat(arguments.get(0))
                        .isSameAs(buildInputs.getPaints());
                    assertThat(arguments.get(1))
                        .isSameAs(cellGrouping);
                    assertThat(arguments.get(2))
                        .isSameAs(cellEdgesByCellKey);
                    assertThat(((SystemFillStates) arguments.get(3)).getNonSolidFillStateBySystemKey())
                        .containsExactly(Map.entry(buildCellKey("A"), FillState.UNFILLED));
                });
        }
    }

    @Nested
    class ReindexClusters {

        @Test
        void resolvesASystemToItsWholeContiguousClusterGroup() {

            // ARRANGE
            var clusters = buildOwnedBy(Map.of("A", "F", "B", "F"));

            // ACT
            reindex(clusters, Map.of(
                "A", List.of(buildEdgeTo("B")),
                "B", List.of(buildEdgeTo("A"))));

            // ASSERT
            assertThat(clusters.getClusterIndex().findClusterMembersOf(buildCellKey("A")))
                .containsExactlyInAnyOrderElementsOf(buildCellKeys("A", "B"));
        }

        @Test
        void excludesADifferentlyOwnedNeighbour() {

            // ARRANGE
            var clusters = buildOwnedBy(Map.of("A", "F", "B", "RIVAL"));

            // ACT
            reindex(clusters, Map.of(
                "A", List.of(buildEdgeTo("B")),
                "B", List.of(buildEdgeTo("A"))));

            // ASSERT
            assertThat(clusters.getClusterIndex().findClusterMembersOf(buildCellKey("A")))
                .containsExactly(buildCellKey("A"));
        }

        // Why the index is re-derived rather than patched: B is the only thing joining A to C, so B
        // changing hands splits one cluster group into two pockets - a change no edit of the standing
        // index would find, since neither A nor C was itself touched.
        @Test
        void seversOneClusterGroupInTwoWhenTheBridgeSystemFlips() {

            // ARRANGE
            var edges = Map.of(
                "A", List.of(buildEdgeTo("B")),
                "B", List.of(buildEdgeTo("A"), buildEdgeTo("C")),
                "C", List.of(buildEdgeTo("B")));

            var clusters = buildOwnedBy(Map.of("A", "F", "B", "F", "C", "F"));

            reindex(clusters, edges);

            clusters
                .getOccupancy()
                .recordOwnerOf(buildCellKey("B"), ResolvedSystemOwner.createSolidlyFilledBy(readOwnerOf("RIVAL")));

            // ACT
            reindex(clusters, edges);

            // ASSERT
            assertThat(clusters.getClusterIndex().findClusterMembersOf(buildCellKey("A")))
                .containsExactly(buildCellKey("A"));
            assertThat(clusters.getClusterIndex().findClusterMembersOf(buildCellKey("C")))
                .containsExactly(buildCellKey("C"));
        }

        // The mirror of the sever: B joining F merges what were two lone pockets.
        @Test
        void bridgesTwoClustersIntoOneWhenTheGapSystemIsGained() {

            // ARRANGE
            var clusters = buildOwnedBy(Map.of("A", "F", "B", "RIVAL", "C", "F"));
            var edges = Map.of(
                "A", List.of(buildEdgeTo("B")),
                "B", List.of(buildEdgeTo("A"), buildEdgeTo("C")),
                "C", List.of(buildEdgeTo("B")));

            reindex(clusters, edges);

            clusters
                .getOccupancy()
                .recordOwnerOf(buildCellKey("B"), ResolvedSystemOwner.createSolidlyFilledBy(readOwnerOf("F")));

            // ACT
            reindex(clusters, edges);

            // ASSERT
            assertThat(clusters.getClusterIndex().findClusterMembersOf(buildCellKey("A")))
                .containsExactlyInAnyOrderElementsOf(buildCellKeys("A", "B", "C"));
        }

        @Test
        void carriesNoClusterForAnUnownedSystem() {

            // ARRANGE
            var clusters = buildOwnedBy(Map.of("A", "F"));

            // ACT
            reindex(clusters, Map.of(
                "A", List.of(buildEdgeTo("UNOWNED")),
                "UNOWNED", List.of(buildEdgeTo("A"))));

            // ASSERT
            assertThat(clusters.getClusterIndex().findClusterMembersOf(buildCellKey("UNOWNED")))
                .isEmpty();
        }
    }

    // A clusters holding the given owners, the one input the cluster index is derived from;
    // every other slot is an inert placeholder.
    private static OwnerMapClusters buildOwnedBy(Map<String, String> ownerIdBySystemId) {

        var ownerBySystemId = new LinkedHashMap<String, SystemOwner>();

        for (var entry : ownerIdBySystemId.entrySet()) {
            ownerBySystemId.put(entry.getKey(), readOwnerOf(entry.getValue()));
        }

        return OwnerMapClusterFixtures.createClustersOwnedBy(ownerBySystemId);
    }

    // Clustering keys off the owner ID alone, so the palette shades are inert here.
    private static SystemOwner readOwnerOf(String ownerId) {

        return new SystemOwner(ownerId, new ColourPair(Color.GRAY, Color.GRAY));
    }

    // Reindexes the clusters over the given adjacency, each cell drawing as its own star
    // (identity draws-as), so a test names only the edges the clusters are walked over.
    private static void reindex(
            OwnerMapClusters clusters,
            Map<String, List<CellEdge>> edges) {

        var systemIdByCellId = new LinkedHashMap<String, String>();

        for (var cellId : edges.keySet()) {
            systemIdByCellId.put(cellId, cellId);
        }

        clusters.reindexClusters(
            buildKeyedValues(edges),
            clusters.resolveCellGroupingOver(buildDrawnSystemKeys(systemIdByCellId)));
    }

    // A clusters whose only varying inputs are the two draw lists; the retained inputs are the
    // shared fixture's inert placeholders, since isEmpty reads only the draw lists.
    private static OwnerMapClusters buildDrawablesWith(
            Map<SystemKey, StyledCell> styledCells,
            Map<String, StyledClusterGroup> clusters) {

        var drawables = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());

        // The draw lists are not constructor inputs; fill the internally-created ones so
        // this fixture's only varying state is what isEmpty reads.
        styledCells.forEach((cellKey, styledCell) -> drawables
            .getPaintedCells()
            .putPaintedCell(cellKey, new PaintedCell(styledCell, List.of())));

        drawables.getStyledClusterGroupByOwnerId().putAll(clusters);

        return drawables;
    }

    private static StyledCell buildAnyStyledCell() {

        return OwnerMapClusterFixtures.createPlaceholderStyledCell();
    }

    private static StyledClusterGroup buildAnyStyledClusterGroup() {

        return OwnerMapClusterFixtures.createClusterGroupWithLoops(List.of());
    }

    // A CategoryStyle whose opacities and widths carry one marker value, so a style bundle is a
    // distinct instance the seam can be checked against.
    private static CategoryStyle buildStyleMarked(double marker) {

        var element = new ElementStyle(OwnerPaletteSlot.PRIMARY, marker);

        return new CategoryStyle(element, element, marker, element, marker);
    }
}
