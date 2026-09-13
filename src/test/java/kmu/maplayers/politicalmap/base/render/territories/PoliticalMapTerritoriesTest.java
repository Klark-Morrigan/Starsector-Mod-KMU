package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.render.clusters.ClusterDrawLists;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.ContentInputs;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellEdgeFixture.buildEdgeTo;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildDrawnSystemKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the built map state the renderer paints and the incremental refresh edits:
 * that the empty fallback is a harmless no-op the render path can lean on after a failed
 * first build, that {@link PoliticalMapTerritories#isEmpty} tracks either draw list, and
 * that the two snapshots handed in - the live occupancy and the inputs the build was baked
 * under - are the two handed back, with the global tier the emission reads coming off the
 * retained theme.
 *
 * <p>Also pins the invariant the cursor read leans on that this model owns: that the cluster index
 * tracks holding through the splits and merges a single flip can cause, a break in which is
 * invisible until a hover resolves the wrong territory. What one cell carries, and what is dropped
 * when its ring is replaced, is {@link PaintedCellStoreTest}'s; what the retained inputs answer
 * for themselves is {@link TerritoryBuildInputsTest}'s.
 */
final class PoliticalMapTerritoriesTest {

    // The cell the draw lists are exercised over, keyed as the cut keys a cell.
    private static final SystemKey SYSTEM_CELL = buildCellKey("system");

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

            assertThat(territories.getStyledCellByCellKey())
                .isEmpty();
            assertThat(territories.getStyledClusterGroupByOwnerId())
                .isEmpty();
            assertThat(territories.getOccupancy().getHolderBySystemKey())
                .isEmpty();
            assertThat(territories.getOccupancy().getInhabitedSystemKeys())
                .isEmpty();
            assertThat(territories.getOccupancy().getSpotlitPresenceSystemKeys())
                .isEmpty();

            // The placeholder inputs' own stand-ins are TerritoryBuildInputs' to pin; read back
            // here only to prove the fallback carries the view it was built for, which is the one
            // thing about them a later frame reads.
            assertThat(territories.getBuildInputs().viewGrouping().view())
                .isSameAs(viewMock);
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

            var territories = buildDrawablesWith(Map.of(SYSTEM_CELL, buildAnyStyledCell()), Map.of());

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
                SystemOccupancy.createEmpty(),
                new TerritoryBuildInputs(
                    new MapStyling(
                        renderStyle,
                        PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE,
                        PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE,
                        PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE),
                    new ViewGrouping(mock(PoliticalMapView.class), HolderGrouping.identity()),
                    ContentInputs.createEmpty(),
                    Set.of(),
                    Set.of()));

            territories.getStyledCellByCellKey().put(SYSTEM_CELL, styledCell);
            territories.getStyledClusterGroupByOwnerId().put("faction", styledClusterGroup);

            // Read through the seam rather than off the class, which is what the framework
            // emission sees. Satisfying the interface with anything other than the build's own two
            // maps - a copy, or a second pair left empty - would paint a frame that no assertion
            // over the political accessors could tell apart from a correct one.
            ClusterDrawLists drawLists = territories;

            assertThat(drawLists.isEmpty())
                .isFalse();
            assertThat(drawLists.getStyledCellByCellKey())
                .containsExactlyEntriesOf(Map.of(SYSTEM_CELL, styledCell));
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
        void gettersHandBackTheTwoSnapshotsHandedIn() {

            var occupancy = SystemOccupancy.createEmpty();
            var buildInputs = TerritoryBuildInputs.createEmpty(mock(PoliticalMapView.class));

            var territories = new PoliticalMapTerritories(occupancy, buildInputs);

            // The two draw lists are created internally, not passed, so the build can fill them;
            // they start empty and stay mutable for the incremental refresh to edit in place.
            assertThat(territories.getStyledCellByCellKey())
                .isEmpty();
            assertThat(territories.getStyledClusterGroupByOwnerId())
                .isEmpty();

            // Both snapshots are handed over whole and handed back the same, by identity: the
            // occupancy because the refresh folds into the very instance the build resolved, and
            // the inputs because a reader takes the record and names the snapshot it reads off it.
            assertThat(territories.getOccupancy())
                .isSameAs(occupancy);
            assertThat(territories.getBuildInputs())
                .isSameAs(buildInputs);
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

            assertThat(territories.getClusterIndex().findClusterMembersOf(buildCellKey("A")))
                .containsExactlyInAnyOrderElementsOf(buildCellKeys("A", "B"));
        }

        @Test
        void reindexClustersExcludesADifferentlyOwnedNeighbour() {

            var territories = buildOwnedBy(Map.of("A", "F", "B", "RIVAL"));

            reindex(territories, Map.of(
                "A", List.of(buildEdgeTo("B")),
                "B", List.of(buildEdgeTo("A"))));

            assertThat(territories.getClusterIndex().findClusterMembersOf(buildCellKey("A")))
                .containsExactly(buildCellKey("A"));
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

            assertThat(territories.getClusterIndex().findClusterMembersOf(buildCellKey("A")))
                .containsExactlyInAnyOrderElementsOf(buildCellKeys("A", "B", "C"));

            territories.getOccupancy().recordHolderOf(buildCellKey("B"), readOwnerOf("RIVAL"));
            reindex(territories, edges);

            assertThat(territories.getClusterIndex().findClusterMembersOf(buildCellKey("A")))
                .containsExactly(buildCellKey("A"));
            assertThat(territories.getClusterIndex().findClusterMembersOf(buildCellKey("C")))
                .containsExactly(buildCellKey("C"));
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

            territories.getOccupancy().recordHolderOf(buildCellKey("B"), readOwnerOf("F"));
            reindex(territories, edges);

            assertThat(territories.getClusterIndex().findClusterMembersOf(buildCellKey("A")))
                .containsExactlyInAnyOrderElementsOf(buildCellKeys("A", "B", "C"));
        }

        @Test
        void reindexClustersCarriesNoClusterForAnUnownedSystem() {

            var territories = buildOwnedBy(Map.of("A", "F"));

            reindex(territories, Map.of(
                "A", List.of(buildEdgeTo("UNOWNED")),
                "UNOWNED", List.of(buildEdgeTo("A"))));

            assertThat(territories.getClusterIndex().findClusterMembersOf(buildCellKey("UNOWNED")))
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

    // Clustering keys off the faction ID alone, so the palette shades are inert here.
    private static DominantHolder readOwnerOf(String factionId) {
        return new DominantHolder(factionId, Color.GRAY, Color.GRAY);
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
        territories.reindexClusters(buildKeyedValues(edges), buildDrawnSystemKeys(systemIdByCellId));
    }

    // A territories whose only varying inputs are the two draw lists; the retained inputs are the
    // shared fixture's inert placeholders, since isEmpty reads only the draw lists.
    private static PoliticalMapTerritories buildDrawablesWith(
            Map<SystemKey, StyledCell> styledCells,
            Map<String, StyledClusterGroup> territories) {

        var drawables = PoliticalMapTerritoryFixtures.createTerritoriesOwnedBy(Map.of());

        // The draw lists are not constructor inputs; fill the internally-created maps so
        // this fixture's only varying state is what isEmpty reads.
        drawables.getStyledCellByCellKey().putAll(styledCells);
        drawables.getStyledClusterGroupByOwnerId().putAll(territories);

        return drawables;
    }

    private static StyledCell buildAnyStyledCell() {
        return PoliticalMapTerritoryFixtures.createPlaceholderStyledCell();
    }

    private static StyledClusterGroup buildAnyStyledClusterGroup() {
        return PoliticalMapTerritoryFixtures.createTerritoryWithLoops(List.of());
    }

    // A CategoryStyle whose opacities and widths carry one marker value, so a style bundle is a
    // distinct instance the seam can be checked against.
    private static CategoryStyle buildStyleMarked(double marker) {

        var element = new ElementStyle(FactionPaletteSlot.PRIMARY, marker);
        return new CategoryStyle(element, element, marker, element, marker);
    }
}
