package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.render.clusters.ClusterDrawLists;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.base.theme.BorderSmoothingStyle;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.CornerRoundingStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.SpikeSandingStyle;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapCategory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the built map state the renderer paints and the incremental refresh edits:
 * that the empty fallback is a harmless no-op the render path can lean on after a failed
 * first build, that {@link PoliticalMapTerritories#isEmpty} tracks either draw list, and
 * that the constructor threads its inputs into the matching accessors (the {@link RenderStyle}
 * theme, whose global tier and four same-typed category bundles a swap would not otherwise catch,
 * the view and grouping the incremental re-shape classifies against, and the filter snapshot it
 * recedes by).
 *
 * <p>Also pins the two invariants the cursor read leans on, since a break in either is invisible
 * until a hover lands on the wrong cell: that a cell's draw record and the shape it is hit-tested
 * against are written and dropped together, and that the cluster index tracks holding through
 * the splits and merges a single flip can cause.
 */
final class PoliticalMapTerritoriesTest {

    @Nested
    class CreateEmpty {

        @Test
        void createEmptyYieldsAnEmptyNoOpFallback() {

            // A stand-in view so this model test names no concrete view: the territories only carry
            // the view for the incremental re-shape to read back, so any PoliticalMapView serves.
            var viewMock = mock(PoliticalMapView.class);
            var territories = PoliticalMapTerritories.createEmpty(viewMock);

            // Both draw lists empty, so the render is a no-op and isEmpty short-circuits
            // the GL state push; the retained inputs are neutral placeholders the next
            // frame's real build replaces before any incremental pass reads them.
            assertThat(territories.isEmpty())
                .isTrue();

            assertThat(territories.getStyledCellByCellId())
                .isEmpty();
            assertThat(territories.getStyledClusterGroupByOwnerId())
                .isEmpty();
            assertThat(territories.getHolderBySystemId())
                .isEmpty();
            assertThat(territories.getInhabitedSystemIds())
                .isEmpty();
            assertThat(territories.getUnfilledSystemIds())
                .isEmpty();

            assertThat(territories.getNeutralColour())
                .isEqualTo(Color.GRAY);
            assertThat(territories.getDesaturationPalette())
                .isEqualTo(new FactionPalette(Color.GRAY, Color.GRAY));

            // The empty fallback is never a filtered build, so it selects no bloc and recedes
            // nothing.
            assertThat(territories.getSelectedBlocId())
                .isNull();
            assertThat(territories.isFiltering())
                .isFalse();
            assertThat(territories.getRecedeAdjustment())
                .isEqualTo(ElementStyleAdjustment.NONE);
            assertThat(territories.getContestedSystemIds())
                .isEmpty();
        }
    }

    @Nested
    class IsEmpty {

        @Test
        void isEmptyIsTrueWhenBothDrawListsAreEmpty() {

            var territories = buildDrawablesWith(Map.of(), Map.of());

            assertThat(territories.isEmpty())
                .isTrue();
        }

        @Test
        void isEmptyIsFalseWhenAStyledCellIsPresent() {

            var territories = buildDrawablesWith(Map.of("system", buildAnyStyledCell()), Map.of());

            assertThat(territories.isEmpty())
                .isFalse();
        }

        @Test
        void isEmptyIsFalseWhenAClusterGroupIsPresent() {

            var territories = buildDrawablesWith(Map.of(), Map.of("faction", buildAnyStyledClusterGroup()));

            assertThat(territories.isEmpty())
                .isFalse();
        }
    }

    @Nested
    class SatisfiesClusterDrawLists {

        @Test
        void satisfiesClusterDrawListsWithTheDrawListsAndTierTheBuildItselfHolds() {

            var styledCell = buildAnyStyledCell();
            var styledClusterGroup = buildAnyStyledClusterGroup();

            // A real theme rather than the shared fixture's inert one, since the global tier is
            // one of the four reads and the seam has to hand over the build's own.
            var renderStyle = PoliticalMapTerritoryFixtures
                .createRenderStyleForEveryCategory(buildStyleMarked(1));

            var territories = new PoliticalMapTerritories(
                new LinkedHashMap<>(),
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                new MapStyling(
                    renderStyle,
                    Color.GRAY,
                    new FactionPalette(Color.GRAY, Color.GRAY)),
                new ViewGrouping(mock(PoliticalMapView.class), HolderGrouping.identity()),
                FilterSnapshot.unfiltered());

            territories.getStyledCellByCellId().put("system", styledCell);
            territories.getStyledClusterGroupByOwnerId().put("faction", styledClusterGroup);

            // Read through the seam rather than off the class, which is what the framework
            // emission sees. Satisfying the interface with anything other than the build's own two
            // maps - a copy, or a second pair left empty - would paint a frame that no assertion
            // over the political accessors could tell apart from a correct one.
            ClusterDrawLists drawLists = territories;

            assertThat(drawLists.isEmpty())
                .isFalse();
            assertThat(drawLists.getStyledCellByCellId())
                .containsExactlyEntriesOf(Map.of("system", styledCell));
            assertThat(drawLists.getStyledClusterGroupByOwnerId())
                .containsExactlyEntriesOf(Map.of("faction", styledClusterGroup));
            assertThat(drawLists.getGlobalStyle())
                .isSameAs(renderStyle.global());
        }
    }

    @Nested
    class ListCandidateBorderLoopsOf {

        @Test
        void listCandidateBorderLoopsOfGathersEveryLoopOfEveryBodyOuterRingsAndEnclavesAlike() {

            var homeOuter = new float[] {0, 0};
            var homeEnclave = new float[] {1, 1};
            var exclaveOuter = new float[] {2, 2};
            var territories = buildDrawablesWith(Map.of(), Map.of(
                "hegemony",
                PoliticalMapTerritoryFixtures.createTerritoryWithLoops(List.of(
                    List.of(homeOuter, homeEnclave),
                    List.of(exclaveOuter)))));

            // Flattened across bodies and in cluster order: the cursor read cannot say which body
            // it is in, so every loop the bloc strokes anywhere is a candidate.
            assertThat(territories.listCandidateBorderLoopsOf("hegemony"))
                .containsExactly(homeOuter, homeEnclave, exclaveOuter);
        }

        @Test
        void listCandidateBorderLoopsOfReturnsNothingForABlocThatIsNotOnTheMap() {

            var territories = buildDrawablesWith(Map.of(), Map.of());

            assertThat(territories.listCandidateBorderLoopsOf("hegemony"))
                .isEmpty();
        }

        @Test
        void listCandidateBorderLoopsOfKeepsTheSameListWhileTheBlocsBodiesStand() {

            var territories = buildDrawablesWith(Map.of(), Map.of(
                "hegemony",
                PoliticalMapTerritoryFixtures.createTerritoryWithLoops(
                    List.of(List.of(new float[] {0, 0})))));

            // Identity, not equality. The highlight memoises its resolved halo against the
            // instance it was handed, so a fresh list per ask would silently defeat that memo and
            // re-clip the wash every frame the cursor rests on one cell - a change no assertion
            // about the loops themselves would notice.
            assertThat(territories.listCandidateBorderLoopsOf("hegemony"))
                .isSameAs(territories.listCandidateBorderLoopsOf("hegemony"));
        }

        @Test
        void listCandidateBorderLoopsOfRecomputesWhenARefreshReplacesTheBlocsBodies() {

            var rebuiltLoop = new float[] {9, 9};
            var territories = buildDrawablesWith(Map.of(), Map.of(
                "hegemony",
                PoliticalMapTerritoryFixtures.createTerritoryWithLoops(
                    List.of(List.of(new float[] {0, 0})))));
                    
            territories.listCandidateBorderLoopsOf("hegemony");

            // The other half of retaining an answer: an incremental refresh replaces a bloc's
            // group wholesale when a neighbour flips, and a retained list that outlived it would
            // halo a frontier the map is no longer drawing.
            territories.getStyledClusterGroupByOwnerId().put("hegemony",
                PoliticalMapTerritoryFixtures.createTerritoryWithLoops(
                    List.of(List.of(rebuiltLoop))));

            assertThat(territories.listCandidateBorderLoopsOf("hegemony"))
                .containsExactly(rebuiltLoop);
        }
    }

    @Nested
    class Getters {

        @Test
        void gettersReturnEachConstructorInputInItsMatchingSlot() {

            var holders = new LinkedHashMap<String, DominantHolder>();
            var inhabited = new LinkedHashSet<>(Set.of("inhabited-system"));

            // A distinct unfilled set so a swapped slot is caught by identity, held apart from the
            // inhabited set it sits beside.
            var unfilled = new LinkedHashSet<>(Set.of("unfilled-system"));
            var neutral = Color.CYAN;
            var desaturationPalette = new FactionPalette(Color.MAGENTA, Color.ORANGE);

            // Four distinct category instances plus a distinct global tier, all wrapped in one
            // theme, so a swapped style slot is caught by identity, not just by the shared
            // CategoryStyle type the compiler would accept either way.
            var factionStyle = buildStyleMarked(1);
            var independentStyle = buildStyleMarked(2);
            var decivilisedStyle = buildStyleMarked(3);
            var uninhabitedStyle = buildStyleMarked(4);

            var categories = new LinkedHashMap<MapStyleCategory, CategoryStyle>();

            categories.put(PoliticalMapCategory.FACTION, factionStyle);
            categories.put(PoliticalMapCategory.INDEPENDENT, independentStyle);
            categories.put(PoliticalMapCategory.DECIVILISED, decivilisedStyle);
            categories.put(PoliticalMapCategory.UNINHABITED, uninhabitedStyle);

            // Every sector-wide value non-zero, so a getter reading the wrong tier is caught by
            // value rather than by both tiers happening to hold the same inert numbers.
            var globalStyle = new GlobalStyle(ThemeFixtures.createHatchStyle(5, 5, 5),
                new BorderSmoothingStyle(
                    new SpikeSandingStyle(true, 5, 5),
                    new CornerRoundingStyle(true, 5, 5, 5)),
                ThemeFixtures.NO_HOVER_HIGHLIGHT,
                0.3);

            var renderStyle = new RenderStyle(globalStyle, categories);
            var viewMock = mock(PoliticalMapView.class);
            var grouping = HolderGrouping.identity();

            // A distinct, non-identity adjustment so a swapped recede field is caught by value.
            var recedeAdjustment = new ElementStyleAdjustment(0.25, true);

            // A non-null selected bloc so the filter snapshot is caught by value and isFiltering()
            // reads true off it.
            var selectedBlocId = "selected-bloc";

            // A distinct contested set so a swapped filter-snapshot field is caught by identity.
            var contested = new LinkedHashSet<>(Set.of("contested-system"));

            // Likewise distinct, and held apart from the contested set it sits beside: the two are
            // both spotlit-presence sets, so only differing contents catch a swap between them.
            var spotlitPresence = new LinkedHashSet<>(Set.of("present-unheld-system"));

            var territories = new PoliticalMapTerritories(
                holders,
                inhabited,
                unfilled,
                new MapStyling(renderStyle, neutral, desaturationPalette),
                new ViewGrouping(viewMock, grouping),
                new FilterSnapshot(
                    selectedBlocId,
                    recedeAdjustment,
                    contested,
                    spotlitPresence));

            // The two draw lists are created internally, not passed, so the build can fill them;
            // they start empty and stay mutable for the incremental refresh to edit in place.
            assertThat(territories.getStyledCellByCellId())
                .isEmpty();
            assertThat(territories.getStyledClusterGroupByOwnerId())
                .isEmpty();

            assertThat(territories.getHolderBySystemId())
                .isSameAs(holders);
            assertThat(territories.getInhabitedSystemIds())
                .isSameAs(inhabited);
            assertThat(territories.getUnfilledSystemIds())
                .isSameAs(unfilled);
            assertThat(territories.getNeutralColour())
                .isSameAs(neutral);
            assertThat(territories.getDesaturationPalette())
                .isSameAs(desaturationPalette);
            assertThat(territories.getRenderStyle())
                .isSameAs(renderStyle);
            assertThat(territories.getGlobalStyle())
                .isSameAs(globalStyle);

            assertThat(territories.getCategoryStyle(PoliticalMapCategory.FACTION))
                .isSameAs(factionStyle);
            assertThat(territories.getCategoryStyle(PoliticalMapCategory.INDEPENDENT))
                .isSameAs(independentStyle);
            assertThat(territories.getCategoryStyle(PoliticalMapCategory.DECIVILISED))
                .isSameAs(decivilisedStyle);
            assertThat(territories.getCategoryStyle(PoliticalMapCategory.UNINHABITED))
                .isSameAs(uninhabitedStyle);

            assertThat(territories.getView())
                .isSameAs(viewMock);
            assertThat(territories.getGrouping())
                .isSameAs(grouping);
            assertThat(territories.getSelectedBlocId())
                .isEqualTo(selectedBlocId);
            assertThat(territories.isFiltering())
                .isTrue();
            assertThat(territories.getRecedeAdjustment())
                .isSameAs(recedeAdjustment);
            assertThat(territories.getContestedSystemIds())
                .isSameAs(contested);
            assertThat(territories.getSpotlitPresenceSystemIds())
                .isSameAs(spotlitPresence);
        }
    }

    @Nested
    class PutStyledCell {

        @Test
        void putStyledCellRecordsTheDrawRecordAndItsShapeUnderTheSameSystem() {

            var territories = buildDrawablesWith(Map.of(), Map.of());
            var styledCell = buildAnyStyledCell();
            var fillPolygon = buildSquarePolygon();

            territories.putStyledCell("system", styledCell, fillPolygon);

            assertThat(territories.getStyledCellByCellId())
                .containsOnlyKeys("system");
            assertThat(territories.getStyledCellByCellId()
                .get("system")).isSameAs(styledCell);

            assertThat(territories.getFillPolygonByCellId())
                .containsOnlyKeys("system");
            assertThat(territories.getFillPolygonByCellId()
                .get("system")).isSameAs(fillPolygon);
        }

        @Test
        void putStyledCellReplacesBothHalvesWhenACellIsReshaped() {
            // The drift the paired write exists to prevent: a re-shaped cell must not keep
            // answering the cursor with the extent it had before it was re-shaped.
            var territories = buildDrawablesWith(Map.of(), Map.of());
            territories.putStyledCell("system", buildAnyStyledCell(), buildSquarePolygon());

            var reshapedCell = buildAnyStyledCell();
            var reshapedPolygon = buildTrianglePolygon();

            territories.putStyledCell("system", reshapedCell, reshapedPolygon);

            assertThat(territories.getStyledCellByCellId().get("system"))
                .isSameAs(reshapedCell);
            assertThat(territories.getFillPolygonByCellId().get("system"))
                .isSameAs(reshapedPolygon);
        }
    }

    @Nested
    class RemoveStyledCell {

        @Test
        void removeStyledCellDropsTheDrawRecordAndItsShapeTogether() {
            // A cell that draws nothing can be hovered no more than it can be seen, so the
            // shape must go with the draw record rather than linger as a phantom hit cluster.
            var territories = buildDrawablesWith(Map.of(), Map.of());

            territories.putStyledCell("system", buildAnyStyledCell(), buildSquarePolygon());
            territories.removeStyledCell("system");

            assertThat(territories.getStyledCellByCellId())
                .isEmpty();
            assertThat(territories.getFillPolygonByCellId())
                .isEmpty();
        }

        @Test
        void removeStyledCellLeavesEveryOtherCellStanding() {

            var territories = buildDrawablesWith(Map.of(), Map.of());

            territories.putStyledCell("dropped", buildAnyStyledCell(), buildSquarePolygon());
            territories.putStyledCell("kept", buildAnyStyledCell(), buildTrianglePolygon());
            territories.removeStyledCell("dropped");

            assertThat(territories.getStyledCellByCellId())
                .containsOnlyKeys("kept");
            assertThat(territories.getFillPolygonByCellId())
                .containsOnlyKeys("kept");
        }
    }

    @Nested
    class ReindexClusters {

        @Test
        void reindexClustersResolvesASystemToItsWholeContiguousTerritory() {

            var territories = buildOwnedBy(Map.of("A", "F", "B", "F"));

            reindex(territories, Map.of(
                "A", List.of(buildEdgeTo("B")),
                "B", List.of(buildEdgeTo("A"))));

            assertThat(territories.getClusterIndex().findClusterMembersOf("A"))
                .containsExactlyInAnyOrder("A", "B");
        }

        @Test
        void reindexClustersExcludesADifferentlyOwnedNeighbour() {
            
            var territories = buildOwnedBy(Map.of("A", "F", "B", "RIVAL"));

            reindex(territories, Map.of(
                "A", List.of(buildEdgeTo("B")),
                "B", List.of(buildEdgeTo("A"))));

            assertThat(territories.getClusterIndex().findClusterMembersOf("A"))
                .containsExactly("A");
        }

        @Test
        void reindexClustersSeversOneTerritoryInTwoWhenTheBridgeSystemFlips() {
            // Why the index is re-derived rather than patched: B is the only thing joining A to
            // C, so B changing hands splits one territory into two pockets - a change no edit of
            // the old index would find, since neither A nor C was itself touched.
            var edges = Map.of(
                "A", List.of(buildEdgeTo("B")),
                "B", List.of(buildEdgeTo("A"), buildEdgeTo("C")),
                "C", List.of(buildEdgeTo("B")));

            var territories = buildOwnedBy(Map.of("A", "F", "B", "F", "C", "F"));
            reindex(territories, edges);

            assertThat(territories.getClusterIndex().findClusterMembersOf("A"))
                .containsExactlyInAnyOrder("A", "B", "C");

            territories.getHolderBySystemId().put("B", readOwnerOf("RIVAL"));
            reindex(territories, edges);

            assertThat(territories.getClusterIndex().findClusterMembersOf("A"))
                .containsExactly("A");
            assertThat(territories.getClusterIndex().findClusterMembersOf("C"))
                .containsExactly("C");
        }

        @Test
        void reindexClustersBridgesTwoTerritoriesIntoOneWhenTheGapSystemIsGained() {
            // The mirror of the sever: B joining F merges what were two lone pockets.
            var edges = Map.of(
                "A", List.of(buildEdgeTo("B")),
                "B", List.of(buildEdgeTo("A"), buildEdgeTo("C")),
                "C", List.of(buildEdgeTo("B")));
            var territories = buildOwnedBy(Map.of("A", "F", "B", "RIVAL", "C", "F"));
            reindex(territories, edges);

            territories.getHolderBySystemId().put("B", readOwnerOf("F"));
            reindex(territories, edges);

            assertThat(territories.getClusterIndex().findClusterMembersOf("A"))
                .containsExactlyInAnyOrder("A", "B", "C");
        }

        @Test
        void reindexClustersCarriesNoClusterForAnUnownedSystem() {

            var territories = buildOwnedBy(Map.of("A", "F"));

            reindex(territories, Map.of(
                "A", List.of(buildEdgeTo("UNOWNED")),
                "UNOWNED", List.of(buildEdgeTo("A"))));

            assertThat(territories.getClusterIndex().findClusterMembersOf("UNOWNED"))
                .isEmpty();
        }
    }

    // A territories holding the given holders, the one input the cluster index is derived from;
    // every other slot is an inert placeholder.
    private static PoliticalMapTerritories buildOwnedBy(Map<String, String> factionIdBySystemId) {

        var ownerBySystemId = new LinkedHashMap<String, DominantHolder>();

        for (var entry : factionIdBySystemId.entrySet()) {
            ownerBySystemId.put(entry.getKey(), readOwnerOf(entry.getValue()));
        }
        return PoliticalMapTerritoryFixtures.createTerritoriesOwnedBy(ownerBySystemId);
    }

    // Clustering keys off the faction id alone, so the palette shades are inert here.
    private static DominantHolder readOwnerOf(String factionId) {
        return new DominantHolder(factionId, Color.GRAY, Color.GRAY);
    }

    // One cell edge facing the given neighbour system. Clustering reads only the adjacency tag,
    // so the segment is left at the origin.
    private static CellEdge buildEdgeTo(String neighbourSystemId) {
        return new CellEdge(0, 0, 0, 0, new EdgeTarget.AcrossSystem(neighbourSystemId));
    }

    // Reindexes the clusters over the given adjacency, each cell drawing as its own star
    // (identity draws-as), so a test names only the edges the clusters are walked over.
    private static void reindex(
            PoliticalMapTerritories territories,
            Map<String, List<CellEdge>> edges) {

        var systemIdByCellId = new LinkedHashMap<String, String>();
        for (var cellId : edges.keySet()) {
            systemIdByCellId.put(cellId, cellId);
        }
        territories.reindexClusters(edges, systemIdByCellId);
    }

    // Two distinct fill shapes, so a test that swaps one for the other is caught by identity.
    // Nothing here reads the geometry, only which instance is held against a system.
    private static List<double[]> buildSquarePolygon() {
        return List.of(
            new double[] {0, 0},
            new double[] {1, 0},
            new double[] {1, 1},
            new double[] {0, 1});
    }

    private static List<double[]> buildTrianglePolygon() {
        return List.of(
            new double[] {0, 0},
            new double[] {2, 0},
            new double[] {0, 2});
    }

    // A territories whose only varying inputs are the two draw lists; the retained inputs are the
    // shared fixture's inert placeholders, since isEmpty reads only the draw lists.
    private static PoliticalMapTerritories buildDrawablesWith(
            Map<String, StyledCell> styledCells,
            Map<String, StyledClusterGroup> territories) {

        var drawables = PoliticalMapTerritoryFixtures.createTerritoriesOwnedBy(Map.of());

        // The draw lists are not constructor inputs; fill the internally-created maps so
        // this fixture's only varying state is what isEmpty reads.
        drawables.getStyledCellByCellId().putAll(styledCells);
        drawables.getStyledClusterGroupByOwnerId().putAll(territories);
        
        return drawables;
    }

    private static StyledCell buildAnyStyledCell() {
        return PoliticalMapTerritoryFixtures.createPlaceholderStyledCell();
    }

    private static StyledClusterGroup buildAnyStyledClusterGroup() {
        return PoliticalMapTerritoryFixtures.createTerritoryWithLoops(List.of());
    }

    // A CategoryStyle whose opacities and widths carry one marker value, so four otherwise
    // interchangeable style bundles are distinct instances.
    private static CategoryStyle buildStyleMarked(double marker) {
        
        var element = new ElementStyle(FactionPaletteSlot.PRIMARY, marker);
        return new CategoryStyle(element, element, marker, element, marker);
    }
}
